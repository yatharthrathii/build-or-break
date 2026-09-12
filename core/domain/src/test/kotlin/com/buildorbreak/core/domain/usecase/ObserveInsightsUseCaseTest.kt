package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.domain.fake.FakeDayCloseRepository
import com.buildorbreak.core.domain.fake.FakeGoalRepository
import com.buildorbreak.core.domain.fake.FakeItemRepository
import com.buildorbreak.core.domain.fake.FakeMeasurementRepository
import com.buildorbreak.core.domain.fake.FakeOccurrenceRepository
import com.buildorbreak.core.domain.fake.FakePlanRepository
import com.buildorbreak.core.domain.fake.FakeSettingsRepository
import com.buildorbreak.core.domain.fake.FakeTemplateRepository
import com.buildorbreak.core.domain.goal.DefaultGoalCalculator
import com.buildorbreak.core.domain.review.DefaultWeeklyReviewBuilder
import com.buildorbreak.core.domain.review.InsightsPeriod
import com.buildorbreak.core.domain.review.SkipCount
import com.buildorbreak.core.model.enums.GoalKind
import com.buildorbreak.core.model.enums.OccurrenceState
import com.buildorbreak.core.model.enums.ReviewStory
import com.buildorbreak.core.model.enums.SkipChip
import com.buildorbreak.core.model.execution.SkipReason
import com.buildorbreak.core.model.plan.Plan
import com.buildorbreak.core.testing.fixtures.ExecutionFixtures
import com.buildorbreak.core.testing.fixtures.GoalFixtures
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

class ObserveInsightsUseCaseTest {

    private val plans = FakePlanRepository()
    private val templates = FakeTemplateRepository()
    private val items = FakeItemRepository()
    private val occurrences = FakeOccurrenceRepository()
    private val closes = FakeDayCloseRepository()
    private val settings = FakeSettingsRepository()

    /** A Sunday, so the whole week Monday to Sunday is behind it. */
    private val today: LocalDate = LocalDate.of(2026, 9, 13)
    private val monday: LocalDate = LocalDate.of(2026, 9, 7)
    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    private val time = FakeTimeProvider(
        initial = today.atTime(20, 0).atZone(zone).toInstant(),
        currentZone = zone,
    )

    private val dispatchers = object : AppDispatchers {
        override val default = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
        override val main = Dispatchers.Unconfined
    }

    private val measurements = FakeMeasurementRepository()
    private val goals = FakeGoalRepository()

    private val observeInsights = ObserveInsightsUseCase(
        sources = InsightsSources(
            plans = plans,
            templates = templates,
            items = items,
            occurrences = occurrences,
            closes = closes,
            measurements = measurements,
        ),
        settings = settings,
        reviews = DefaultWeeklyReviewBuilder(),
        observeGoal = ObserveGoalUseCase(
            plans = plans,
            goals = goals,
            today = GoalToday(occurrences, measurements, items, time),
            calculator = DefaultGoalCalculator(),
            time = time,
            dispatchers = dispatchers,
        ),
        time = time,
        dispatchers = dispatchers,
    )

    private suspend fun seedPlan() {
        plans.upsert(Plan(id = 0, name = "Plan", isActive = true, zone = zone, createdAt = Instant.EPOCH))
        templates.upsert(PlanFixtures.template(id = 0, planId = 1))
        items.upsert(PlanFixtures.item(id = 0, templateId = 1, title = "Walk"))
        items.upsert(PlanFixtures.item(id = 0, templateId = 1, title = "Read"))
    }

    @Test
    fun `keeping the plan while the goal does not move says the plan is too small`() = runTest {
        seedPlan()
        // A whole previous week and most of this one kept, so the person is
        // doing the work, and a goal sitting at a tenth of the way through
        // with two thirds of the time gone, so the work is not enough.
        closes.closes.value = GoalFixtures.closes(from = monday.minusWeeks(1), days = 7) +
            GoalFixtures.closes(from = monday, days = 5)
        goals.upsert(
            GoalFixtures.goal(
                kind = GoalKind.NUMBER,
                startValue = 0.0,
                targetValue = 10.0,
                startDate = monday.minusWeeks(4),
                targetDate = monday.plusWeeks(2),
            ),
        )
        goals.upsertProgress(GoalFixtures.progress(date = monday, rawValue = 1.0, smoothedValue = 1.0))

        val insights = observeInsights(InsightsPeriod.WEEK).first()!!

        assertThat(insights.story).isEqualTo(ReviewStory.PLAN_TOO_SMALL)
    }

    @Test
    fun `no plan means nothing to show`() = runTest {
        assertThat(observeInsights(InsightsPeriod.WEEK).first()).isNull()
    }

