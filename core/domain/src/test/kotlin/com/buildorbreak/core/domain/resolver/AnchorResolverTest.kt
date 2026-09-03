package com.buildorbreak.core.domain.resolver

import com.buildorbreak.core.model.execution.Occurrence
import com.buildorbreak.core.model.plan.Item
import com.buildorbreak.core.testing.fixtures.ExecutionFixtures
import com.buildorbreak.core.testing.fixtures.PlanFixtures.fixedAt
import com.buildorbreak.core.testing.fixtures.PlanFixtures.item
import com.buildorbreak.core.testing.fixtures.PlanFixtures.relativeTo
import com.buildorbreak.core.testing.fixtures.PlanFixtures.window
import com.google.common.truth.Truth.assertThat
import java.time.LocalTime
import kotlin.time.Duration.Companion.minutes
import org.junit.jupiter.api.Test

class AnchorResolverTest {

    private val resolver = AnchorResolver()
    private val builder = AnchorGraphBuilder()

    private val date = ExecutionFixtures.DATE
    private val zone = ExecutionFixtures.ZONE
    private val dayStart: LocalTime = LocalTime.of(6, 0)

    /**
     * Places every item the way the timeline resolver will: graph order first,
     * then one pass, each item seeing the ones already placed.
     */
    private fun placeAll(items: List<Item>, occurrences: Map<Long, Occurrence> = emptyMap()): Map<Long, Placement> {
        val graph = builder.build(items)
        val byId = items.associateBy { it.id }
        val placed = LinkedHashMap<Long, Placement>()

        graph.order.forEach { id ->
            placed[id] = resolver.resolve(
                byId.getValue(id),
                AnchorContext(date, zone, dayStart, graph, placed, occurrences),
            )
        }
        return placed
    }

    @Test
    fun `a fixed anchor resolves at its own clock time`() {
        val placed = placeAll(listOf(item(id = 1, anchor = fixedAt(8, 15))))

        assertThat(placed.getValue(1).at).isEqualTo(date.atTime(8, 15))
        assertThat(placed.getValue(1).degraded).isFalse()
        assertThat(placed.getValue(1).clamped).isFalse()
    }

    @Test
    fun `a window resolves at the start of the window, not the end`() {
        val placed = placeAll(listOf(item(id = 1, anchor = window(fromHour = 11, toHour = 14))))

        assertThat(placed.getValue(1).at).isEqualTo(date.atTime(11, 0))
    }

    @Test
    fun `a relative item sits at its offset from the parent`() {
        val placed = placeAll(
            listOf(
                item(id = 1, anchor = fixedAt(8)),
                item(id = 2, anchor = relativeTo(1, 30.minutes)),
            ),
        )

        assertThat(placed.getValue(2).at).isEqualTo(date.atTime(8, 30))
    }

    @Test
    fun `a completed parent moves its children to when it actually finished`() {
        val placed = placeAll(
            items = listOf(
                item(id = 1, anchor = fixedAt(8)),
                item(id = 2, anchor = relativeTo(1, 30.minutes)),
            ),
            // Breakfast was planned for 08:00 and actually finished at 08:40.
            occurrences = mapOf(1L to ExecutionFixtures.completedAt(1, date.atTime(8, 40))),
        )

        assertThat(placed.getValue(2).at).isEqualTo(date.atTime(9, 10))
    }

    @Test
    fun `a skipped parent leaves its children on the planned time`() {
        val placed = placeAll(
            items = listOf(
                item(id = 1, anchor = fixedAt(8)),
                item(id = 2, anchor = relativeTo(1, 30.minutes)),
            ),
            occurrences = mapOf(1L to ExecutionFixtures.skipped(1)),
        )

        assertThat(placed.getValue(2).at).isEqualTo(date.atTime(8, 30))
    }

    @Test
    fun `a snoozed parent takes its children with it`() {
        val placed = placeAll(
            items = listOf(
                item(id = 1, anchor = fixedAt(8)),
                item(id = 2, anchor = relativeTo(1, 30.minutes)),
                item(id = 3, anchor = relativeTo(2, 15.minutes)),
            ),
            occurrences = mapOf(1L to ExecutionFixtures.snoozedBy(1, minutes = 45)),
        )

        // The whole chain moves, which is what the snooze preview diffs.
        assertThat(placed.getValue(1).at).isEqualTo(date.atTime(8, 0))
        assertThat(placed.getValue(2).at).isEqualTo(date.atTime(9, 15))
        assertThat(placed.getValue(3).at).isEqualTo(date.atTime(9, 30))
    }

    @Test
    fun `an item whose parent was cut from a cycle falls back to the day start`() {
        val placed = placeAll(
            listOf(
                item(id = 1, anchor = relativeTo(2, 10.minutes)),
                item(id = 2, anchor = relativeTo(1, 10.minutes)),
            ),
        )

        // Id 2 is the most recently created, so it loses its anchor.
        assertThat(placed.getValue(2).degraded).isTrue()
        assertThat(placed.getValue(2).at).isEqualTo(date.atTime(6, 10))

        // Id 1 keeps a usable parent and is not itself degraded.
        assertThat(placed.getValue(1).degraded).isFalse()
        assertThat(placed.getValue(1).at).isEqualTo(date.atTime(6, 20))
    }

    @Test
    fun `an item whose parent is not on this template falls back to the day start`() {
        val placed = placeAll(
            listOf(
                item(id = 1, anchor = fixedAt(8)),
                item(id = 2, anchor = relativeTo(parentItemId = 99, offset = 20.minutes)),
            ),
        )

        assertThat(placed.getValue(2).degraded).isTrue()
        assertThat(placed.getValue(2).at).isEqualTo(date.atTime(6, 20))
    }

    @Test
    fun `a chain that runs past midnight is clamped to the last minute of the day`() {
        val placed = placeAll(
            listOf(
                item(id = 1, anchor = fixedAt(23, 30)),
                item(id = 2, anchor = relativeTo(1, 60.minutes)),
            ),
        )

        assertThat(placed.getValue(2).at).isEqualTo(date.atTime(23, 59))
        assertThat(placed.getValue(2).clamped).isTrue()
    }

    @Test
    fun `a negative offset before the start of the day is clamped, not moved to yesterday`() {
        val placed = placeAll(
            listOf(
                item(id = 1, anchor = fixedAt(0, 10)),
                item(id = 2, anchor = relativeTo(1, (-30).minutes)),
            ),
        )

        assertThat(placed.getValue(2).at).isEqualTo(date.atStartOfDay())
        assertThat(placed.getValue(2).clamped).isTrue()
    }

    @Test
    fun `an item that resolves inside the day is neither degraded nor clamped`() {
        val placed = placeAll(
            listOf(
                item(id = 1, anchor = fixedAt(8)),
                item(id = 2, anchor = relativeTo(1, 30.minutes)),
            ),
        )

        assertThat(placed.values.none { it.degraded || it.clamped }).isTrue()
    }
}
