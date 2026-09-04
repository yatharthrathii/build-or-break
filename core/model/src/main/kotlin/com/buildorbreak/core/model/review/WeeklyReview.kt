package com.buildorbreak.core.model.review

import com.buildorbreak.core.model.enums.ReviewStory
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * One week, told as one story.
 *
 * Deliberately not a wall of statistics. A report that lists everything is read
 * once and skipped forever, so this carries at most one thing that went well,
 * at most one thing that did not, and one question. Everything else the user
 * can already see on the calendar.
 *
 * [story] is the shape of the week. The report has no other form, which is why
 * no model is needed to write it: each story maps to one prewritten string with
 * number slots.
 */
data class WeeklyReview(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val story: ReviewStory,
    val adherence: Float,
    /** Last week, when there was one. This is what makes a trend visible. */
    val previousAdherence: Float?,
    val win: ReviewWin?,
    val problem: ReviewProblem?,
    val question: ReviewQuestion?,
) {
    val isImproving: Boolean get() = previousAdherence?.let { adherence > it } ?: false
}

/**
 * The one thing worth pointing at.
 *
 * Named rather than aggregated on purpose. "You kept the evening walk every day
 * this week" lands. "Adherence 86 percent" does not.
 */
data class ReviewWin(val itemId: Long, val title: String, val done: Int, val outOf: Int)

/** The one thing worth fixing, and whether it clusters on a particular day. */
data class ReviewProblem(
    val itemId: Long,
    val title: String,
    val misses: Int,
    val outOf: Int,
    val weekday: DayOfWeek? = null,
)

/**
 * What the app asks about [ReviewProblem], and what it will accept as an answer.
 *
 * This is what turns a report into a decision. A review nobody can act on is a
 * review nobody opens twice, and the act of choosing is most of the value: a
 * plan somebody chose is a plan they keep.
 */
data class ReviewQuestion(val itemId: Long, val options: List<ReviewAnswer>)

/**
 * Every answer is a real option, including the ones most apps never offer.
 *
 * `LEAVE_IT` and `REMOVE_ITEM` matter more than they look. Without an honest way
 * out, somebody who has decided they are not going to do a thing will pick
 * whichever answer ends the conversation, the same question will return next
 * week, and the report stops being trusted. A routine that can be edited down is
 * a routine that survives.
 */
enum class ReviewAnswer {
    /** Move it to when it actually happens. */
    MOVE_TIME,

    /** Give it a range instead of a minute, so an ordinary day cannot break it. */
    WIDEN_WINDOW,

    /** Make the reminder louder. For things that are simply forgotten. */
    RAISE_SALIENCE,

    /** Run the smaller version by default. For things that are too big as set. */
    USE_MINIMUM,

    /** Nothing is wrong with the plan. The user intends to keep it as it is. */
    KEEP_AND_FOCUS,

    /** It is fine as it is, and the app should stop asking. */
    LEAVE_IT,

    /** It is not happening. Taking it off the plan is a valid outcome. */
    REMOVE_ITEM,
}
