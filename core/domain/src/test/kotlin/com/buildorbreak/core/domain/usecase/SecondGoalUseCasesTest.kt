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
import com.buildorbreak.core.domain.goal.Prices
import com.buildorbreak.core.model.enums.GoalKind
import com.buildorbreak.core.model.enums.PointReason
import com.buildorbreak.core.model.goal.PointEntry
import com.buildorbreak.core.model.plan.Plan
import com.buildorbreak.core.testing.fixtures.ExecutionFixtures.done
import com.buildorbreak.core.testing.fixtures.GoalFixtures
import com.buildorbreak.core.testing.fixtures.GoalFixtures.goal
import com.buildorbreak.core.testing.fixtures.GoalFixtures.progress
import com.buildorbreak.core.testing.fixtures.PlanFixtures
import com.buildorbreak.core.testing.time.FakeTimeProvider
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

private const val ITEM_ID = 4L

/** Halfway through the ten day goal, in the zone the fixtures use. */
private val HALFWAY: Instant = GoalFixtures.START.plusDays(5).atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant()

/**
 * How many goals may run at once, and what the second one costs.
 *
 * Apart from `GoalUseCasesTest`, which is about what a goal says. This is
 * about the rule: the first is free, the second is paid for before it is
 * written, there is no third, and nothing is ever taken for an edit.
 */
class SecondGoalUseCasesTest {

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
    private val cost = ObserveGoalCostUseCase(plans, goals, dispatchers)

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

    private suspend fun givenPoints(points: Int) {
        ledger.add(
            PointEntry(id = 0, at = HALFWAY, date = GoalFixtures.START, delta = points, reason = PointReason.AD_REWARD),
        )
    }

    private fun running() = goals.goals.value.filter { it.isActive }.map { it.title }

    @Test
    fun `the first goal is free`() = runTest {
        givenAPlan()

        assertThat(save(goal(id = 0, title = "First"))).isInstanceOf(Outcome.Success::class.java)
        assertThat(ledger.entries.value).isEmpty()
    }

    @Test
    fun `a second goal runs beside the first and is paid for`() = runTest {
        givenAPlan()
        givenPoints(500)
        save(goal(id = 0, title = "First"))

        save(goal(id = 0, title = "Second"))

        assertThat(running()).containsExactly("First", "Second").inOrder()
        assertThat(wallet().first().balance).isEqualTo(500 - Prices.SECOND_GOAL)
    }

    @Test
    fun `without the points there is no second goal, and the first is untouched`() = runTest {
        givenAPlan()
        save(goal(id = 0, title = "First"))

        val result = save(goal(id = 0, title = "Second"))

        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
        assertThat(running()).containsExactly("First")
    }

    @Test
    fun `there is no third goal at any price`() = runTest {
        givenAPlan()
        givenPoints(5_000)
        save(goal(id = 0, title = "First"))
        save(goal(id = 0, title = "Second"))
        val before = wallet().first().balance

        val result = save(goal(id = 0, title = "Third"))

        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
        assertThat(running()).containsExactly("First", "Second")
        assertThat(wallet().first().balance).isEqualTo(before)
    }

    @Test
    fun `editing a goal is never charged, even with two running`() = runTest {
        givenAPlan()
        givenPoints(500)
        save(goal(id = 0, title = "First"))
        val second = (save(goal(id = 0, title = "Second")) as Outcome.Success).value
        val before = wallet().first().balance

        save(goal(id = second, title = "Second, renamed"))

        assertThat(running()).containsExactly("First", "Second, renamed")
        assertThat(wallet().first().balance).isEqualTo(before)
    }

    @Test
    fun `the goal that follows a finished one takes its place for free`() = runTest {
        givenAPlan()
        val finished = (save(goal(id = 0, title = "Finished")) as Outcome.Success).value

        save(goal(id = 0, title = "Next"), replaces = finished)

        assertThat(running()).containsExactly("Next")
        assertThat(ledger.entries.value).isEmpty()
    }

    @Test
    fun `a replacement that cannot be paid for leaves the old goal running`() = runTest {
        givenAPlan()
        givenPoints(500)
        save(goal(id = 0, title = "First"))
        val second = (save(goal(id = 0, title = "Second")) as Outcome.Success).value

        // Replacing the second while the first still runs is a paid goal, and 200 is not 300.
        val result = save(goal(id = 0, title = "Third"), replaces = second)

        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
        assertThat(running()).containsExactly("First", "Second")
    }

    @Test
    fun `the price shown is the price charged`() = runTest {
        givenAPlan()
        assertThat(cost().first()).isEqualTo(GoalCost(points = 0, atLimit = false))

        givenPoints(500)
        save(goal(id = 0, title = "First"))
        assertThat(cost().first()).isEqualTo(GoalCost(points = Prices.SECOND_GOAL, atLimit = false))

        save(goal(id = 0, title = "Second"))
        assertThat(cost().first().atLimit).isTrue()
    }

    @Test
    fun `both running goals are observed, oldest first`() = runTest {
        givenAPlan()
        givenPoints(500)
        save(goal(id = 0, title = "First"))
        save(goal(id = 0, title = "Second"))

        assertThat(observe.all().first().map { it.goal.title }).containsExactly("First", "Second").inOrder()
        assertThat(observe().first()!!.goal.title).isEqualTo("First")
    }

    @Test
    fun `closing a day advances every running goal`() = runTest {
        givenAPlan()
        givenPoints(500)
        save(goal(id = 0, title = "First", kind = GoalKind.COUNT, itemId = ITEM_ID, targetValue = 4.0))
        save(goal(id = 0, title = "Second", kind = GoalKind.COUNT, itemId = ITEM_ID, targetValue = 2.0))
        occurrences.occurrences.value = listOf(done(ITEM_ID, GoalFixtures.START))

        val results = closer.close(PlanFixtures.PLAN_ID, GoalFixtures.START)

        assertThat(results.map { it.percent }).containsExactly(0.25f, 0.5f)
        assertThat(goals.progress.value).hasSize(2)
    }
}
