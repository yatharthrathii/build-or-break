package com.buildorbreak.core.domain.goal

import com.buildorbreak.core.model.goal.Goal
import com.buildorbreak.core.model.goal.GoalProgress
import com.buildorbreak.core.model.goal.Reading
import java.time.LocalDate

/**
 * The four numbers every goal screen is built from. architecture.md section 5.1.
 *
 * One interface rather than four injected collaborators, because a caller that
 * wants the pace always wants the projection next to it, and a screen that had
 * to assemble them itself would be the place the two quietly disagreed.
 */
interface GoalCalculator {
    /** Seven day window. See [MovingAverage]. */
    fun smooth(readings: List<Reading>): List<Double>

    fun paceTarget(goal: Goal, on: LocalDate): Double

    fun project(goal: Goal, progress: List<GoalProgress>): Double

    fun percentComplete(goal: Goal, current: Double): Float
}

class DefaultGoalCalculator(
    private val average: MovingAverage = MovingAverage(),
    private val pace: PaceProjector = PaceProjector(),
) : GoalCalculator {

    override fun smooth(readings: List<Reading>): List<Double> = average.smooth(readings)

    override fun paceTarget(goal: Goal, on: LocalDate): Double = pace.paceTarget(goal, on)

    override fun project(goal: Goal, progress: List<GoalProgress>): Double = pace.project(goal, progress)

    override fun percentComplete(goal: Goal, current: Double): Float = pace.percentComplete(goal, current)
}
