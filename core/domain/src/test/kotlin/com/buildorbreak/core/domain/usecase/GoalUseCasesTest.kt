package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.domain.fake.FakeDayCloseRepository
import com.buildorbreak.core.domain.fake.FakeGoalRepository
import com.buildorbreak.core.domain.fake.FakeItemRepository
import com.buildorbreak.core.domain.fake.FakeMeasurementRepository
import com.buildorbreak.core.domain.fake.FakeOccurrenceRepository
import com.buildorbreak.core.domain.fake.FakePlanRepository
import com.buildorbreak.core.domain.fake.FakePointLedgerRepository
import com.buildorbreak.core.domain.goal.DefaultGoalCalculator
import com.buildorbreak.core.domain.goal.GoalCloser
import com.buildorbreak.core.domain.goal.GoalProgressWriter
import com.buildorbreak.core.domain.goal.GoalSources
import com.buildorbreak.core.domain.goal.GoalStanding
import com.buildorbreak.core.domain.goal.GoalWeek
import com.buildorbreak.core.model.enums.GoalKind
import com.buildorbreak.core.model.plan.Plan
import com.buildorbreak.core.testing.fixtures.ExecutionFixtures.done
import com.buildorbreak.core.testing.fixtures.GoalFixtures
import com.buildorbreak.core.testing.fixtures.GoalFixtures.goal
import com.buildorbreak.core.testing.fixtures.GoalFixtures.progress
import com.buildorbreak.core.testing.fixtures.PlanFixtures
import com.buildorbreak.core.testing.time.FakeTimeProvider
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

private const val ITEM_ID = 4L

/** Halfway through the ten day goal, in the zone the fixtures use. */
private val HALFWAY: Instant = GoalFixtures.START.plusDays(5).atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant()

class GoalUseCasesTest {

    private val plans = FakePlanRepository()
    private val goals = FakeGoalRepository()
    private val items = FakeItemRepository()
    private val measurements = FakeMeasurementRepository()
    private val occurrences = FakeOccurrenceRepository()
    private val closes = FakeDayCloseRepository()

    private val time = FakeTimeProvider(initial = HALFWAY, currentZone = ZoneId.of("Asia/Kolkata"))

    /**
     * Plain unconfined dispatchers rather than a test one.
     *
     * The snapshot combines two flows and hops threads, and a
     * TestCoroutineScheduler of its own would collide with the one runTest
     * makes. Nothing here needs virtual time.
     */
    private val dispatchers = object : AppDispatchers {
        override val default = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
        override val main = Dispatchers.Unconfined
    }
    private val calculator = DefaultGoalCalculator()

    private val today = GoalToday(occurrences, measurements, items, time)
    private val observe = ObserveGoalUseCase(plans, goals, today, calculator, time, dispatchers)
    private val ledger = FakePointLedgerRepository()
    private val wallet = ObserveWalletUseCase(closes, ledger, time, dispatchers)
    private val spend = SpendPointsUseCase(wallet, ledger, time, dispatchers)
    private val save = SaveGoalUseCase(plans, goals, spend, dispatchers)
    private val retire = RetireGoalUseCase(goals, dispatchers)

    private val closer = GoalCloser(
        sources = GoalSources(goals, items, measurements, occurrences, closes),
        writer = GoalProgressWriter(calculator),
        calculator = calculator,
    )

    private fun givenAPlan() {
        plans.plans.value = listOf(
            Plan(
                id = PlanFixtures.PLAN_ID,
                name = "Weekdays",
                isActive = true,
                zone = ZoneId.of("Asia/Kolkata"),
                createdAt = Instant.EPOCH,
            ),
        )
    }

    // Observing ---------------------------------------------------------------

    @Test
    fun `no goal is the ordinary state and is not an error`() = runTest {
        givenAPlan()

        assertThat(observe().first()).isNull()
    }

    @Test
    fun `a goal set this morning is not already behind`() = runTest {
        givenAPlan()
        goals.upsert(goal(kind = GoalKind.COUNT))

        val snapshot = observe().first()

        assertThat(snapshot!!.hasData).isFalse()
        assertThat(snapshot.standing).isEqualTo(GoalStanding.UNKNOWN)
    }

