package com.buildorbreak.core.testing.fixtures

import com.buildorbreak.core.model.enums.DayQuality
import com.buildorbreak.core.model.enums.GoalKind
import com.buildorbreak.core.model.enums.ValueKind
import com.buildorbreak.core.model.goal.DayClose
import com.buildorbreak.core.model.goal.Goal
import com.buildorbreak.core.model.goal.GoalProgress
import com.buildorbreak.core.model.goal.Reading
import java.time.Instant
import java.time.LocalDate

/**
 * Builders for goal types.
 *
 * The default goal deliberately runs from zero to ten over ten days, so pace and
 * projection assertions are arithmetic a reader can check in their head. A test
 * that needs real body weight numbers passes them in and says why.
 */
object GoalFixtures {

    val START: LocalDate = LocalDate.of(2026, 1, 1)

    /** Ten days after [START], so `Goal.totalDays` is exactly ten. */
    val TARGET: LocalDate = LocalDate.of(2026, 1, 11)

    const val GOAL_ID = 1L

    fun goal(
        id: Long = GOAL_ID,
        planId: Long = PlanFixtures.PLAN_ID,
        kind: GoalKind = GoalKind.NUMBER,
        title: String = "Goal $id",
        itemId: Long? = null,
        valueKind: ValueKind = ValueKind.WEIGHT_KG,
        startValue: Double = 0.0,
        targetValue: Double = 10.0,
        startDate: LocalDate = START,
        targetDate: LocalDate = TARGET,
        isActive: Boolean = true,
    ): Goal = Goal(
        id = id,
        planId = planId,
        kind = kind,
        title = title,
        itemId = itemId,
        valueKind = valueKind,
        startValue = startValue,
        targetValue = targetValue,
        startDate = startDate,
        targetDate = targetDate,
        isActive = isActive,
    )

    fun progress(
        date: LocalDate,
        goalId: Long = GOAL_ID,
        rawValue: Double? = null,
        smoothedValue: Double? = null,
        cumulative: Double = 0.0,
        paceTarget: Double = 0.0,
        projectedFinal: Double = 0.0,
        counted: Boolean = true,
    ): GoalProgress = GoalProgress(
        goalId = goalId,
        date = date,
        rawValue = rawValue,
        smoothedValue = smoothedValue,
        cumulative = cumulative,
        paceTarget = paceTarget,
        projectedFinal = projectedFinal,
        counted = counted,
    )

    /**
     * A finished day. [quality] is derived from the counts by default, so a test
     * that says "eight of ten" does not also have to say what that means.
     */
    fun close(
        date: LocalDate,
        itemsDone: Int = 10,
        itemsMinimum: Int = 0,
        itemsMissed: Int = 0,
        itemsTotal: Int = itemsDone + itemsMinimum + itemsMissed,
        quality: DayQuality = qualityOf(itemsDone, itemsMinimum, itemsTotal),
        planId: Long = PlanFixtures.PLAN_ID,
        closedAt: Instant = Instant.EPOCH,
    ): DayClose = DayClose(
        date = date,
        planId = planId,
        itemsDone = itemsDone,
        itemsMinimum = itemsMinimum,
        itemsMissed = itemsMissed,
        itemsTotal = itemsTotal,
        quality = quality,
        closedAt = closedAt,
    )

    /** Consecutive closed days from [from], each with the given adherence counts. */
    fun closes(
        from: LocalDate,
        days: Int,
        itemsDone: Int = 10,
        itemsTotal: Int = 10,
    ): List<DayClose> = (0 until days).map {
        close(
            date = from.plusDays(it.toLong()),
            itemsDone = itemsDone,
            itemsMissed = itemsTotal - itemsDone,
            itemsTotal = itemsTotal,
        )
    }

    /**
     * Mirrors `DayQualityClassifier`, which this module cannot depend on: the
     * domain depends on this one for its own tests, so the arrow only points one
     * way. Kept next to the thresholds it copies so a change to either is
     * obvious in review.
     */
    private const val GOOD_DAY = 0.8
    private const val OK_DAY = 0.5

    private fun qualityOf(done: Int, minimum: Int, total: Int): DayQuality {
        if (total <= 0) return DayQuality.GOOD
        val adherence = (done + minimum).toDouble() / total
        return when {
            adherence >= GOOD_DAY -> DayQuality.GOOD
            adherence >= OK_DAY -> DayQuality.OK
            else -> DayQuality.POOR
        }
    }

    /** Readings on consecutive days from [START], in the order given. */
    fun consecutive(vararg values: Double): List<Reading> =
        values.mapIndexed { index, value -> Reading(START.plusDays(index.toLong()), value) }
}
