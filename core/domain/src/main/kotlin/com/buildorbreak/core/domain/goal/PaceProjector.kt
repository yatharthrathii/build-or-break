package com.buildorbreak.core.domain.goal

import com.buildorbreak.core.model.enums.GoalKind
import com.buildorbreak.core.model.goal.Goal
import com.buildorbreak.core.model.goal.GoalProgress
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Where the goal should be today, and where it is actually heading.
 *
 * Both numbers are deliberately simple. A straight line from start to target is
 * something a user can verify against their own arithmetic, and a projection
 * that is just the current rate carried forward is something they can argue
 * with. A cleverer model would be harder to trust and no more correct, because
 * eight weeks of body weight does not contain enough signal to justify one.
 */
class PaceProjector {

    /**
     * The straight line between the start and the target, sampled today.
     *
     * Works unchanged for a goal that goes down. `Goal.span` is negative for
     * losing five kilograms, so the line slopes the other way and every
     * comparison against it still reads the same direction.
     */
    fun paceTarget(goal: Goal, on: LocalDate): Double {
        // A rate has no line to climb. Ninety percent of days kept is the
        // bar on day one and the bar on the last day, so the pace line for a
        // consistency goal is the target itself, flat.
        if (goal.kind == GoalKind.CONSISTENCY) return goal.targetValue

        val fraction = goal.daysElapsed(on).toDouble() / goal.totalDays
        return goal.startValue + goal.span * fraction
    }

    /**
     * At this rate, what the value will be on the target date.
     *
     * Counting and accumulating goals project from what has been banked, because
     * twelve gym sessions is a total. Measured goals project from the smoothed
     * level, never the raw reading, for the reason in [MovingAverage].
     *
     * With nothing logged yet there is no rate to carry forward, so this returns
     * the starting value rather than inventing optimism.
     *
     * **A week that was left out changes the rate and nothing else.** The goal
     * still stands where it really stands: sessions done in a sick week were
     * done, and a weight that dropped did drop. What the week loses is its say
     * in how fast things are going. Its days come out of the divisor and its
     * change comes out of the distance covered, and the rate that is left is
     * carried forward from where the goal actually is. It used to work the
     * other way round, reading the goal as if it had stopped on the last
     * counted day, which took ten sessions back to six for a week of flu and
     * did nothing at all once a newer week existed.
     */
    fun project(goal: Goal, progress: List<GoalProgress>): Double {
        val rows = progress.sortedBy { it.date }
        val latest = rows.lastOrNull() ?: return goal.startValue
        val elapsed = goal.daysElapsed(latest.date)
        if (elapsed <= 0) return goal.startValue

        // A rate carried forward is the rate. Somebody keeping eighty percent
        // of their days ends the period at eighty percent unless something
        // changes, and a line drawn through the average as if it were a total
        // would promise four hundred percent by the end. The rate from before
        // a week that was left out is the one that says how they usually do.
        if (goal.kind == GoalKind.CONSISTENCY) return (rows.lastOrNull { it.counted } ?: latest).currentFor(goal)

        return carriedForward(goal, rows, elapsed)
    }

    /** Where the goal is, plus the rate of the days that count over the days that remain. */
    private fun carriedForward(goal: Goal, rows: List<GoalProgress>, elapsed: Int): Double {
        val current = rows.last().currentFor(goal)
        val leftOut = leftOut(goal, rows)
        val days = elapsed - leftOut.days

        // Everything so far was left out, so there is no rate. Flat from here.
        if (days <= 0) return current

        val rate = (current - goal.startValue - leftOut.change) / days
        return current + rate * (goal.totalDays - elapsed)
    }

    /** How many days the left out rows cover, and how far the goal moved across them. */
    private data class LeftOut(val days: Int, val change: Double)

    /**
     * Each left out row answers for the stretch since the row before it.
     *
     * Usually one day, because the close writes one row a night. Measured
     * by date rather than assumed, so a gap in the history cannot make a
     * week weigh less than it was.
     */
    private fun leftOut(goal: Goal, rows: List<GoalProgress>): LeftOut {
        var days = 0
        var change = 0.0

        rows.forEachIndexed { index, row ->
            if (row.counted) return@forEachIndexed

            val before = rows.getOrNull(index - 1)
            val since = before?.date ?: goal.startDate

            days += ChronoUnit.DAYS.between(since, row.date).toInt()
            change += row.currentFor(goal) - (before?.currentFor(goal) ?: goal.startValue)
        }

        return LeftOut(days, change)
    }

    /**
     * How far along the goal is, as zero to one.
     *
     * Clamped at both ends on purpose. Overshooting a target is worth
     * celebrating but it is not a hundred and forty percent of a progress bar,
     * and sliding backwards past the start should read as nothing done rather
     * than as a negative bar that the UI has to special case.
     */
    fun percentComplete(goal: Goal, current: Double, on: LocalDate): Float {
        // A rate is banked with time. Being at the rate on day one of fifty
        // six is not a goal nearly reached, it is a good first day; what has
        // been banked is the share of the period kept at the rate so far.
        // Reached, therefore, only when the period is over and the rate held.
        if (goal.kind == GoalKind.CONSISTENCY) {
            val held = if (goal.targetValue > 0.0) (current / goal.targetValue).coerceIn(0.0, 1.0) else 1.0
            val elapsed = goal.daysElapsed(on).toDouble() / goal.totalDays

            return (held * elapsed).toFloat().coerceIn(0f, 1f)
        }

        if (goal.span == 0.0) {
            val reached = if (goal.isIncreasing) current >= goal.targetValue else current <= goal.targetValue
            return if (reached) 1f else 0f
        }

        return ((current - goal.startValue) / goal.span).toFloat().coerceIn(0f, 1f)
    }
}
