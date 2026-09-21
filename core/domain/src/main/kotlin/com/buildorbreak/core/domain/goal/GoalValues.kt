package com.buildorbreak.core.domain.goal

import com.buildorbreak.core.model.enums.GoalKind
import com.buildorbreak.core.model.goal.Goal
import com.buildorbreak.core.model.goal.GoalProgress

/**
 * Where a goal stands, read off one row of its history.
 *
 * The four kinds keep their answer in different columns, and every screen and
 * every calculation has to pick the same one or two numbers in the app will
 * quietly disagree. Defined once, here, rather than as four `when` blocks that
 * drift apart.
 *
 * A measured goal prefers the smoothed level and falls back to the raw reading
 * only while there is too little history to smooth. Nothing recorded at all
 * reads as the starting value rather than as zero: zero kilograms is a
 * different claim from no weigh in yet.
 */
fun GoalProgress.currentFor(goal: Goal): Double = when (goal.kind) {
    GoalKind.COUNT, GoalKind.DURATION -> cumulative
    GoalKind.NUMBER, GoalKind.CONSISTENCY -> smoothedValue ?: rawValue ?: goal.startValue
}

/**
 * The same question asked of a whole history. The newest row wins.
 *
 * Whether or not its week counts. Leaving a week out is a statement about
 * the rate, and where the goal stands is not a rate: what was done in that
 * week was done.
 */
fun List<GoalProgress>.currentFor(goal: Goal): Double = maxByOrNull { it.date }?.currentFor(goal) ?: goal.startValue
