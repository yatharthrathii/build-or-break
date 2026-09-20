package com.buildorbreak.core.domain.resolver

import com.buildorbreak.core.model.enums.DayMode
import com.buildorbreak.core.testing.fixtures.ExecutionFixtures
import com.buildorbreak.core.testing.fixtures.PlanFixtures
import com.buildorbreak.core.testing.fixtures.PlanFixtures.item
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.time.Duration
import org.junit.jupiter.api.Test

/**
 * A routine pasted in the evening did not miss its morning.
 *
 * The steps before the plan existed are not part of its first day. From the
 * next day on, every step is.
 */
class PlanStartTest {

    private val resolver = DefaultTimelineResolver()
    private val date: LocalDate = ExecutionFixtures.DATE

    private val wake = item(id = 1, title = "Wake", anchor = PlanFixtures.fixedAt(6, 30))
    private val lunch = item(id = 2, title = "Lunch", anchor = PlanFixtures.fixedAt(13, 30))
    private val dinner = item(id = 3, title = "Dinner", anchor = PlanFixtures.fixedAt(20, 30))

    private fun resolve(on: LocalDate, startedAt: LocalDateTime?) = resolver.resolve(
        ResolveInput(
            template = PlanFixtures.template(),
            blocks = emptyList(),
            items = listOf(wake, lunch, dinner),
            occurrences = emptyList(),
            date = on,
            zone = ExecutionFixtures.ZONE,
            dayShift = Duration.ZERO,
            mode = DayMode.NORMAL,
            startedAt = startedAt,
        ),
    )

    @Test
    fun `on the day the plan was made, the steps before that moment are left out`() {
        val day = resolve(on = date, startedAt = date.atTime(18, 0))

        assertThat(day.entries.map { it.item.title }).containsExactly("Dinner")
        assertThat(day.total).isEqualTo(1)
    }

    @Test
    fun `from the next day on, the whole routine runs`() {
        val day = resolve(on = date.plusDays(1), startedAt = date.atTime(18, 0))

        assertThat(day.entries.map { it.item.title }).containsExactly("Wake", "Lunch", "Dinner").inOrder()
    }

    @Test
    fun `the day says how many steps its own start hid`() {
        val day = resolve(on = date, startedAt = date.atTime(18, 0))

        assertThat(day.hiddenBeforeStart).isEqualTo(2)
    }

    @Test
    fun `a day that hid nothing says so`() {
        assertThat(resolve(on = date.plusDays(1), startedAt = date.atTime(18, 0)).hiddenBeforeStart).isEqualTo(0)
    }

    @Test
    fun `no start means no cut off`() {
        assertThat(resolve(on = date, startedAt = null).total).isEqualTo(3)
    }
}
