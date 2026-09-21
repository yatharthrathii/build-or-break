package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.domain.error.DomainError.DataError
import com.buildorbreak.core.domain.export.BackupProblem
import com.buildorbreak.core.domain.export.CURRENT_SCHEMA_VERSION
import com.buildorbreak.core.domain.export.ExportBuilder
import com.buildorbreak.core.domain.export.ExportReader
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
import com.buildorbreak.core.domain.fake.RecordingWidgetGateway
import com.buildorbreak.core.domain.goal.DefaultGoalCalculator
import com.buildorbreak.core.domain.goal.GoalCloser
import com.buildorbreak.core.domain.goal.GoalProgressWriter
import com.buildorbreak.core.domain.goal.GoalSources
import com.buildorbreak.core.domain.repository.ResetRepository
import com.buildorbreak.core.domain.resolver.DefaultTimelineResolver
import com.buildorbreak.core.model.enums.DayMode
import com.buildorbreak.core.model.enums.DayQuality
import com.buildorbreak.core.model.enums.GoalKind
import com.buildorbreak.core.model.enums.Milestone
import com.buildorbreak.core.model.enums.OccurrenceState
import com.buildorbreak.core.model.enums.ValueKind
import com.buildorbreak.core.model.execution.Measurement
import com.buildorbreak.core.model.execution.Occurrence
import com.buildorbreak.core.model.goal.DayClose
import com.buildorbreak.core.model.goal.Goal
import com.buildorbreak.core.model.goal.MilestoneAward
import com.buildorbreak.core.model.plan.Anchor
import com.buildorbreak.core.model.plan.DayTemplate
import com.buildorbreak.core.model.plan.Plan
import com.buildorbreak.core.model.plan.Weekdays
import com.buildorbreak.core.testing.fixtures.PlanFixtures
import com.buildorbreak.core.testing.time.FakeTimeProvider
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

private val ZONE: ZoneId = ZoneId.of("Asia/Kolkata")
private val NOW: Instant = Instant.parse("2026-09-18T05:00:00Z")
private val TODAY: LocalDate = LocalDate.of(2026, 9, 18)

/**
 * A backup written and put back.
 *
 * The export has always claimed to round trip, and a claim like that is only
 * worth the test that goes both ways: everything here is written, exported,
 * wiped, restored, and compared with what went in. The two halves live in
 * different files and are easy to let drift apart, which is exactly the kind
 * of thing nobody notices until the day somebody actually needs the file.
 *
 * The comparisons are by content and never by id. Ids are rebuilt on the way
 * back in, and a test that expected them to survive would be asserting the
 * one thing a restore must not do.
 */
class RestoreBackupUseCaseTest {

    private val plans = FakePlanRepository()
    private val templates = FakeTemplateRepository()
    private val items = FakeItemRepository()
    private val goals = FakeGoalRepository()
    private val occurrences = FakeOccurrenceRepository()
    private val measurements = FakeMeasurementRepository()
    private val milestones = FakeMilestoneRepository()
    private val closes = FakeDayCloseRepository()
    private val settings = FakeSettingsRepository()
    private val dayLogs = FakeDayLogRepository()
    private val alarms = RecordingAlarmGateway()
    private val widget = RecordingWidgetGateway()

    private val time = FakeTimeProvider(initial = NOW, currentZone = ZONE)

    private val dispatchers = object : AppDispatchers {
        override val default = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
        override val main = Dispatchers.Unconfined
    }

    /**
     * A wipe that really empties the fakes.
     *
     * The shared `RecordingResetRepository` only counts the call, which is
     * the right fake for a screen that asks for a wipe. Here the wipe is half
     * of the behaviour under test: a restore that appended to what was
     * already there would pass every assertion below on the rows it added.
     */
    private val reset = object : ResetRepository {
        override suspend fun wipeEverything(): Outcome<Unit, DataError> {
            plans.plans.value = emptyList()
            templates.templates.value = emptyList()
            items.items.value = emptyList()
            items.blocks.value = emptyList()
            goals.goals.value = emptyList()
            goals.progress.value = emptyList()
            goals.leftOutWeeks.value = emptySet()
            occurrences.occurrences.value = emptyList()
            measurements.measurements.value = emptyList()
            closes.closes.value = emptyList()
            milestones.awards.value = emptyList()

            return Outcome.Success(Unit)
        }
    }

    private val observeToday = ObserveTodayUseCase(
        plans = plans,
        templates = templates,
        items = items,
        occurrences = occurrences,
        dayLogs = dayLogs,
        settings = settings,
        resolver = DefaultTimelineResolver(),
        time = time,
        dispatchers = dispatchers,
    )

    /** The eight tables a backup touches, as the use cases take them. */
    private val backup = BackupSources(
        plans = plans,
        templates = templates,
        items = items,
        goals = goals,
        occurrences = occurrences,
        measurements = measurements,
        milestones = milestones,
        closes = closes,
    )