    @Test
    fun `kept and total count only what was settled this week`() = runTest {
        seedPlan()
        occurrences.occurrences.value = listOf(
            ExecutionFixtures.done(itemId = 1, date = monday, id = 1, zone = zone),
            ExecutionFixtures.missed(itemId = 2, date = monday, id = 2),
            ExecutionFixtures.done(itemId = 1, date = monday.plusDays(1), id = 3, zone = zone),
            // Still open. Not settled, so not counted either way.
            ExecutionFixtures.occurrence(itemId = 2, date = today, id = 4, state = OccurrenceState.PENDING),
            // Last week. Feeds the comparison, not this week.
            ExecutionFixtures.done(itemId = 1, date = monday.minusDays(2), id = 5, zone = zone),
        )

        val insights = observeInsights(InsightsPeriod.WEEK).first()!!

        assertThat(insights.kept).isEqualTo(2)
        assertThat(insights.total).isEqualTo(3)
        assertThat(insights.previousKept).isEqualTo(1)
        assertThat(insights.previousTotal).isEqualTo(1)
    }

    @Test
    fun `the reasons given for skips are counted, commonest first`() = runTest {
        seedPlan()
        occurrences.occurrences.value = listOf(
            ExecutionFixtures.occurrence(itemId = 1, date = monday, id = 1, state = OccurrenceState.SKIPPED),
            ExecutionFixtures.occurrence(itemId = 2, date = monday, id = 2, state = OccurrenceState.SKIPPED),
            ExecutionFixtures.occurrence(
                itemId = 1,
                date = monday.plusDays(1),
                id = 3,
                state = OccurrenceState.SKIPPED,
            ),
            // Skipped, and no reason given. Never counted, never guessed at.
            ExecutionFixtures.occurrence(
                itemId = 2,
                date = monday.plusDays(1),
                id = 4,
                state = OccurrenceState.SKIPPED,
            ),
        )
        reason(occurrenceId = 1, chip = SkipChip.WORK_CAME_UP)
        reason(occurrenceId = 2, chip = SkipChip.NO_TIME)
        reason(occurrenceId = 3, chip = SkipChip.WORK_CAME_UP)

        val reasons = observeInsights(InsightsPeriod.WEEK).first()!!.skipReasons

        assertThat(reasons).containsExactly(
            SkipCount(SkipChip.WORK_CAME_UP, 2),
            SkipCount(SkipChip.NO_TIME, 1),
        ).inOrder()
    }

    @Test
    fun `nothing is shown when nobody said why`() = runTest {
        seedPlan()
        occurrences.occurrences.value = listOf(
            ExecutionFixtures.occurrence(itemId = 1, date = monday, id = 1, state = OccurrenceState.SKIPPED),
        )

        assertThat(observeInsights(InsightsPeriod.WEEK).first()!!.skipReasons).isEmpty()
    }

    private suspend fun reason(occurrenceId: Long, chip: SkipChip) {
        measurements.recordSkipReason(
            SkipReason(id = 0, occurrenceId = occurrenceId, chip = chip, text = null, createdAt = Instant.EPOCH),
        )
    }

    @Test
    fun `a week has seven bars and a day with nothing settled is empty, not zero`() = runTest {
        seedPlan()
        occurrences.occurrences.value = listOf(ExecutionFixtures.done(itemId = 1, date = monday, id = 1, zone = zone))

        val bars = observeInsights(InsightsPeriod.WEEK).first()!!.bars

        assertThat(bars).hasSize(7)
        assertThat(bars.first().fraction).isEqualTo(1f)
        assertThat(bars.last().fraction).isNull()
        assertThat(bars.last().isWeekend).isTrue()
    }

    @Test
    fun `a month has four bars, one per week`() = runTest {
        seedPlan()

        val insights = observeInsights(InsightsPeriod.MONTH).first()!!

        assertThat(insights.bars).hasSize(4)
        assertThat(insights.from).isEqualTo(monday.minusWeeks(3))
        assertThat(insights.to).isEqualTo(monday.plusDays(6))
    }

    @Test
    fun `the step table lists only steps that had a chance this period`() = runTest {
        seedPlan()
        occurrences.occurrences.value = listOf(
            ExecutionFixtures.done(itemId = 1, date = monday, id = 1, zone = zone),
            ExecutionFixtures.missed(itemId = 1, date = monday.plusDays(1), id = 2),
        )

        val steps = observeInsights(InsightsPeriod.WEEK).first()!!.steps

        assertThat(steps.map { it.title }).containsExactly("Walk")
        assertThat(steps.single().kept).isEqualTo(1)
        assertThat(steps.single().outOf).isEqualTo(2)
    }

    @Test
    fun `a dismissed week hides the suggestion until next week`() = runTest {
        seedPlan()
        // Enough misses on one weekday, across weeks, for the pattern detector.
        occurrences.occurrences.value = (0 until 4).map { weeksAgo ->
            ExecutionFixtures.missed(itemId = 2, date = monday.minusWeeks(weeksAgo.toLong()), id = 10L + weeksAgo)
        }

        val before = observeInsights(InsightsPeriod.WEEK).first()!!
        settings.setDismissedReviewWeek(monday)
        val after = observeInsights(InsightsPeriod.WEEK).first()!!

        // Whether or not the detector found a pattern, dismissal must never
        // leave a suggestion standing for the dismissed week.
        assertThat(after.suggestion).isNull()
        if (before.suggestion != null) assertThat(before.suggestion!!.weekStart).isEqualTo(monday)
    }
}
