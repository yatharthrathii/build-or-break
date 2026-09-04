package com.buildorbreak.core.data.mapper

import com.buildorbreak.core.data.entity.DayCloseEntity
import com.buildorbreak.core.data.entity.GoalEntity
import com.buildorbreak.core.data.entity.GoalProgressEntity
import com.buildorbreak.core.data.entity.MilestoneAwardEntity
import com.buildorbreak.core.model.enums.DayQuality
import com.buildorbreak.core.model.enums.GoalKind
import com.buildorbreak.core.model.enums.Milestone
import com.buildorbreak.core.model.enums.ValueKind
import com.buildorbreak.core.model.goal.DayClose
import com.buildorbreak.core.model.goal.Goal
import com.buildorbreak.core.model.goal.GoalProgress
import com.buildorbreak.core.model.goal.MilestoneAward

internal fun GoalEntity.toModel(): Goal = Goal(
    id = id,
    planId = planId,
    kind = kind.toEnum(GoalKind.COUNT),
    title = title,
    itemId = itemId,
    valueKind = valueKind.toEnum(ValueKind.NONE),
    startValue = startValue,
    targetValue = targetValue,
    startDate = startDate,
    targetDate = targetDate,
    isActive = isActive,
)

internal fun Goal.toEntity(): GoalEntity = GoalEntity(
    id = id,
    planId = planId,
    kind = kind.name,
    title = title,
    itemId = itemId,
    valueKind = valueKind.name,
    startValue = startValue,
    targetValue = targetValue,
    startDate = startDate,
    targetDate = targetDate,
    isActive = isActive,
)

internal fun GoalProgressEntity.toModel(): GoalProgress = GoalProgress(
    goalId = goalId,
    date = date,
    rawValue = rawValue,
    smoothedValue = smoothedValue,
    cumulative = cumulative,
    paceTarget = paceTarget,
    projectedFinal = projectedFinal,
    counted = counted,
)

internal fun GoalProgress.toEntity(): GoalProgressEntity = GoalProgressEntity(
    goalId = goalId,
    date = date,
    rawValue = rawValue,
    smoothedValue = smoothedValue,
    cumulative = cumulative,
    paceTarget = paceTarget,
    projectedFinal = projectedFinal,
    counted = counted,
)

internal fun DayCloseEntity.toModel(): DayClose = DayClose(
    date = date,
    planId = planId,
    itemsDone = itemsDone,
    itemsMinimum = itemsMinimum,
    itemsMissed = itemsMissed,
    itemsTotal = itemsTotal,
    // An unreadable quality falls back to POOR, which suppresses praise, the
    // countdown and any milestone. Withholding a well earned congratulation is
    // recoverable; congratulating somebody on a day that went badly is not.
    quality = quality.toEnum(DayQuality.POOR),
    closedAt = closedAt,
)

internal fun DayClose.toEntity(): DayCloseEntity = DayCloseEntity(
    date = date,
    planId = planId,
    itemsDone = itemsDone,
    itemsMinimum = itemsMinimum,
    itemsMissed = itemsMissed,
    itemsTotal = itemsTotal,
    quality = quality.name,
    closedAt = closedAt,
)

/**
 * Null when the stored name is not a milestone this build knows.
 *
 * Dropping the row is right here. An award exists only to stop something firing
 * twice, and a name nothing can match cannot be suppressing anything.
 */
internal fun MilestoneAwardEntity.toModelOrNull(): MilestoneAward? {
    val known = Milestone.entries.firstOrNull { it.name == milestone } ?: return null

    return MilestoneAward(
        milestone = known,
        goalId = goalId,
        itemId = itemId,
        awardedOn = awardedOn,
        seenAt = seenAt,
    )
}

internal fun MilestoneAward.toEntity(): MilestoneAwardEntity = MilestoneAwardEntity(
    milestone = milestone.name,
    goalId = goalId,
    itemId = itemId,
    awardedOn = awardedOn,
    seenAt = seenAt,
)
