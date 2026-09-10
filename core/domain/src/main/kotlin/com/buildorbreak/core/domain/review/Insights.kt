package com.buildorbreak.core.domain.review

import com.buildorbreak.core.model.enums.ReviewStory
import com.buildorbreak.core.model.enums.SkipChip
import com.buildorbreak.core.model.review.ReviewAnswer
import java.time.LocalDate
import kotlin.time.Duration

/** How wide the Insights screen looks. */
enum class InsightsPeriod {
    /** Monday to Sunday of the current week. */
    WEEK,

    /** The last four whole weeks, one bar each. */
    MONTH,
}

/**
 * What the Insights screen shows, worked out once.
 *
 * Numbers a person can check against the list below them: "49 of 63 steps"
 * rather than a percentage on its own, and a per step table where every row is
 * a count. The one thing that is not a count is the suggestion, and that comes
 * from the weekly review builder so the same week always produces the same one.
 */
data class Insights(
    val period: InsightsPeriod,
    val from: LocalDate,
    val to: LocalDate,
    /** Steps settled as done in the period, out of everything settled. */
    val kept: Int,
    val total: Int,
    /** Same window, one period earlier. Null when there was nothing to compare. */
    val previousKept: Int?,
    val previousTotal: Int?,
    /** Mean of the per step medians that ran late. Null when nothing did. */
    val averageSlip: Duration?,
    /** One bar per day in a week, one per week in a month. */
    val bars: List<InsightBar>,
    val steps: List<StepStat>,
    /**
     * Why steps were skipped, commonest first. Empty when nobody said.
     *
     * The point of asking. "You skipped the walk four times" is a fact the user
     * can see on their own timeline; "three of those were because work came up"
     * is the one that suggests moving the walk rather than trying harder.
     */
    val skipReasons: List<SkipCount>,
    val suggestion: Suggestion?,
    val story: ReviewStory,
) {
    val adherence: Float get() = if (total == 0) 0f else kept.toFloat() / total

    val previousAdherence: Float? get() = previousTotal?.takeIf { it > 0 }?.let { (previousKept ?: 0).toFloat() / it }

    /** Percentage points against the previous period, when there is one. */
    val changeInPoints: Int? get() = previousAdherence?.let { ((adherence - it) * PERCENT).toInt() }

    private companion object {
        const val PERCENT = 100
    }
}

/**
 * One bar on the chart.
 *
 * [fraction] is null when nothing in that span was settled yet, which the chart
 * draws as empty rather than as zero. A day that has not happened is not a day
 * that went badly.
 */
/** One reason and how often it was given. */
data class SkipCount(val chip: SkipChip, val count: Int)

data class InsightBar(val start: LocalDate, val fraction: Float?, val isWeekend: Boolean)

/** One row of the step table. [slip] is the median lateness, when there is one. */
data class StepStat(
    val itemId: Long,
    val title: String,
    val kept: Int,
    val outOf: Int,
    val slip: Duration?,
) {
    val fraction: Float get() = if (outOf == 0) 0f else kept.toFloat() / outOf
}

/**
 * The one change worth making.
 *
 * Lifted straight from the weekly review's problem and its first suggested
 * answer. The screen turns the answer into a sentence and offers it once; the
 * user can apply it, or say not this week and not be asked again until next.
 */
data class Suggestion(
    val itemId: Long,
    val title: String,
    val answer: ReviewAnswer,
    val misses: Int,
    val outOf: Int,
    val slip: Duration?,
    /** The Monday of the week the suggestion belongs to. Keyed for dismissal. */
    val weekStart: LocalDate,
)