    @Test
    fun `half the target at half the time reads as on pace`() = runTest {
        givenAPlan()
        goals.upsert(goal(kind = GoalKind.COUNT))
        goals.upsertProgress(progress(date = GoalFixtures.START.plusDays(5), cumulative = 5.0))

        val snapshot = observe().first()

        assertThat(snapshot!!.current).isEqualTo(5.0)
        assertThat(snapshot.standing).isEqualTo(GoalStanding.ON_PACE)
        assertThat(snapshot.daysLeft).isEqualTo(5)
    }

    @Test
    fun `well past the pace line reads as ahead`() = runTest {
        givenAPlan()
        goals.upsert(goal(kind = GoalKind.COUNT))
        goals.upsertProgress(progress(date = GoalFixtures.START.plusDays(5), cumulative = 9.0))

        assertThat(observe().first()!!.standing).isEqualTo(GoalStanding.AHEAD)
    }

    @Test
    fun `reaching the target says so rather than saying ahead`() = runTest {
        givenAPlan()
        goals.upsert(goal(kind = GoalKind.COUNT))
        goals.upsertProgress(progress(date = GoalFixtures.START.plusDays(5), cumulative = 10.0))

        assertThat(observe().first()!!.standing).isEqualTo(GoalStanding.REACHED)
    }

    @Test
    fun `what was done in a week left out still counts toward where the goal stands`() = runTest {
        givenAPlan()
        goals.upsert(goal(kind = GoalKind.COUNT))
        goals.upsertProgress(progress(date = GoalFixtures.START.plusDays(3), cumulative = 3.0))
        goals.upsertProgress(progress(date = GoalFixtures.START.plusDays(5), cumulative = 5.0, counted = false))

        // Five sessions were done. Leaving the week out is about pace, and
        // taking two of them away would punish the week twice.
        assertThat(observe().first()!!.current).isEqualTo(5.0)
    }

    @Test
    fun `leaving a week out moves the forecast`() = runTest {
        givenAPlan()
        goals.upsert(goal(kind = GoalKind.COUNT))
        goals.upsertProgress(progress(date = GoalFixtures.START.plusDays(3), cumulative = 3.0))
        goals.upsertProgress(progress(date = GoalFixtures.START.plusDays(5), cumulative = 3.0))
        val before = observe().first()!!.projected

        goals.setWeekCounted(goals.progress.value.first().goalId, LocalDate.of(2026, 1, 5), counted = false)

        assertThat(observe().first()!!.projected).isGreaterThan(before)
    }

    @Test
    fun `the weeks are listed newest first, each with whether it counts`() = runTest {
        givenAPlan()
        val goalId = (goals.upsert(goal(kind = GoalKind.COUNT)) as Outcome.Success).value
        goals.upsertProgress(progress(date = GoalFixtures.START.plusDays(3), cumulative = 3.0))
        goals.upsertProgress(progress(date = GoalFixtures.START.plusDays(5), cumulative = 5.0))
        goals.setWeekCounted(goalId, LocalDate.of(2026, 1, 5), counted = false)

        // The first of January 2026 is a Thursday, so its week began on the 29th.
        assertThat(observe().first()!!.weeks).containsExactly(
            GoalWeek(start = LocalDate.of(2026, 1, 5), counted = false),
            GoalWeek(start = LocalDate.of(2025, 12, 29), counted = true),
        ).inOrder()
    }

    @Test
    fun `a week that was left out is still listed, so it can be put back`() = runTest {
        givenAPlan()
        val goalId = (goals.upsert(goal(kind = GoalKind.COUNT)) as Outcome.Success).value
        goals.upsertProgress(progress(date = GoalFixtures.START, cumulative = 1.0))
        goals.setWeekCounted(goalId, LocalDate.of(2025, 12, 29), counted = false)

        assertThat(observe().first()!!.weeks).contains(GoalWeek(start = LocalDate.of(2025, 12, 29), counted = false))
    }

