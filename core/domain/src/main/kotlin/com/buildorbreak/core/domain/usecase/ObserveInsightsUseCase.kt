package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.time.TimeProvider
import com.buildorbreak.core.domain.repository.DayCloseRepository
import com.buildorbreak.core.domain.repository.ItemRepository
import com.buildorbreak.core.domain.repository.MeasurementRepository
import com.buildorbreak.core.domain.repository.OccurrenceRepository
import com.buildorbreak.core.domain.repository.PlanRepository
import com.buildorbreak.core.domain.repository.SettingsRepository
import com.buildorbreak.core.domain.repository.TemplateRepository
import com.buildorbreak.core.domain.review.InsightBar
import com.buildorbreak.core.domain.review.Insights
import com.buildorbreak.core.domain.review.InsightsPeriod
import com.buildorbreak.core.domain.review.ReviewInput
import com.buildorbreak.core.domain.review.SkipCount
import com.buildorbreak.core.domain.review.StepStat
import com.buildorbreak.core.domain.review.Suggestion
import com.buildorbreak.core.domain.review.TimeShiftDetector
import com.buildorbreak.core.domain.review.WeeklyReviewBuilder
import com.buildorbreak.core.model.enums.OccurrenceState
import com.buildorbreak.core.model.execution.Occurrence
import com.buildorbreak.core.model.execution.SkipReason
import com.buildorbreak.core.model.plan.Item
import com.buildorbreak.core.model.plan.Plan
import com.buildorbreak.core.model.review.WeeklyReview
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlin.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest

private const val DAYS_IN_WEEK = 7L
private const val WEEKS_IN_MONTH = 4L

/** How far back the slip and skip detectors read. Four weeks, whatever the view. */
private const val PATTERN_WINDOW_DAYS = 28L

/**
 * Everything the Insights screen shows, recomputed when the day changes.
 *
 * The heavy reads are one shot, so this observes the two cheap things that
 * mean the answer has changed, today's occurrences and the dismissal flag, and
 * rebuilds on either. A settle on Today shows up here on the next open without
 * a four week query being held open in between.
 */
/**
 * The six tables a review reads, in one injectable bag.
 *
 * They always travel together and none of them is interesting on its own here.
 * Injecting them one by one gave this class a constructor nobody could read.
 */
class InsightsSources @Inject constructor(
    val plans: PlanRepository,
    val templates: TemplateRepository,
    val items: ItemRepository,
    val occurrences: OccurrenceRepository,
    val closes: DayCloseRepository,
    val measurements: MeasurementRepository,
)

