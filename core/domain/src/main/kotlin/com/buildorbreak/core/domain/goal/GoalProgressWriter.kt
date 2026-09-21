package com.buildorbreak.core.domain.goal

import com.buildorbreak.core.model.enums.GoalKind
import com.buildorbreak.core.model.enums.ValueKind
import com.buildorbreak.core.model.execution.Measurement
import com.buildorbreak.core.model.execution.Occurrence
import com.buildorbreak.core.model.goal.DayClose
import com.buildorbreak.core.model.goal.Goal
import com.buildorbreak.core.model.goal.GoalProgress
import com.buildorbreak.core.model.goal.Reading
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** A consistency goal is written as a percentage, so its target reads as one too. */
private const val PERCENT = 100.0

/**
 * Everything one day of one goal is worked out from.
 *
 * A snapshot, like `ResolveInput`. The writer cannot read anything it was not
 * handed, so the same day always produces the same row and a projection that
 * looked wrong can be reproduced from its inputs alone.
 */
data class GoalDay(
    val goal: Goal,
    val date: LocalDate,
    /** Every reading of the goal's series up to and including [date], oldest first. */
    val readings: List<Reading>,
    /** Every close from the goal's start date to [date]. */
    val closes: List<DayClose>,
    /** The occurrences of the tracked item on [date]. */
    val occurrences: List<Occurrence>,
    /** What was logged against the tracked item on [date]. */
    val measurements: List<Measurement>,
    /** How long the tracked item is planned to take. Zero when it says nothing. */
    val plannedMinutes: Int = 0,
    /** Yesterday's row, which is where a running total comes from. */
    val previous: GoalProgress?,
    /** The Mondays of the weeks the user left out of this goal. */
    val leftOutWeeks: Set<LocalDate> = emptySet(),
)

/**
 * Turns one finished day into one row of goal history.
 *
 * The four goal kinds accumulate completely differently and the difference is
 * the whole reason `GoalKind` exists: a weight goal is a level that is measured,
 * a count goal is a tally, a duration goal is a sum of minutes and a consistency
 * goal is a rate. Working that out on every read would put the same four cases
 * in every screen that shows a goal, and the first time one of them drifted
 * nobody would be able to say which number was right.
 *
 * **The row is written once, at the daily close, and never recomputed.** That is
 * what lets a month of history render without a month of arithmetic, and it is
 * why the running total is read from yesterday's row rather than resummed: a
 * tally that is rebuilt from scratch every night is a tally that changes when
 * an old day is edited, which is not what a total of what you actually did
 * should do.
 */
class GoalProgressWriter(private val calculator: GoalCalculator = DefaultGoalCalculator()) {

    fun rowFor(day: GoalDay): GoalProgress {
        val goal = day.goal
        val raw = rawValueFor(day)
        val cumulative = cumulativeFor(day, raw)
        val smoothed = smoothedFor(day)

        val row = GoalProgress(
            goalId = goal.id,
            date = day.date,
            rawValue = raw,
            smoothedValue = smoothed,
            cumulative = cumulative,
            paceTarget = calculator.paceTarget(goal, day.date),
            // Filled in below, once the row it depends on exists. A projection
            // is a function of the history including today, and today is this.
            projectedFinal = 0.0,
            // Asked of the week, not carried from yesterday. Carrying it meant
            // a week could only be left out once it had a day in it, and the
            // first day of a week, having no yesterday in the same week, came
            // back as counting every time it was written again.
            counted = day.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) !in day.leftOutWeeks,
        )

        return row.copy(projectedFinal = calculator.project(goal, listOf(row)))
    }

    /**
     * What happened today, in whatever unit the goal counts in.
     *
     * Null only for a measured goal on a day nobody logged anything, which is
     * an ordinary day and not a zero: recording a missing weigh in as zero
     * kilograms would drag the smoothed line through the floor.
     */
    private fun rawValueFor(day: GoalDay): Double? = when (day.goal.kind) {
        GoalKind.NUMBER -> day.readings.lastOrNull { it.date == day.date }?.value

        GoalKind.COUNT -> day.occurrences.count { it.isDone }.toDouble()

        GoalKind.DURATION -> minutesOn(day)

        GoalKind.CONSISTENCY -> day.closes.lastOrNull { it.date == day.date }?.let { it.adherence * PERCENT }
    }

    /**
     * Minutes actually spent, preferring what was logged over what was planned.
     *
     * A step marked done with no number against it still counts for its planned
     * duration. Requiring a number would mean a forty hour study goal only
     * advanced on the days somebody remembered to type one in, which is the
     * kind of silent zero that makes a progress bar untrustworthy.
     */
    private fun minutesOn(day: GoalDay): Double {
        // Minutes only. A step that asks for pages can still carry a duration
        // goal, and thirty pages logged against it are not thirty minutes.
        val logged = day.measurements.filter { it.date == day.date && it.kind == ValueKind.MINUTES }.sumOf { it.value }
        if (logged > 0.0) return logged

        return day.occurrences.count { it.isDone } * day.plannedMinutes.toDouble()
    }

    /**
     * The running total, carried forward.
     *
     * Measured goals do not accumulate: a body weight is a level, not a sum,
     * and adding today's reading to yesterday's would produce a number with no
     * meaning at all. They keep the previous total so the column is never null.
     */
    private fun cumulativeFor(day: GoalDay, raw: Double?): Double {
        val carried = day.previous?.cumulative ?: 0.0

        return when (day.goal.kind) {
            GoalKind.COUNT, GoalKind.DURATION -> carried + (raw ?: 0.0)
            GoalKind.NUMBER -> carried
            GoalKind.CONSISTENCY -> carried + if ((raw ?: 0.0) > 0.0) 1.0 else 0.0
        }
    }

    /**
     * The honest number, for the two kinds that have one.
     *
     * Body weight swings by up to a kilogram a day from water and food, so a
     * raw reading must never drive a suggestion; techspec.md section 5b. A
     * consistency goal is smoothed over the whole period instead, because the
     * question it answers is "how often, overall", not "how was today".
     */
    private fun smoothedFor(day: GoalDay): Double? = when (day.goal.kind) {
        GoalKind.NUMBER -> calculator.smooth(day.readings).lastOrNull()

        GoalKind.CONSISTENCY ->
            day.closes
                .filter { it.date >= day.goal.startDate && it.date <= day.date }
                .takeIf { it.isNotEmpty() }
                ?.map { it.adherence.toDouble() }
                ?.average()
                ?.times(PERCENT)

        GoalKind.COUNT, GoalKind.DURATION -> null
    }
}
