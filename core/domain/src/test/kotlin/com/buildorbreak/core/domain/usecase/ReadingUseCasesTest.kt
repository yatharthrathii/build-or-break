package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.domain.fake.FakeDayCloseRepository
import com.buildorbreak.core.domain.fake.FakeGoalRepository
import com.buildorbreak.core.domain.fake.FakeItemRepository
import com.buildorbreak.core.domain.fake.FakeMeasurementRepository
import com.buildorbreak.core.domain.fake.FakeOccurrenceRepository
import com.buildorbreak.core.domain.fake.FakePlanRepository
import com.buildorbreak.core.domain.goal.DefaultGoalCalculator
import com.buildorbreak.core.domain.goal.GoalCloser
import com.buildorbreak.core.domain.goal.GoalProgressWriter
import com.buildorbreak.core.domain.goal.GoalSources
import com.buildorbreak.core.model.enums.GoalKind
import com.buildorbreak.core.model.enums.ValueKind
import com.buildorbreak.core.model.execution.Measurement
import com.buildorbreak.core.model.plan.Plan
import com.buildorbreak.core.testing.fixtures.GoalFixtures
import com.buildorbreak.core.testing.fixtures.GoalFixtures.goal
import com.buildorbreak.core.testing.fixtures.PlanFixtures
import com.buildorbreak.core.testing.time.FakeTimeProvider
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

private val ZONE: ZoneId = ZoneId.of("Asia/Kolkata")

/** Day six of the ten day goal, so five days are behind it and can be rebuilt. */
private val NOW: Instant = GoalFixtures.START.plusDays(5).atStartOfDay(ZONE).toInstant()

/**
 * Correcting a number that has already been averaged into the history.
 *
 * The behaviour under test is the awkward one: a weigh in typed wrong does
 * not stop being wrong at midnight, so putting it right has to put the rows
 * built on it right too. Everything else on this screen is a list.
 */
class ReadingUseCasesTest {

    private val plans = FakePlanRepository()
    private val goals = FakeGoalRepository()
    private val items = FakeItemRepository()
    private val measurements = FakeMeasurementRepository()
    private val occurrences = FakeOccurrenceRepository()
    private val closes = FakeDayCloseRepository()

    private val time = FakeTimeProvider(initial = NOW, currentZone = ZONE)

    private val dispatchers = object : AppDispatchers {
        override val default = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
        override val main = Dispatchers.Unconfined
    }

    private val calculator = DefaultGoalCalculator()

    private val closer = GoalCloser(
        sources = GoalSources(goals, items, measurements, occurrences, closes),
        writer = GoalProgressWriter(calculator),
        calculator = calculator,
    )

    private val recompute = RecomputeGoalHistoryUseCase(plans, goals, closer, time)
    private val observe = ObserveGoalReadingsUseCase(plans, goals, measurements, dispatchers)
    private val save = SaveReadingUseCase(measurements, recompute, dispatchers)
    private val delete = DeleteReadingUseCase(measurements, recompute, dispatchers)

    private fun givenAPlan() {
        plans.plans.value = listOf(
            Plan(
                id = PlanFixtures.PLAN_ID,
                name = "Weekdays",
                isActive = true,
                zone = ZONE,
                createdAt = Instant.EPOCH,
            ),
        )
    }

    private suspend fun givenAMeasuredGoal() {
        givenAPlan()
        goals.upsert(goal(kind = GoalKind.NUMBER, startValue = 48.0, targetValue = 52.0))
    }

    private suspend fun reading(dayOffset: Long, value: Double): Measurement {
        val row = Measurement(
            id = 0,
            itemId = 1,
            occurrenceId = null,
            date = GoalFixtures.START.plusDays(dayOffset),
            value = value,
            kind = ValueKind.WEIGHT_KG,
        )
        measurements.upsert(row)

        return measurements.measurements.value.last()
    }

    @Test
    fun `no goal means no series to show`() = runTest {
        givenAPlan()

        assertThat(observe().first()).isNull()
    }

    @Test
    fun `a counting goal has no readings, because it counts completions`() = runTest {
        givenAPlan()
        goals.upsert(goal(kind = GoalKind.COUNT, itemId = 1))
        reading(dayOffset = 0, value = 48.0)

        assertThat(observe().first()).isNull()
    }

    @Test
    fun `the series comes back newest first`() = runTest {
        givenAMeasuredGoal()
        reading(dayOffset = 0, value = 48.0)
        reading(dayOffset = 2, value = 49.0)
        reading(dayOffset = 1, value = 48.5)

        val series = observe().first()

        assertThat(series!!.readings.map { it.value }).containsExactly(49.0, 48.5, 48.0).inOrder()
    }

    @Test
    fun `correcting a reading replaces it rather than adding a second one`() = runTest {
        givenAMeasuredGoal()
        val typo = reading(dayOffset = 1, value = 720.0)

        save(typo, value = 72.0)

        val series = observe().first()
        assertThat(series!!.readings).hasSize(1)
        assertThat(series.readings.single().value).isEqualTo(72.0)
    }

    @Test
    fun `a corrected reading rewrites the history that was averaged from it`() = runTest {
        givenAMeasuredGoal()
        reading(dayOffset = 0, value = 48.0)
        val typo = reading(dayOffset = 1, value = 720.0)
        recompute(GoalFixtures.START)

        val poisoned = goals.observeProgress(GoalFixtures.GOAL_ID).first()
            .first { it.date == GoalFixtures.START.plusDays(1) }
        assertThat(poisoned.smoothedValue).isGreaterThan(100.0)

        save(typo, value = 49.0)

        val fixed = goals.observeProgress(GoalFixtures.GOAL_ID).first()
            .first { it.date == GoalFixtures.START.plusDays(1) }
        assertThat(fixed.smoothedValue).isWithin(TOLERANCE).of(48.5)
    }

    @Test
    fun `removing a reading takes it out of the average too`() = runTest {
        givenAMeasuredGoal()
        reading(dayOffset = 0, value = 48.0)
        val typo = reading(dayOffset = 1, value = 720.0)
        recompute(GoalFixtures.START)

        delete(typo)

        assertThat(observe().first()!!.readings).hasSize(1)

        val fixed = goals.observeProgress(GoalFixtures.GOAL_ID).first()
            .first { it.date == GoalFixtures.START.plusDays(1) }
        assertThat(fixed.smoothedValue).isWithin(TOLERANCE).of(48.0)
    }

    @Test
    fun `a value that is not a number is refused rather than written`() = runTest {
        givenAMeasuredGoal()
        val row = reading(dayOffset = 1, value = 49.0)

        assertThat(save(row, value = Double.NaN)).isInstanceOf(Outcome.Failure::class.java)
        assertThat(observe().first()!!.readings.single().value).isEqualTo(49.0)
    }

    @Test
    fun `the rebuild stops at yesterday, because today has no row until tonight`() = runTest {
        givenAMeasuredGoal()
        reading(dayOffset = 0, value = 48.0)

        recompute(GoalFixtures.START)

        val dates = goals.observeProgress(GoalFixtures.GOAL_ID).first().map { it.date }
        assertThat(dates).doesNotContain(time.today())
        assertThat(dates).contains(time.today().minusDays(1))
    }

    private companion object {
        const val TOLERANCE = 0.001
    }
}