class ObserveInsightsUseCase @Inject constructor(
    private val sources: InsightsSources,
    private val settings: SettingsRepository,
    private val reviews: WeeklyReviewBuilder,
    private val time: TimeProvider,
    private val dispatchers: AppDispatchers,
) {

    private val plans get() = sources.plans
    private val templates get() = sources.templates
    private val items get() = sources.items
    private val occurrences get() = sources.occurrences
    private val closes get() = sources.closes
    private val measurements get() = sources.measurements

    /**
     * Two samples and no threshold, unlike the detector the weekly review uses.
     * That one decides whether to suggest a move and must be conservative; this
     * one fills a column in a table and is allowed to say "+9m".
     */
    private val slips = TimeShiftDetector(minimumSamples = 2, threshold = Duration.ZERO)

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(period: InsightsPeriod): Flow<Insights?> {
        val today = time.today()

        return combine(
            plans.observeActive(),
            occurrences.observeForDate(today),
            settings.dismissedReviewWeek,
        ) { plan, _, dismissed -> plan to dismissed }
            .mapLatest { (plan, dismissed) -> plan?.let { build(it, period, dismissed, today) } }
            .flowOn(dispatchers.default)
    }

    private suspend fun build(
        plan: Plan,
        period: InsightsPeriod,
        dismissed: LocalDate?,
        today: LocalDate,
    ): Insights {
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val span = if (period == InsightsPeriod.WEEK) DAYS_IN_WEEK else DAYS_IN_WEEK * WEEKS_IN_MONTH
        val from = if (period == InsightsPeriod.WEEK) weekStart else weekStart.minusWeeks(WEEKS_IN_MONTH - 1)
        val to = from.plusDays(span - 1)

        val planItems = itemsOn(plan)
        val current = occurrences.between(from, to)
        val previous = occurrences.between(from.minusDays(span), from.minusDays(1))
        val recent = occurrences.between(today.minusDays(PATTERN_WINDOW_DAYS), today)

        val review = reviewFor(plan, weekStart, planItems, recent)
        val stats = statsFor(planItems, current, recent)

        return Insights(
            period = period,
            from = from,
            to = to,
            kept = current.count { it.isDone },
            total = current.count { it.isSettled },
            previousKept = previous.count { it.isDone }.takeIf { previous.any { it.isSettled } },
            previousTotal = previous.count { it.isSettled }.takeIf { previous.any { it.isSettled } },
            averageSlip = averageSlip(stats),
            bars = barsFor(period, from, current),
            steps = stats,
            skipReasons = skipReasonsIn(current),
            suggestion = suggestionFor(review, stats, weekStart).takeIf { dismissed != weekStart },
            story = review.story,
        )
    }

    /** Counted, commonest first, and only the ones somebody actually gave. */
    private suspend fun skipReasonsIn(period: List<Occurrence>): List<SkipCount> {
        val skipped = period.filter { it.state == OccurrenceState.SKIPPED }.map { it.id }
        if (skipped.isEmpty()) return emptyList()

        return measurements.skipReasonsFor(skipped)
            .mapNotNull { it.chip }
            .groupingBy { it }
            .eachCount()
            .map { (chip, count) -> SkipCount(chip, count) }
            .sortedByDescending { it.count }
    }

    private suspend fun itemsOn(plan: Plan): List<Item> =
        templates.observeForPlan(plan.id).first().flatMap { items.observeForTemplate(it.id).first() }

    private suspend fun reviewFor(
        plan: Plan,
        weekStart: LocalDate,
        planItems: List<Item>,
        recent: List<Occurrence>,
    ): WeeklyReview {
        val weekEnd = weekStart.plusDays(DAYS_IN_WEEK - 1)
        val lastWeekStart = weekStart.minusWeeks(1)

        return reviews.build(
            ReviewInput(
                weekStart = weekStart,
                closes = closes.observeRange(weekStart, weekEnd).first().filter { it.planId == plan.id },
                occurrences = recent.filter { it.date in weekStart..weekEnd },
                items = planItems,
                previousCloses = closes.observeRange(lastWeekStart, weekStart.minusDays(1)).first(),
                recentOccurrences = recent,
                // Without these the detector sees counts and nothing else, so
                // every cause comes back UNKNOWN and the same fix is offered
                // whether a step was forgotten, crowded out or dreaded. Asking
                // why and then not reading the answer is the worst of both:
                // the user is interrupted and the report learns nothing.
                reasons = reasonsFor(recent),
            ),
        )
    }

    /** Every reason given in the trailing window, whatever the current view is. */
    private suspend fun reasonsFor(recent: List<Occurrence>): List<SkipReason> {
        val skipped = recent.filter { it.state == OccurrenceState.SKIPPED }.map { it.id }

        return if (skipped.isEmpty()) emptyList() else measurements.skipReasonsFor(skipped)
    }

    /** One row per step on the plan, in plan order, only when it had a chance. */
    private fun statsFor(planItems: List<Item>, current: List<Occurrence>, recent: List<Occurrence>): List<StepStat> =
        planItems.mapNotNull { item ->
            val settled = current.filter { it.itemId == item.id && it.isSettled }
            if (settled.isEmpty()) return@mapNotNull null

            StepStat(
                itemId = item.id,
                title = item.title,
                kept = settled.count { it.isDone },
                outOf = settled.size,
                slip = slips.detect(item.id, recent, time.zone())?.median,
            )
        }

    /** Only the late ones. Something done early is not a slip anybody minds. */
    private fun averageSlip(stats: List<StepStat>): Duration? {
        val late = stats.mapNotNull { it.slip }.filter { it.isPositive() }
        if (late.isEmpty()) return null

        return late.reduce(Duration::plus) / late.size
    }

    private fun barsFor(period: InsightsPeriod, from: LocalDate, current: List<Occurrence>): List<InsightBar> {
        val count = if (period == InsightsPeriod.WEEK) DAYS_IN_WEEK else WEEKS_IN_MONTH
        val width = if (period == InsightsPeriod.WEEK) 1L else DAYS_IN_WEEK

        return (0 until count).map { index ->
            val start = from.plusDays(index * width)
            val end = start.plusDays(width - 1)
            val settled = current.filter { it.date in start..end && it.isSettled }

            InsightBar(
                start = start,
                fraction = if (settled.isEmpty()) null else settled.count { it.isDone }.toFloat() / settled.size,
                isWeekend = width == 1L && start.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            )
        }
    }

    private fun suggestionFor(review: WeeklyReview, stats: List<StepStat>, weekStart: LocalDate): Suggestion? {
        val problem = review.problem ?: return null
        val answer = review.question?.options?.firstOrNull() ?: return null

        return Suggestion(
            itemId = problem.itemId,
            title = problem.title,
            answer = answer,
            misses = problem.misses,
            outOf = problem.outOf,
            slip = stats.firstOrNull { it.itemId == problem.itemId }?.slip,
            weekStart = weekStart,
        )
    }
}
