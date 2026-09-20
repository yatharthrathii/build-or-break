package com.buildorbreak.core.domain.review

import com.buildorbreak.core.domain.resolver.DefaultTimelineResolver
import com.buildorbreak.core.domain.resolver.ResolveInput
import com.buildorbreak.core.model.enums.DayMode
import com.buildorbreak.core.model.enums.Salience
import com.buildorbreak.core.model.execution.Occurrence
import com.buildorbreak.core.model.plan.Anchor
import com.buildorbreak.core.model.plan.Item
import com.buildorbreak.core.model.resolved.ResolvedDay
import com.buildorbreak.core.testing.fixtures.ExecutionFixtures
import com.buildorbreak.core.testing.fixtures.PlanFixtures
import com.buildorbreak.core.testing.fixtures.PlanFixtures.fixedAt
import com.buildorbreak.core.testing.fixtures.PlanFixtures.item
import com.google.common.truth.Truth.assertThat
import java.time.LocalTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import org.junit.jupiter.api.Test

class CatchUpPlannerTest {

    private val planner = CatchUpPlanner()
    private val resolver = DefaultTimelineResolver()
    private val date = ExecutionFixtures.DATE

    private fun dayOf(items: List<Item>, occurrences: List<Occurrence> = emptyList()): ResolvedDay = resolver.resolve(
        ResolveInput(
            template = PlanFixtures.template(),
            blocks = emptyList(),
            items = items,
            occurrences = occurrences,
            date = date,
            zone = ExecutionFixtures.ZONE,
            dayShift = Duration.ZERO,
            mode = DayMode.NORMAL,
        ),
    )

    @Test
    fun `a day with nothing missed needs no catch up`() {
        val day = dayOf(listOf(item(id = 1, anchor = fixedAt(21))))

        val plan = planner.plan(day, now = date.atTime(20, 0))

        assertThat(plan.hasRoom).isFalse()
        assertThat(plan.outOfTime).isEmpty()
    }

    @Test
    fun `missed items are fitted into the time that is left`() {
        val day = dayOf(
            listOf(
                item(id = 1, anchor = fixedAt(8), duration = 20.minutes),
                item(id = 2, anchor = fixedAt(9), duration = 20.minutes),
            ),
        )

        val plan = planner.plan(day, now = date.atTime(20, 0))

        assertThat(plan.suggestions.map { it.itemId }).containsExactly(1L, 2L).inOrder()
        assertThat(plan.suggestions.first().at).isEqualTo(date.atTime(20, 0))
        // Twenty minutes, then five minutes of room to breathe.
        assertThat(plan.suggestions.last().at).isEqualTo(date.atTime(20, 25))
    }

    @Test
    fun `what matters most is offered first, whatever order it was missed in`() {
        val day = dayOf(
            listOf(
                item(id = 1, anchor = fixedAt(8), salience = Salience.SILENT, duration = 10.minutes),
                item(id = 2, anchor = fixedAt(9), salience = Salience.ALARM, duration = 10.minutes),
                item(id = 3, anchor = fixedAt(10), salience = Salience.NOTIFY, duration = 10.minutes),
            ),
        )

        val plan = planner.plan(day, now = date.atTime(20, 0))

        assertThat(plan.suggestions.map { it.itemId }).containsExactly(2L, 3L, 1L).inOrder()
    }

    @Test
    fun `at most three, because an evening cannot absorb a whole day`() {
        val day = dayOf((1L..8L).map { item(id = it, anchor = fixedAt(8), duration = 10.minutes) })

        val plan = planner.plan(day, now = date.atTime(20, 0))

        assertThat(plan.suggestions).hasSize(3)
        // Left over rather than impossible. There are two and a half hours of
        // evening in front of these and each takes ten minutes, so calling
        // them out of time would be a claim the user can disprove by looking
        // at a clock.
        assertThat(plan.beyondCap).hasSize(5)
        assertThat(plan.outOfTime).isEmpty()
    }

    @Test
    fun `the smaller version is offered when only that still fits`() {
        val day = dayOf(
            listOf(
                item(
                    id = 1,
                    anchor = fixedAt(8),
                    duration = 90.minutes,
                    minimum = PlanFixtures.minimum(duration = 10.minutes),
                ),
            ),
        )

        val plan = planner.plan(day, now = date.atTime(22, 0))

        assertThat(plan.suggestions.single().useMinimum).isTrue()
        assertThat(plan.suggestions.single().duration).isEqualTo(10.minutes)
    }