    @Test
    fun `this week can be left out before it has a single day in it`() = runTest {
        givenAPlan()
        val goalId = (goals.upsert(goal(kind = GoalKind.COUNT)) as Outcome.Success).value

        // Today is the sixth of January, a Tuesday, and nothing has been closed.
        val thisWeek = LocalDate.of(2026, 1, 5)
        assertThat(observe().first()!!.weeks).containsExactly(GoalWeek(start = thisWeek, counted = true))

        goals.setWeekCounted(goalId, thisWeek, counted = false)

        assertThat(observe().first()!!.weeks).containsExactly(GoalWeek(start = thisWeek, counted = false))
    }

    @Test
    fun `a day closed inside a week left out in advance is written as left out`() = runTest {
        givenAPlan()
        val goalId = (goals.upsert(goal(kind = GoalKind.COUNT, itemId = ITEM_ID)) as Outcome.Success).value
        goals.setWeekCounted(goalId, LocalDate.of(2026, 1, 5), counted = false)
        occurrences.occurrences.value = listOf(done(ITEM_ID, LocalDate.of(2026, 1, 5)))

        closer.close(PlanFixtures.PLAN_ID, LocalDate.of(2026, 1, 5))

        assertThat(goals.progress.value.single().counted).isFalse()
    }

    @Test
    fun `a step done today moves the goal before the day has been closed`() = runTest {
        // Otherwise a counting goal sits at the same number all day however
        // many times the step is done, which is the most deflating thing a
        // goal screen can do.
        givenAPlan()
        goals.upsert(goal(kind = GoalKind.COUNT, itemId = ITEM_ID, targetValue = 10.0))
        goals.upsertProgress(progress(date = GoalFixtures.START.plusDays(4), cumulative = 4.0))
        occurrences.occurrences.value = listOf(done(ITEM_ID, GoalFixtures.START.plusDays(5)))

        val snapshot = observe().first()

        assertThat(snapshot!!.current).isEqualTo(5.0)
        assertThat(snapshot.hasData).isTrue()
    }

    @Test
    fun `a measured goal waits for the close rather than showing one raw reading`() = runTest {
        // Its honest value is the seven day average, and one weigh in cannot
        // be smoothed. Moving a bar by a kilo of water would undo the reason
        // the smoothing exists.
        givenAPlan()
        goals.upsert(goal(kind = GoalKind.NUMBER, itemId = ITEM_ID, startValue = 80.0, targetValue = 75.0))
        occurrences.occurrences.value = listOf(done(ITEM_ID, GoalFixtures.START.plusDays(5)))

        val snapshot = observe().first()

        assertThat(snapshot!!.current).isEqualTo(80.0)
        assertThat(snapshot.hasData).isFalse()
    }

    @Test
    fun `a day outside the goal window adds nothing to it`() = runTest {
        givenAPlan()
        goals.upsert(
            goal(
                kind = GoalKind.COUNT,
                itemId = ITEM_ID,
                startDate = GoalFixtures.START.minusDays(20),
                targetDate = GoalFixtures.START.minusDays(10),
            ),
        )
        occurrences.occurrences.value = listOf(done(ITEM_ID, GoalFixtures.START.plusDays(5)))

        assertThat(observe().first()!!.current).isEqualTo(0.0)
    }

    @Test
    fun `on the first day there is no rate to carry forward`() = runTest {
        // A goal set yesterday with one completion has banked something and
        // has no elapsed time to divide it by. Reporting a projection there
        // means reporting that the goal will be missed, on day one.
        givenAPlan()
        goals.upsert(goal(kind = GoalKind.COUNT, itemId = ITEM_ID))
        goals.upsertProgress(progress(date = GoalFixtures.START, cumulative = 1.0))

        assertThat(observe().first()!!.hasProjection).isFalse()
    }

    @Test
    fun `once a banked day has time behind it the rate is real`() = runTest {
        givenAPlan()
        goals.upsert(goal(kind = GoalKind.COUNT, itemId = ITEM_ID))
        goals.upsertProgress(progress(date = GoalFixtures.START.plusDays(3), cumulative = 3.0))

        assertThat(observe().first()!!.hasProjection).isTrue()
    }

    // Saving ------------------------------------------------------------------

