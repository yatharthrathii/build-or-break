package com.buildorbreak.core.domain.review

import com.buildorbreak.core.model.enums.ReviewStory
import com.buildorbreak.core.model.execution.Occurrence
import com.buildorbreak.core.model.execution.SkipReason
import com.buildorbreak.core.model.goal.DayClose
import com.buildorbreak.core.model.plan.Item
import com.buildorbreak.core.model.review.ReviewAnswer
import com.buildorbreak.core.model.review.ReviewProblem
import com.buildorbreak.core.model.review.ReviewQuestion
import com.buildorbreak.core.model.review.ReviewWin
import com.buildorbreak.core.model.review.WeeklyReview
import java.time.LocalDate

/** Matches `DayQuality.GOOD`. One threshold for a good day and a good week. */
private const val HIGH_ADHERENCE = 0.8f

/** A drop this large is a trend. Anything smaller is an ordinary week. */
private const val SLIPPING_BY = 0.15f

/** Below this the goal is not keeping up with the plan that is being kept. */
private const val ON_PACE_TOLERANCE = 0.9f

private const val DAYS_IN_WEEK = 7

/**
 * Everything one week needs to be told as a story.
 *
 * A snapshot, like `ResolveInput`. The builder cannot read anything it was not
 * handed, so the same week always produces the same review and a report that
 * looked wrong can be reproduced exactly from its inputs.
 */
data class ReviewInput(
    val weekStart: LocalDate,
    val closes: List<DayClose>,
    /** This week only. The win is drawn from here. */
    val occurrences: List<Occurrence>,
    val items: List<Item>,
    val previousCloses: List<DayClose> = emptyList(),
    /**
     * The trailing window the pattern detector reads, several weeks wide.
     *
     * Separate from [occurrences] because a weekday cluster cannot exist inside
     * a single week: one week contains exactly one Monday, and two misses on the
     * same weekday is the whole definition of the pattern. Handing the detector
     * seven days would quietly guarantee it never finds the thing it exists to
     * find. Defaults to this week so a first review still works.
     */
    val recentOccurrences: List<Occurrence> = occurrences,
    val reasons: List<SkipReason> = emptyList(),
    /** Zero to one, or null when no goal is active. */
    val goalPercent: Float? = null,
    /** Where a straight line says the goal should be by now. */
    val goalPaceFraction: Float? = null,
)

/**
 * One week, one story, one question.
 *
 * The restraint is the design. Three decisions are baked in, and each is the
 * opposite of what a dashboard would do:
 *
 * 1. **One win and one problem, not a list.** A wall of numbers is skipped, and
 *    a report nobody reads changes nothing.
 * 2. **The question always has a way out.** `LEAVE_IT` and `REMOVE_ITEM` are
 *    offered every time, because a question with no honest answer gets a
 *    dishonest one and the same problem returns next week unchanged.
 * 3. **The suggestion depends on why, not on how many.** Three misses because
 *    work ran over and three misses because the step is too big are different
 *    problems. Offering one fix for both is wrong half the time.
 */
interface WeeklyReviewBuilder {
    fun build(input: ReviewInput): WeeklyReview
}