    @Test
    fun `the full version is preferred while there is still room for it`() {
        val day = dayOf(
            listOf(
                item(
                    id = 1,
                    anchor = fixedAt(8),
                    duration = 30.minutes,
                    minimum = PlanFixtures.minimum(duration = 5.minutes),
                ),
            ),
        )

        val plan = planner.plan(day, now = date.atTime(20, 0))

        assertThat(plan.suggestions.single().useMinimum).isFalse()
        assertThat(plan.suggestions.single().duration).isEqualTo(30.minutes)
    }

    @Test
    fun `something with no smaller version and no room left is honestly out of time`() {
        val day = dayOf(listOf(item(id = 1, anchor = fixedAt(8), duration = 120.minutes)))

        val plan = planner.plan(day, now = date.atTime(21, 30))

        assertThat(plan.suggestions).isEmpty()
        assertThat(plan.outOfTime).containsExactly(1L)
    }

    @Test
    fun `a pinned item is never quietly rescheduled`() {
        val day = dayOf(
            listOf(
                item(id = 1, anchor = fixedAt(8), pinned = true, duration = 10.minutes),
                item(id = 2, anchor = fixedAt(9), duration = 10.minutes),
            ),
        )

        val plan = planner.plan(day, now = date.atTime(20, 0))

        assertThat(plan.suggestions.map { it.itemId }).containsExactly(2L)
        assertThat(plan.outOfTime).doesNotContain(1L)
    }

    @Test
    fun `something already done is not offered again`() {
        val day = dayOf(
            items = listOf(item(id = 1, anchor = fixedAt(8), duration = 10.minutes)),
            occurrences = listOf(ExecutionFixtures.done(itemId = 1, date = date)),
        )

        assertThat(planner.plan(day, now = date.atTime(20, 0)).suggestions).isEmpty()
    }

    @Test
    fun `after the last useful hour nothing is proposed at all`() {
        val day = dayOf(listOf(item(id = 1, anchor = fixedAt(8), duration = 10.minutes)))

        val plan = planner.plan(day, now = date.atTime(23, 0))

        assertThat(plan.suggestions).isEmpty()
        assertThat(plan.outOfTime).containsExactly(1L)
    }

    @Test
    fun `timeline items are never part of a catch up`() {
        val day = dayOf(listOf(item(id = 1, anchor = fixedAt(8), salience = Salience.TIMELINE)))

        val plan = planner.plan(day, now = date.atTime(20, 0))

        assertThat(plan.suggestions).isEmpty()
        assertThat(plan.outOfTime).isEmpty()
    }

    @Test
    fun `a missed step is fitted around what is still to come, not on top of it`() {
        // Gym missed at 08:00, dinner pinned at 18:15. At 18:00 the gym must
        // not be proposed for 18:00: that puts two alarms in the same minute
        // and moves the problem rather than solving it.
        val day = dayOf(
            listOf(
                item(id = 1, anchor = fixedAt(8), duration = 45.minutes),
                item(id = 2, anchor = fixedAt(18, 15), pinned = true, duration = 30.minutes),
            ),
        )

        val plan = planner.plan(day, now = date.atTime(18, 0))

        val gym = plan.suggestions.single { it.itemId == 1L }
        assertThat(gym.at).isEqualTo(date.atTime(18, 50))
    }

    @Test
    fun `a gap before the next step is used when the step fits in it`() {
        val day = dayOf(
            listOf(
                item(id = 1, anchor = fixedAt(8), duration = 10.minutes),
                item(id = 2, anchor = fixedAt(18, 30), duration = 30.minutes),
            ),
        )

        val plan = planner.plan(day, now = date.atTime(18, 0))

        assertThat(plan.suggestions.single().at).isEqualTo(date.atTime(18, 0))
    }

    @Test
    fun `every missed repeat of an interval item is its own miss`() {
        val day = dayOf(
            listOf(
                item(
                    id = 1,
                    anchor = Anchor.Interval(from = LocalTime.of(9, 0), to = LocalTime.of(13, 0), every = 2.hours),
                    duration = 5.minutes,
                ),
            ),
        )

        val plan = planner.plan(day, now = date.atTime(18, 0))

        assertThat(plan.suggestions.map { it.sequenceInDay }).containsExactly(0, 1, 2).inOrder()
    }
}
