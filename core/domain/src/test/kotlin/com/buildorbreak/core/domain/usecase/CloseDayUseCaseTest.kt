package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.domain.fake.FakeDayCloseRepository
import com.buildorbreak.core.domain.fake.FakeDayLogRepository
import com.buildorbreak.core.domain.fake.FakeGoalRepository
import com.buildorbreak.core.domain.fake.FakeItemRepository
import com.buildorbreak.core.domain.fake.FakeMeasurementRepository
import com.buildorbreak.core.domain.fake.FakeMilestoneRepository
import com.buildorbreak.core.domain.fake.FakeOccurrenceRepository
import com.buildorbreak.core.domain.fake.FakePlanRepository
import com.buildorbreak.core.domain.fake.FakeSettingsRepository
import com.buildorbreak.core.domain.fake.FakeTemplateRepository
import com.buildorbreak.core.domain.fake.RecordingAlarmGateway
import com.buildorbreak.core.domain.fake.RecordingNotificationGateway
import com.buildorbreak.core.domain.goal.DefaultDayQualityClassifier
import com.buildorbreak.core.domain.goal.DefaultGoalCalculator
import com.buildorbreak.core.domain.goal.DefaultMilestoneEvaluator
import com.buildorbreak.core.domain.goal.GoalCloser
import com.buildorbreak.core.domain.goal.GoalProgressWriter
import com.buildorbreak.core.domain.goal.GoalSources
import com.buildorbreak.core.domain.parse.PlanTextParser
import com.buildorbreak.core.domain.resolver.DefaultTimelineResolver
import com.buildorbreak.core.model.enums.DayQuality
import com.buildorbreak.core.model.enums.OccurrenceState
import com.buildorbreak.core.testing.time.FakeTimeProvider
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * A day that was never opened is a day on which nothing was done.
 *
 * Occurrence rows only exist for a day the app was opened on or that the
 * daily job ran for. A phone left in a drawer from Monday to Friday has no
 * rows for any of them, and a close that counted rows alone wrote five days
 * of nothing down as five perfect days. The run grew, the thirty day figure
 * climbed, and a consistency goal moved through a week in which nothing
 * happened. Every number in the app is built on these rows.
 */
class CloseDayUseCaseTest {

    private val plans = FakePlanRepository()
    private val templates = FakeTemplateRepository()
    private val items = FakeItemRepository()
    private val occurrences = FakeOccurrenceRepository()
    private val dayLogs = FakeDayLogRepository()
    private val closes = FakeDayCloseRepository()
    private val milestones = FakeMilestoneRepository()
    private val goals = FakeGoalRepository()
    private val measurements = FakeMeasurementRepository()
    private val notifications = RecordingNotificationGateway()

    // 00:30 UTC is 06:00 in Kolkata on Monday 5 January.
    private val time = FakeTimeProvider(
        initial = Instant.parse("2026-01-05T00:30:00Z"),
        currentZone = ZoneId.of("Asia/Kolkata"),
    )

    private val dispatchers = object : AppDispatchers {
        override val default = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
        override val main = Dispatchers.Unconfined
    }

    private val observeToday = ObserveTodayUseCase(
        plans = plans,
        templates = templates,
        items = items,
        occurrences = occurrences,
        dayLogs = dayLogs,
        settings = FakeSettingsRepository(),
        resolver = DefaultTimelineResolver(),
        time = time,
        dispatchers = dispatchers,
    )

    private val reschedule = RescheduleAllUseCase(observeToday, occurrences, RecordingAlarmGateway(), time, dispatchers)

    private val importPlan = ImportPlanUseCase(
        plans = plans,
        templates = templates,
        items = items,
        reschedule = reschedule,
        time = time,
        dispatchers = dispatchers,
    )

    private val closeDay = CloseDayUseCase(
        sources = CloseSources(plans, occurrences, closes, milestones),
        observeToday = observeToday,
        quality = DefaultDayQualityClassifier(),
        evaluator = DefaultMilestoneEvaluator(),
        goals = GoalCloser(
            sources = GoalSources(goals, items, measurements, occurrences, closes),
            writer = GoalProgressWriter(),
            calculator = DefaultGoalCalculator(),
        ),
        notifications = notifications,
        time = time,
        dispatchers = dispatchers,
    )

    private suspend fun givenAPlan() {
        importPlan(PlanTextParser().parse("07:30 Gym\n09:00 Study\n21:00 Read").items, templateName = "Weekday")
    }

    @Test
    fun `a day the app never opened is closed against what was planned`() = runTest {
        givenAPlan()
        time.advanceByDays(1)

        val written = closeDay()

        val yesterday = written.single()
        assertThat(yesterday.itemsTotal).isEqualTo(3)
        assertThat(yesterday.itemsDone).isEqualTo(0)
        assertThat(yesterday.itemsMissed).isEqualTo(3)
        assertThat(yesterday.quality).isEqualTo(DayQuality.POOR)
    }

    @Test
    fun `a week away is a week of missed days, not a week of kept ones`() = runTest {
        givenAPlan()
        time.advanceByDays(6)

        val written = closeDay()

        // The day the plan was made, and the five after it.
        assertThat(written).hasSize(6)
        assertThat(written.map { it.quality }.toSet()).containsExactly(DayQuality.POOR)
    }

    @Test
    fun `the days before the plan existed are not closed at all`() = runTest {
        givenAPlan()
        time.advanceByDays(1)

        closeDay()

        val created = time.today().minusDays(1)
        assertThat(closes.closes.value.map { it.date }).containsExactly(created)
    }

    @Test
    fun `what was done on an opened day still counts`() = runTest {
        givenAPlan()
        val row = occurrences.observeForDate(time.today()).first().first()
        occurrences.settle(row.id, OccurrenceState.DONE, time.now())
        time.advanceByDays(1)

        val yesterday = closeDay().single()

        assertThat(yesterday.itemsDone).isEqualTo(1)
        assertThat(yesterday.itemsMissed).isEqualTo(2)
        assertThat(yesterday.itemsTotal).isEqualTo(3)
    }

    @Test
    fun `open rows are settled as missed by the close`() = runTest {
        givenAPlan()
        val date = time.today()
        time.advanceByDays(1)

        closeDay()

        val states = occurrences.observeForDate(date).first().map { it.state }.toSet()
        assertThat(states).containsExactly(OccurrenceState.MISSED)
    }

    @Test
    fun `closing twice writes the same day once`() = runTest {
        givenAPlan()
        time.advanceByDays(1)

        closeDay()
        closeDay()

        assertThat(closes.closes.value).hasSize(1)
    }
}
