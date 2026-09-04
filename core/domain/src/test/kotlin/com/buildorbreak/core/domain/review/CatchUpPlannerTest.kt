package com.buildorbreak.core.domain.review

import com.buildorbreak.core.domain.resolver.DefaultTimelineResolver
import com.buildorbreak.core.domain.resolver.ResolveInput
import com.buildorbreak.core.model.enums.DayMode
import com.buildorbreak.core.model.enums.Salience
import com.buildorbreak.core.model.execution.Occurrence
import com.buildorbreak.core.model.plan.Item
import com.buildorbreak.core.model.resolved.ResolvedDay
import com.buildorbreak.core.testing.fixtures.ExecutionFixtures
import com.buildorbreak.core.testing.fixtures.PlanFixtures
import com.buildorbreak.core.testing.fixtures.PlanFixtures.fixedAt
import com.buildorbreak.core.testing.fixtures.PlanFixtures.item
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration
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
        assertThat(plan.outOfTime).hasSize(5)
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
}
