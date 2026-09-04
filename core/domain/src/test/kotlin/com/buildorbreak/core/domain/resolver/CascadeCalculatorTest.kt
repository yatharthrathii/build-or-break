package com.buildorbreak.core.domain.resolver

import com.buildorbreak.core.model.enums.DayMode
import com.buildorbreak.core.model.plan.Item
import com.buildorbreak.core.testing.fixtures.ExecutionFixtures
import com.buildorbreak.core.testing.fixtures.PlanFixtures
import com.buildorbreak.core.testing.fixtures.PlanFixtures.fixedAt
import com.buildorbreak.core.testing.fixtures.PlanFixtures.item
import com.buildorbreak.core.testing.fixtures.PlanFixtures.relativeTo
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import org.junit.jupiter.api.Test

class CascadeCalculatorTest {

    private val cascade = DefaultCascadeCalculator()
    private val date = ExecutionFixtures.DATE

    private fun inputOf(items: List<Item>) = ResolveInput(
        template = PlanFixtures.template(),
        blocks = emptyList(),
        items = items,
        occurrences = emptyList(),
        date = date,
        zone = ExecutionFixtures.ZONE,
        dayShift = Duration.ZERO,
        mode = DayMode.NORMAL,
    )

    @Test
    fun `snoozing something nothing hangs off moves only itself`() {
        val preview = cascade.preview(
            inputOf(listOf(item(id = 1, anchor = fixedAt(8)))),
            itemId = 1,
            shift = 30.minutes,
        )

        assertThat(preview.movesNothing).isFalse()
        assertThat(preview.moved).hasSize(1)
        assertThat(preview.moved.single().from).isEqualTo(date.atTime(8, 0))
        assertThat(preview.moved.single().to).isEqualTo(date.atTime(8, 30))
    }

    @Test
    fun `snoozing a parent takes the whole chain with it`() {
        val input = inputOf(
            listOf(
                item(id = 1, title = "Workout", anchor = fixedAt(8)),
                item(id = 2, title = "Shake", anchor = relativeTo(1, 30.minutes)),
                item(id = 3, title = "Weigh in", anchor = relativeTo(2, 15.minutes)),
            ),
        )

        val preview = cascade.preview(input, itemId = 1, shift = 45.minutes)

        assertThat(preview.moved.map { it.title }).containsExactly("Workout", "Shake", "Weigh in")
        assertThat(preview.moved.first { it.itemId == 2L }.to).isEqualTo(date.atTime(9, 15))
        assertThat(preview.moved.first { it.itemId == 3L }.to).isEqualTo(date.atTime(9, 30))
    }

    @Test
    fun `an item that is fixed elsewhere in the day is not dragged along`() {
        val input = inputOf(
            listOf(
                item(id = 1, anchor = fixedAt(8)),
                item(id = 2, anchor = relativeTo(1, 30.minutes)),
                item(id = 9, anchor = fixedAt(18)),
            ),
        )

        val preview = cascade.preview(input, itemId = 1, shift = 45.minutes)

        assertThat(preview.moved.map { it.itemId }).containsExactly(1L, 2L)
    }

    @Test
    fun `snoozing something that is not on the plan today moves nothing`() {
        val preview = cascade.preview(
            inputOf(listOf(item(id = 1, anchor = fixedAt(8)))),
            itemId = 99,
            shift = 30.minutes,
        )

        assertThat(preview.movesNothing).isTrue()
        assertThat(preview.collisions).isEmpty()
    }

    @Test
    fun `a snooze that pushes one item onto another reports the clash`() {
        val input = inputOf(
            listOf(
                item(id = 1, anchor = fixedAt(8)),
                item(id = 2, anchor = fixedAt(9)),
            ),
        )

        val preview = cascade.preview(input, itemId = 1, shift = 58.minutes)

        assertThat(preview.collisions).hasSize(1)
        assertThat(preview.collisions.single().firstItemId).isEqualTo(1L)
        assertThat(preview.collisions.single().secondItemId).isEqualTo(2L)
        assertThat(preview.collisions.single().gap).isEqualTo(2.minutes)
    }

    @Test
    fun `a clash the plan already had is not blamed on the snooze`() {
        val input = inputOf(
            listOf(
                item(id = 1, anchor = fixedAt(8, 0)),
                item(id = 2, anchor = fixedAt(8, 2)),
                item(id = 3, anchor = fixedAt(15, 0)),
            ),
        )

        val preview = cascade.preview(input, itemId = 3, shift = 10.minutes)

        assertThat(preview.moved.map { it.itemId }).containsExactly(3L)
        assertThat(preview.collisions).isEmpty()
    }

    @Test
    fun `a snooze wide enough to clear the next item reports no clash`() {
        val input = inputOf(
            listOf(
                item(id = 1, anchor = fixedAt(8)),
                item(id = 2, anchor = fixedAt(9)),
            ),
        )

        val preview = cascade.preview(input, itemId = 1, shift = 20.minutes)

        assertThat(preview.collisions).isEmpty()
    }

    @Test
    fun `the preview carries back the item and the shift it was asked about`() {
        val preview = cascade.preview(
            inputOf(listOf(item(id = 1, anchor = fixedAt(8)))),
            itemId = 1,
            shift = 30.minutes,
        )

        assertThat(preview.itemId).isEqualTo(1L)
        assertThat(preview.shift).isEqualTo(30.minutes)
    }
}
