package com.buildorbreak.core.model.goal

import com.buildorbreak.core.model.enums.DayQuality
import com.buildorbreak.core.model.enums.GoalKind
import com.buildorbreak.core.model.enums.Milestone
import com.buildorbreak.core.model.enums.ValueKind
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * One target attached to a plan.
 *
 * Four kinds cover every build habit, which is what stops this app from being a
 * weight gain app with extra steps. Gaining two kilograms, doing twelve gym
 * sessions, accumulating forty hours of study and taking medicine on ninety five
 * percent of days all run through the same engine.
 */
data class Goal(
    val id: Long,
    val planId: Long,
    val kind: GoalKind,
    val title: String,
    /** COUNT and DURATION goals track a single item. */
    val itemId: Long?,
    /** NUMBER goals track a single measurement series. */
    val valueKind: ValueKind,
    val startValue: Double,
    val targetValue: Double,
    val startDate: LocalDate,
    val targetDate: LocalDate,
    val isActive: Boolean,
) {
    val totalDays: Int
        get() = ChronoUnit.DAYS.between(startDate, targetDate).toInt().coerceAtLeast(1)

    val span: Double get() = targetValue - startValue

    val isIncreasing: Boolean get() = span >= 0

    fun daysElapsed(on: LocalDate): Int = ChronoUnit.DAYS.between(startDate, on).toInt().coerceIn(0, totalDays)

    fun daysLeft(on: LocalDate): Int = ChronoUnit.DAYS.between(on, targetDate).toInt().coerceAtLeast(0)
}

/** A single reading in a measurement series, before smoothing. */
data class Reading(val date: LocalDate, val value: Double)

/**
 * One row per day per goal, written by the daily close.
 *
 * Precomputing this is what lets a month view render without recomputing a
 * month of history on every scroll.
 */
data class GoalProgress(
    val goalId: Long,
    val date: LocalDate,
    /** Today's reading. Null when nothing was logged. */
    val rawValue: Double?,
    /**
     * Seven day moving average. This is the honest number.
     *
     * Body weight swings by up to a kilogram a day from water and food. A raw
     * reading must never drive a suggestion. See techspec.md section 5b.
     */
    val smoothedValue: Double?,
    /** COUNT and DURATION goals accumulate here. */
    val cumulative: Double,
    /** Where a straight line from start to target sits today. */
    val paceTarget: Double,
    /** At this rate, what the value will be on the target date. */
    val projectedFinal: Double,
    /** False when the user marked the week as not counting. */
    val counted: Boolean = true,
)

/** How a finished day went. Written once, at the daily close. */
data class DayClose(
    val date: LocalDate,
    val planId: Long,
    val itemsDone: Int,
    val itemsMinimum: Int,
    val itemsMissed: Int,
    val itemsTotal: Int,
    val quality: DayQuality,
    val closedAt: Instant,
) {
    /** A minimum version counts. Scaling down is not failing. */
    val adherence: Float
        get() = if (itemsTotal == 0) 1f else (itemsDone + itemsMinimum).toFloat() / itemsTotal

    val isFullDay: Boolean get() = itemsTotal > 0 && itemsDone == itemsTotal
}

/**
 * Proof that a milestone has already fired.
 *
 * The existence of a row is the whole anti repeat mechanism. There is no
 * counter, no flag and no date arithmetic to get wrong.
 */
data class MilestoneAward(
    val milestone: Milestone,
    val goalId: Long?,
    val itemId: Long?,
    val awardedOn: LocalDate,
    val seenAt: Instant?,
)