    private val export = ExportPlanUseCase(
        sources = backup,
        builder = ExportBuilder(),
        time = time,
        dispatchers = dispatchers,
    )

    private val calculator = DefaultGoalCalculator()

    private val recompute = RecomputeGoalHistoryUseCase(
        plans = plans,
        goals = goals,
        closer = GoalCloser(
            sources = GoalSources(goals, items, measurements, occurrences, closes),
            writer = GoalProgressWriter(calculator),
            calculator = calculator,
        ),
        time = time,
    )

    private val restore = RestoreBackupUseCase(
        reader = ExportReader(),
        reset = reset,
        sources = backup,
        after = RestoreAftermath(
            recompute = recompute,
            reschedule = RescheduleAllUseCase(observeToday, occurrences, alarms, time, dispatchers),
            alarms = alarms,
            widget = widget,
        ),
        settings = settings,
        time = time,
        dispatchers = dispatchers,
    )

    // A morning, with a step that hangs off another one. The relative anchor
    // is the part of a restore most likely to come back pointing at nothing.
    private suspend fun givenADayWorthKeeping() {
        plans.plans.value = listOf(
            Plan(
                id = PlanFixtures.PLAN_ID,
                name = "My routine",
                isActive = true,
                zone = ZONE,
                createdAt = Instant.parse("2026-08-01T00:00:00Z"),
            ),
        )

        templates.upsert(
            DayTemplate(
                id = PlanFixtures.TEMPLATE_ID,
                planId = PlanFixtures.PLAN_ID,
                name = "Weekdays",
                weekdays = Weekdays.EveryDay,
                isDefault = true,
                mode = DayMode.NORMAL,
                sortOrder = 0,
            ),
        )

        items.upsert(
            PlanFixtures.item(
                id = 1,
                title = "Wake up",
                anchor = Anchor.Fixed(LocalTime.of(8, 0)),
                valueKind = ValueKind.WEIGHT_KG,
            ),
        )
        items.upsert(
            PlanFixtures.item(
                id = 2,
                title = "Shake",
                anchor = Anchor.Relative(parentItemId = 1, offset = 30.minutes),
            ),
        )
    }

    private suspend fun givenSomeHistory() {
        occurrences.occurrences.value = listOf(
            Occurrence(
                id = 7,
                itemId = 1,
                date = TODAY.minusDays(1),
                plannedAt = TODAY.minusDays(1).atTime(8, 0),
                scheduledAt = null,
                firedAt = null,
                settledAt = NOW,
                state = OccurrenceState.DONE,
                sequenceInDay = 0,
            ),
        )

        measurements.upsert(
            Measurement(
                id = 0,
                itemId = 1,
                occurrenceId = 7,
                date = TODAY.minusDays(1),
                value = 50.5,
                kind = ValueKind.WEIGHT_KG,
            ),
        )

        closes.upsert(
            DayClose(
                date = TODAY.minusDays(1),
                planId = PlanFixtures.PLAN_ID,
                itemsDone = 2,
                itemsMinimum = 0,
                itemsMissed = 0,
                itemsTotal = 2,
                quality = DayQuality.GOOD,
                closedAt = NOW,
            ),
        )
    }

    private suspend fun givenAGoal() {
        goals.upsert(
            Goal(
                id = 0,
                planId = PlanFixtures.PLAN_ID,
                kind = GoalKind.NUMBER,
                title = "Weight gain",
                itemId = null,
                valueKind = ValueKind.WEIGHT_KG,
                startValue = 50.0,
                targetValue = 51.5,
                startDate = TODAY.minusDays(2),
                targetDate = TODAY.plusDays(12),
                isActive = true,
            ),
        )
    }

    @Test
    fun `a plan survives being exported and put back`() = runTest {
        givenADayWorthKeeping()

        val file = export()!!
        val outcome = restore(file)

        assertThat(outcome).isInstanceOf(Outcome.Success::class.java)

        val plan = plans.observeActive().first()!!
        assertThat(plan.name).isEqualTo("My routine")
        assertThat(plan.zone).isEqualTo(ZONE)

        val template = templates.observeForPlan(plan.id).first().single()
        assertThat(template.name).isEqualTo("Weekdays")
        assertThat(items.observeForTemplate(template.id).first().map { it.title })
            .containsExactly("Wake up", "Shake")
    }

    @Test
    fun `a step that hangs off another one still points at it afterwards`() = runTest {
        givenADayWorthKeeping()

        restore(export()!!)

        val template = templates.templates.value.single()
        val restored = items.observeForTemplate(template.id).first()
        val wake = restored.single { it.title == "Wake up" }
        val shake = restored.single { it.title == "Shake" }

        assertThat(shake.anchor).isEqualTo(Anchor.Relative(parentItemId = wake.id, offset = 30.minutes))
    }