    @Test
    fun `a goal that ends before it starts is refused rather than saved`() = runTest {
        givenAPlan()

        val outcome = save(goal(id = 0, startDate = GoalFixtures.TARGET, targetDate = GoalFixtures.START))

        assertThat(outcome).isInstanceOf(Outcome.Failure::class.java)
        assertThat(goals.goals.value).isEmpty()
    }

    @Test
    fun `retiring a goal keeps its history`() = runTest {
        givenAPlan()
        goals.upsert(goal())
        goals.upsertProgress(progress(date = GoalFixtures.START, cumulative = 2.0))

        retire(GoalFixtures.GOAL_ID)

        assertThat(observe().first()).isNull()
        assertThat(goals.progress.value).hasSize(1)
    }

    // Closing -----------------------------------------------------------------

    @Test
    fun `closing a day writes one goal row and reports how far along it is`() = runTest {
        givenAPlan()
        goals.upsert(goal(kind = GoalKind.COUNT, itemId = ITEM_ID, targetValue = 4.0))
        occurrences.occurrences.value = listOf(done(ITEM_ID, GoalFixtures.START))

        val result = closer.close(PlanFixtures.PLAN_ID, GoalFixtures.START)

        assertThat(goals.progress.value).hasSize(1)
        assertThat(result.single().percent).isEqualTo(0.25f)
    }

    @Test
    fun `closing the same day twice does not count it twice`() = runTest {
        givenAPlan()
        goals.upsert(goal(kind = GoalKind.COUNT, itemId = ITEM_ID))
        occurrences.occurrences.value = listOf(done(ITEM_ID, GoalFixtures.START))

        closer.close(PlanFixtures.PLAN_ID, GoalFixtures.START)
        closer.close(PlanFixtures.PLAN_ID, GoalFixtures.START)

        assertThat(goals.progress.value).hasSize(1)
        assertThat(goals.progress.value.single().cumulative).isEqualTo(1.0)
    }

    @Test
    fun `a day written again stays left out if its week was left out`() = runTest {
        givenAPlan()
        goals.upsert(goal(kind = GoalKind.COUNT, itemId = ITEM_ID))
        occurrences.occurrences.value = listOf(done(ITEM_ID, GoalFixtures.START))

        closer.close(PlanFixtures.PLAN_ID, GoalFixtures.START)
        val goalId = goals.progress.value.single().goalId
        goals.setWeekCounted(goalId, LocalDate.of(2025, 12, 29), counted = false)

        // What correcting a reading does: the same day goes through the closer again.
        closer.close(PlanFixtures.PLAN_ID, GoalFixtures.START)

        assertThat(goals.progress.value.single().counted).isFalse()
    }

    @Test
    fun `a day outside the goal window writes nothing at all`() = runTest {
        givenAPlan()
        goals.upsert(goal(kind = GoalKind.COUNT, itemId = ITEM_ID))

        val result = closer.close(PlanFixtures.PLAN_ID, GoalFixtures.START.minusDays(1))

        assertThat(result).isEmpty()
        assertThat(goals.progress.value).isEmpty()
    }

    @Test
    fun `with no goal a close reports nothing and does not fail`() = runTest {
        givenAPlan()

        assertThat(closer.close(PlanFixtures.PLAN_ID, GoalFixtures.START)).isEmpty()
    }

    @Test
    fun `a measured goal that goes nowhere is refused`() = runTest {
        givenAPlan()

        val outcome = save(goal(id = 0, kind = GoalKind.NUMBER, startValue = 80.0, targetValue = 80.0))

        assertThat(outcome).isInstanceOf(Outcome.Failure::class.java)
        assertThat(goals.goals.value).isEmpty()
    }

    @Test
    fun `a target that is not a number is refused`() = runTest {
        givenAPlan()

        val outcome = save(goal(id = 0, kind = GoalKind.COUNT, targetValue = Double.NaN))

        assertThat(outcome).isInstanceOf(Outcome.Failure::class.java)
        assertThat(goals.goals.value).isEmpty()
    }

    @Test
    fun `a counting goal with nothing to count up to is refused`() = runTest {
        givenAPlan()

        val outcome = save(goal(id = 0, kind = GoalKind.COUNT, targetValue = 0.0))

        assertThat(outcome).isInstanceOf(Outcome.Failure::class.java)
    }
}
