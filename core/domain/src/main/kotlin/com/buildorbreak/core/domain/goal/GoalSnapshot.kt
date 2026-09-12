package com.buildorbreak.core.domain.goal

import com.buildorbreak.core.model.goal.Goal
import java.time.LocalDate

/** Within this fraction of the pace line, the goal is neither ahead nor behind. */
private const val ON_PACE_BAND = 0.05

/**
 * Where a goal actually is, worked out once.
 *
 * Every number a goal screen shows, so no screen has to know the difference
 * between a level, a tally and a rate. [current] is already in the goal's own
 * unit: kilograms for a measured goal, sessions for a count, minutes for a
 * duration, percent for a consistency goal.
 */
data class GoalSnapshot(
    val goal: Goal,
    /** Where the goal is now, smoothed where smoothing applies. */
    val current: Double,
    /** Where a straight line from start to target says it should be today. */
    val paceTarget: Double,
    /** At this rate, where it lands on the target date. */
    val projected: Double,
    /** Zero to one, clamped at both ends. */
    val percent: Float,
    val daysLeft: Int,
    /**
     * The recent line, oldest first, for the sparkline.
     *
     * Only the values that exist. A measured goal with three readings in two
     * weeks draws three points, not fourteen with eleven zeroes in them.
     */
    val trail: List<Double>,
    /** Whether anything has been recorded against this goal yet, today included. */
    val hasData: Boolean,
    /**
     * Whether there is a rate to carry forward at all.
     *
     * Separate from [hasData], and stricter. A projection divides what has
     * been banked by the days it took, so it needs a banked day with time
     * behind it. On the morning a goal is set there is a completion and no
     * elapsed time, and the honest answer to "at this rate" is that there is
     * no rate yet, not that the goal will be missed.
     */
    val hasProjection: Boolean,
    val on: LocalDate,
    /**
     * What was typed today, unsmoothed. Measured goals only.
     *
     * Shown beside [current], never instead of it. Somebody who weighed 50.5
     * this morning and reads 49.8 on the card will think the app is wrong
     * unless the card also says what it saw today and why the two differ.
     */
    val todayReading: Double? = null,
) {
    /** How far [current] has come from where the goal began. */
    val changeSinceStart: Double get() = current - goal.startValue

    val totalDays: Int get() = goal.totalDays

    val daysElapsed: Int get() = goal.daysElapsed(on)

    /** Where the pace line sits, as zero to one, for drawing the marker. */
    val paceFraction: Float get() = (daysElapsed.toFloat() / totalDays).coerceIn(0f, 1f)

    /**
     * Ahead, behind, or neither.
     *
     * Compared against the pace line rather than against the target, because
     * being at forty percent of a goal means nothing without knowing whether
     * forty percent of the time has gone. Null until something is recorded: a
     * goal set this morning is not behind.
     */
    val standing: GoalStanding
        get() = when {
            percent >= COMPLETE -> GoalStanding.REACHED
            // Over before it was reached. "Behind, with no days left" is the
            // arithmetic; this is the sentence.
            daysElapsed >= totalDays -> GoalStanding.OVER
            !hasData -> GoalStanding.UNKNOWN
            paceFraction <= 0f -> GoalStanding.UNKNOWN
            percent > paceFraction + ON_PACE_BAND -> GoalStanding.AHEAD
            percent < paceFraction - ON_PACE_BAND -> GoalStanding.BEHIND
            else -> GoalStanding.ON_PACE
        }

    /**
     * Whether this goal is over, one way or the other.
     *
     * Reached counts as over even with days to spare: a goal that has been
     * met is finished, and a screen that keeps showing days left over a full
     * bar is asking somebody to keep working at something they have already
     * done. The screen says which of the two it was; this only says that it
     * is time to ask about the next one.
     */
    val isFinished: Boolean
        get() = standing == GoalStanding.REACHED || standing == GoalStanding.OVER

    /** Whether the current rate gets there in time. */
    val willReach: Boolean
        get() = if (goal.isIncreasing) projected >= goal.targetValue else projected <= goal.targetValue

    private companion object {
        const val COMPLETE = 1f
    }
}

/** How a goal is doing against its own pace line. The screen turns this into words. */
enum class GoalStanding {
    AHEAD,
    ON_PACE,
    BEHIND,
    REACHED,

    /** The target date has passed and the target was not reached. */
    OVER,

    /** Nothing recorded yet, or the goal starts today. Not a verdict. */
    UNKNOWN,
}