class DefaultWeeklyReviewBuilder(
    private val skips: SkipPatternDetector = SkipPatternDetector(),
) : WeeklyReviewBuilder {

    /**
     * A problem and the reason behind it, kept together while the review is
     * assembled.
     *
     * [SkipCause] is a domain heuristic and deliberately does not travel on the
     * model type, so the database and the export format never inherit a
     * classification that is going to be tuned. Carrying it in a local pair for
     * the length of one call is all it needs.
     */
    private data class Diagnosis(val problem: ReviewProblem, val cause: SkipCause)

    override fun build(input: ReviewInput): WeeklyReview {
        val adherence = adherenceOf(input.closes)
        val previous = input.previousCloses.takeIf { it.isNotEmpty() }?.let(::adherenceOf)

        val titles = input.items.associate { it.id to it.title }
        val diagnosis = worstProblem(input, titles)

        return WeeklyReview(
            weekStart = input.weekStart,
            weekEnd = input.weekStart.plusDays(DAYS_IN_WEEK - 1L),
            story = storyOf(input, adherence, previous, diagnosis),
            adherence = adherence,
            previousAdherence = previous,
            win = bestWin(input, titles),
            problem = diagnosis?.problem,
            question = diagnosis?.let { questionFor(it, input) },
        )
    }

    // The story ---------------------------------------------------------------

    /**
     * Order matters more than any single threshold here.
     *
     * A first week is always `SETTLING_IN`, whatever the numbers say, because a
     * conclusion drawn from four days is not a conclusion. After that, a week
     * that went well is reported as going well even if something is still being
     * missed: telling somebody who kept eighty five percent of their plan that
     * they have a problem is how a report loses its reader for good.
     */
    private fun storyOf(
        input: ReviewInput,
        adherence: Float,
        previous: Float?,
        diagnosis: Diagnosis?,
    ): ReviewStory {
        if (previous == null || input.closes.size < DAYS_IN_WEEK) return ReviewStory.SETTLING_IN

        return when {
            // Doing the work and the goal is still not moving. The plan is the
            // problem, not the person, and saying so is the point of this story.
            adherence >= HIGH_ADHERENCE && isBehindPace(input) -> ReviewStory.PLAN_TOO_SMALL
            adherence >= HIGH_ADHERENCE -> ReviewStory.ON_TRACK

            adherence < previous - SLIPPING_BY -> ReviewStory.LOSING_GRIP

            diagnosis == null -> ReviewStory.MIXED

            diagnosis.problem.weekday != null || diagnosis.cause == SkipCause.TIMING -> ReviewStory.TIMING_PROBLEM
            diagnosis.cause == SkipCause.REMINDER -> ReviewStory.REMINDER_PROBLEM

            else -> ReviewStory.MIXED
        }
    }

    private fun isBehindPace(input: ReviewInput): Boolean {
        val percent = input.goalPercent ?: return false
        val pace = input.goalPaceFraction ?: return false

        return pace > 0f && percent / pace < ON_PACE_TOLERANCE
    }

    // The win and the problem -------------------------------------------------

    /**
     * The item kept most reliably, named.
     *
     * Ties break towards the item that had the most chances, because keeping
     * something seven times out of seven says more than twice out of twice. And
     * it is named rather than aggregated on purpose: "you kept the evening walk
     * every day this week" lands, "adherence 86 percent" does not.
     */
    private fun bestWin(input: ReviewInput, titles: Map<Long, String>): ReviewWin? =
        input.occurrences.filter { it.isSettled }
            .groupBy { it.itemId }
            .mapNotNull { (itemId, forItem) ->
                val done = forItem.count { it.isDone }
                val title = titles[itemId]
                if (done == 0 || title == null) null else ReviewWin(itemId, title, done, forItem.size)
            }
            .maxWithOrNull(compareBy({ it.done.toFloat() / it.outOf }, { it.outOf }))

    private fun worstProblem(input: ReviewInput, titles: Map<Long, String>): Diagnosis? =
        skips.detect(input.recentOccurrences, input.reasons)
            .firstNotNullOfOrNull { pattern ->
                titles[pattern.itemId]?.let { title ->
                    Diagnosis(
                        problem = ReviewProblem(
                            itemId = pattern.itemId,
                            title = title,
                            misses = pattern.misses,
                            outOf = pattern.opportunities,
                            weekday = pattern.weekday,
                        ),
                        cause = pattern.cause,
                    )
                }
            }

    // The question ------------------------------------------------------------

    /**
     * The fix offered first is the one the reason implies, and the way out is
     * always on the list.
     *
     * A clustered weekday gets `WIDEN_WINDOW` ahead of `MOVE_TIME`, which is the
     * one suggestion here worth arguing about. Moving a fixed time relocates the
     * problem to a different minute. Giving the item a range means an ordinary
     * day can no longer break it at all, and that capability is the reason this
     * app exists rather than another checklist.
     */
    private fun questionFor(diagnosis: Diagnosis, input: ReviewInput): ReviewQuestion {
        val item = input.items.firstOrNull { it.id == diagnosis.problem.itemId }

        val suggested = when (diagnosis.cause) {
            SkipCause.REMINDER -> listOf(ReviewAnswer.RAISE_SALIENCE, ReviewAnswer.MOVE_TIME)

            SkipCause.MOTIVATION ->
                if (item?.hasMinimum == true) listOf(ReviewAnswer.USE_MINIMUM) else listOf(ReviewAnswer.WIDEN_WINDOW)

            SkipCause.TIMING, SkipCause.UNKNOWN -> listOf(ReviewAnswer.WIDEN_WINDOW, ReviewAnswer.MOVE_TIME)
        }

        return ReviewQuestion(
            itemId = diagnosis.problem.itemId,
            options = suggested + listOf(ReviewAnswer.KEEP_AND_FOCUS, ReviewAnswer.LEAVE_IT, ReviewAnswer.REMOVE_ITEM),
        )
    }

    private fun adherenceOf(closes: List<DayClose>): Float =
        if (closes.isEmpty()) 0f else closes.map { it.adherence }.average().toFloat()
}