    @Test
    fun `the goal and its readings come back with it`() = runTest {
        givenADayWorthKeeping()
        givenAGoal()
        givenSomeHistory()

        restore(export()!!)

        val plan = plans.observeActive().first()!!
        val goal = goals.observeActive(plan.id).first()!!
        assertThat(goal.title).isEqualTo("Weight gain")
        assertThat(goal.targetValue).isEqualTo(51.5)

        assertThat(measurements.measurements.value.map { it.value }).containsExactly(50.5)
    }

    @Test
    fun `a restored goal stands where it stood, not back at nought`() = runTest {
        givenADayWorthKeeping()
        givenAGoal()
        givenSomeHistory()

        restore(export()!!)

        // Yesterday's weigh in is in the file, and the goal's row for that day is rebuilt from it.
        val row = goals.progress.value.single { it.date == TODAY.minusDays(1) }
        assertThat(row.rawValue).isEqualTo(50.5)
    }

    @Test
    fun `a week left out of a goal is still left out after a restore`() = runTest {
        givenADayWorthKeeping()
        givenAGoal()
        givenSomeHistory()
        // The seventeenth of September 2026 is a Thursday, in the week of the fourteenth.
        val week = LocalDate.of(2026, 9, 14)
        goals.setWeekCounted(goals.goals.value.single().id, week, counted = false)

        restore(export()!!)

        val goalId = goals.goals.value.single().id
        assertThat(goals.observeLeftOutWeeks(goalId).first()).containsExactly(week)
        assertThat(goals.progress.value.single { it.date == TODAY.minusDays(1) }.counted).isFalse()
    }

    @Test
    fun `two running goals both come back running`() = runTest {
        givenADayWorthKeeping()
        givenAGoal()
        goals.upsert(goals.goals.value.single().copy(id = 0, title = "Read more", kind = GoalKind.CONSISTENCY))

        restore(export()!!)

        val plan = plans.observeActive().first()!!
        assertThat(goals.observeAllActive(plan.id).first().map { it.title }).containsExactly("Weight gain", "Read more")
    }

    @Test
    fun `the days that were closed are still closed`() = runTest {
        givenADayWorthKeeping()
        givenSomeHistory()

        restore(export()!!)

        val close = closes.observeRange(TODAY.minusDays(7), TODAY).first().single()
        assertThat(close.date).isEqualTo(TODAY.minusDays(1))
        assertThat(close.itemsDone).isEqualTo(2)
        assertThat(close.quality).isEqualTo(DayQuality.GOOD)
    }

    @Test
    fun `what happened comes back as it happened, not as pending`() = runTest {
        givenADayWorthKeeping()
        givenSomeHistory()

        restore(export()!!)

        val yesterday = occurrences.occurrences.value.single { it.date == TODAY.minusDays(1) }
        assertThat(yesterday.state).isEqualTo(OccurrenceState.DONE)
        assertThat(yesterday.settledAt).isEqualTo(NOW)
    }

    @Test
    fun `a badge already earned comes back earned, and is not announced again`() = runTest {
        givenADayWorthKeeping()
        milestones.award(
            MilestoneAward(
                milestone = Milestone.FIRST_WEEK,
                goalId = null,
                itemId = null,
                awardedOn = TODAY.minusDays(30),
                seenAt = NOW,
            ),
        )

        restore(export()!!)

        val awarded = milestones.awarded().single()
        assertThat(awarded.milestone).isEqualTo(Milestone.FIRST_WEEK)
        assertThat(awarded.awardedOn).isEqualTo(TODAY.minusDays(30))
        assertThat(milestones.observeUnseen().first()).isEmpty()
    }

    @Test
    fun `a restore does not drop somebody back into the first run`() = runTest {
        givenADayWorthKeeping()
        settings.setOnboardingComplete(true)

        restore(export()!!)

        assertThat(settings.onboardingComplete.first()).isTrue()
    }

    @Test
    fun `restoring replaces what is there rather than adding to it`() = runTest {
        givenADayWorthKeeping()
        val file = export()!!

        restore(file)
        restore(file)

        assertThat(plans.plans.value).hasSize(1)
        assertThat(items.items.value.map { it.title }).containsExactly("Wake up", "Shake")
    }

    @Test
    fun `a file that is not ours is refused and nothing is touched`() = runTest {
        givenADayWorthKeeping()

        val outcome = restore("{\"this\": \"is not a backup\"}")

        assertThat(outcome).isEqualTo(Outcome.Failure(BackupProblem.NOT_READABLE))
        assertThat(items.items.value).hasSize(2)
    }

    @Test
    fun `a file from a newer app says so rather than half reading it`() = runTest {
        givenADayWorthKeeping()

        val newer = export()!!.replace(
            "\"schema_version\": $CURRENT_SCHEMA_VERSION",
            "\"schema_version\": ${CURRENT_SCHEMA_VERSION + 1}",
        )

        assertThat(restore(newer)).isEqualTo(Outcome.Failure(BackupProblem.TOO_NEW))
        assertThat(items.items.value).hasSize(2)
    }
}
