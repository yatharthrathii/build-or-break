package com.buildorbreak.core.domain.resolver

import com.buildorbreak.core.model.enums.DayMode
import com.buildorbreak.core.model.plan.MinimumVersion
import com.buildorbreak.core.testing.fixtures.ExecutionFixtures
import com.buildorbreak.core.testing.fixtures.PlanFixtures
import com.buildorbreak.core.testing.fixtures.PlanFixtures.item
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import org.junit.jupiter.api.Test

/**
 * A parent that finishes a little late should not drag its children with it.
 *
 * The tolerance is the difference between a plan that adapts and a plan that
 * twitches. Ten minutes late on the gym is ordinary life.
 */
class LateToleranceTest {

    private val resolver = DefaultTimelineResolver()
    private val date: LocalDate = ExecutionFixtures.DATE

    private val gym = item(id = 1, title = "Gym", anchor = PlanFixtures.fixedAt(7, 30))
    private val shower = item(id = 2, title = "Shower", anchor = PlanFixtures.relativeTo(1, 15.minutes))

    private fun resolve(gymDoneMinutesLate: Long, tolerance: Duration) = resolver.resolve(
        ResolveInput(
            template = PlanFixtures.template(),
            blocks = emptyList(),
            items = listOf(gym, shower),
            occurrences = listOf(
                ExecutionFixtures.completedAt(itemId = 1, at = date.atTime(7, 30).plusMinutes(gymDoneMinutesLate)),
            ),
            date = date,
            zone = ExecutionFixtures.ZONE,
            dayShift = Duration.ZERO,
            mode = DayMode.NORMAL,
            lateTolerance = tolerance,
        ),
    )

    @Test
    fun `inside the tolerance the child keeps its planned time`() {
        val day = resolve(gymDoneMinutesLate = 10, tolerance = 15.minutes)

        assertThat(day.entryFor(2)?.at).isEqualTo(date.atTime(7, 45))
    }

    @Test
    fun `beyond the tolerance the child follows the real finish`() {
        val day = resolve(gymDoneMinutesLate = 40, tolerance = 15.minutes)

        assertThat(day.entryFor(2)?.at).isEqualTo(date.atTime(8, 25))
    }

    @Test
    fun `zero tolerance moves the child for any slip at all`() {
        val day = resolve(gymDoneMinutesLate = 3, tolerance = Duration.ZERO)

        assertThat(day.entryFor(2)?.at).isEqualTo(date.atTime(7, 48))
    }

    @Test
    fun `a reduced day marks only the steps that have a smaller version`() {
        val withMinimum =
            item(id = 3, title = "Read", anchor = PlanFixtures.fixedAt(21), minimum = MinimumVersion("Two pages"))

        val day = resolver.resolve(
            ResolveInput(
                template = PlanFixtures.template(),
                blocks = emptyList(),
                items = listOf(gym, withMinimum),
                occurrences = emptyList(),
                date = date,
                zone = ExecutionFixtures.ZONE,
                dayShift = Duration.ZERO,
                mode = DayMode.REDUCED,
            ),
        )

        assertThat(day.entryFor(1)?.reduced).isFalse()
        assertThat(day.entryFor(3)?.reduced).isTrue()
    }
}
