package com.buildorbreak.core.domain.goal

import com.buildorbreak.core.model.enums.PointReason

/**
 * What everything costs, in one place.
 *
 * A day's routine is worth about a hundred and ten points, so these are
 * priced in days rather than in round numbers: a freeze is two days of work,
 * a third routine is five. That is the only scale that means anything, and
 * it is the reason to keep the numbers together where the ratios are visible.
 *
 * **Three things are deliberately free, and they stay free.**
 *
 * Undoing inside the undo bar, because charging somebody to correct a tap
 * they made four seconds ago is the app fining them for its own ambiguity.
 * Setting a first goal, because a new account has no points and a goal is
 * the reason most people open this app at all. Editing a goal, because a
 * typo in a target is not a feature request.
 *
 * What is charged for is extra, never repair: keeping a run across a day
 * that was genuinely missed, rewriting a settle long after the fact, and a
 * third day template when two cover a weekday and a weekend.
 */
object Prices {

    /** Two days of a kept routine. */
    const val STREAK_FREEZE = 200

    /** Rewriting a settle the undo bar has already let go of. */
    const val UNDO_STEP = 150

    /** Five days. A weekday and a weekend template come free. */
    const val EXTRA_ROUTINE = 500

    /** Every plan gets this many day templates without paying. */
    const val FREE_ROUTINES = 2

    /** Three good days. Less than a routine, because a goal ends and a routine does not. */
    const val SECOND_GOAL = 300

    /** One goal is part of the app. It is what the routine is for. */
    const val FREE_GOALS = 1

    /**
     * Two, and not more at any price.
     *
     * A goal is a promise about where the days are going, and somebody
     * chasing four of them at once is keeping none. The second is there for
     * the ordinary case of one for the body and one for the mind.
     */
    const val MAX_GOALS = 2

    /** What one rewarded ad is worth, once the SDK is wired up. */
    const val AD_REWARD = 100

    /**
     * How many ads a day can be turned into points.
     *
     * One. A habit app is opened for thirty seconds at a time, and a second
     * prompt in the same day is a nag rather than an offer. It also keeps the
     * ceiling from ads below what an ordinary day of the routine earns, so
     * the score stays a record of the routine and not of watching videos.
     * Raise it here if the impressions are ever worth more than that.
     */
    const val ADS_PER_DAY = 1

    fun of(reason: PointReason): Int = when (reason) {
        PointReason.STREAK_FREEZE -> STREAK_FREEZE
        PointReason.UNDO_STEP -> UNDO_STEP
        PointReason.EXTRA_ROUTINE -> EXTRA_ROUTINE
        PointReason.SECOND_GOAL -> SECOND_GOAL
        PointReason.AD_REWARD -> AD_REWARD
    }
}
