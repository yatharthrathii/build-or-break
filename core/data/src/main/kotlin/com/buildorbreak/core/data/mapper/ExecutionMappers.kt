package com.buildorbreak.core.data.mapper

import com.buildorbreak.core.data.entity.DayLogEntity
import com.buildorbreak.core.data.entity.MeasurementEntity
import com.buildorbreak.core.data.entity.OccurrenceEntity
import com.buildorbreak.core.data.entity.SkipReasonEntity
import com.buildorbreak.core.model.enums.DayMode
import com.buildorbreak.core.model.enums.OccurrenceState
import com.buildorbreak.core.model.enums.SkipChip
import com.buildorbreak.core.model.enums.ValueKind
import com.buildorbreak.core.model.execution.DayLog
import com.buildorbreak.core.model.execution.Measurement
import com.buildorbreak.core.model.execution.Occurrence
import com.buildorbreak.core.model.execution.SkipReason
import com.buildorbreak.core.model.goal.Reading

internal fun OccurrenceEntity.toModel(): Occurrence = Occurrence(
    id = id,
    itemId = itemId,
    date = date,
    plannedAt = plannedAt,
    scheduledAt = scheduledAt,
    firedAt = firedAt,
    settledAt = settledAt,
    // An unreadable state falls back to PENDING rather than to DONE. Counting
    // something as done when it may not have happened would quietly inflate
    // every adherence figure built on top of it, and adherence is what the
    // weekly review and the goal projection are made of.
    state = state.toEnum(OccurrenceState.PENDING),
    shiftMinutes = shiftMinutes,
    snoozeCount = snoozeCount,
    sequenceInDay = sequenceInDay,
)

internal fun Occurrence.toEntity(): OccurrenceEntity = OccurrenceEntity(
    id = id,
    itemId = itemId,
    date = date,
    plannedAt = plannedAt,
    scheduledAt = scheduledAt,
    firedAt = firedAt,
    settledAt = settledAt,
    state = state.name,
    shiftMinutes = shiftMinutes,
    snoozeCount = snoozeCount,
    sequenceInDay = sequenceInDay,
)

internal fun SkipReasonEntity.toModel(): SkipReason = SkipReason(
    id = id,
    occurrenceId = occurrenceId,
    // No chip is a real and common answer, so an unreadable one reads as none
    // rather than as OTHER. The detector already works without a reason.
    chip = chip?.let { stored -> SkipChip.entries.firstOrNull { it.name == stored } },
    text = text,
    createdAt = createdAt,
)

internal fun SkipReason.toEntity(): SkipReasonEntity = SkipReasonEntity(
    id = id,
    occurrenceId = occurrenceId,
    chip = chip?.name,
    text = text,
    createdAt = createdAt,
)

internal fun MeasurementEntity.toModel(): Measurement = Measurement(
    id = id,
    itemId = itemId,
    occurrenceId = occurrenceId,
    date = date,
    value = value,
    kind = kind.toEnum(ValueKind.NONE),
    note = note,
)

internal fun Measurement.toEntity(): MeasurementEntity = MeasurementEntity(
    id = id,
    itemId = itemId,
    occurrenceId = occurrenceId,
    date = date,
    value = value,
    kind = kind.name,
    note = note,
)

/** The shape the moving average walks. Nothing else about a measurement matters to it. */
internal fun MeasurementEntity.toReading(): Reading = Reading(date = date, value = value)

internal fun DayLogEntity.toModel(): DayLog = DayLog(
    date = date,
    planId = planId,
    templateId = templateId,
    dayShiftMinutes = dayShiftMinutes,
    mode = mode.toEnum(DayMode.NORMAL),
    chosenAt = chosenAt,
)

internal fun DayLog.toEntity(): DayLogEntity = DayLogEntity(
    date = date,
    planId = planId,
    templateId = templateId,
    dayShiftMinutes = dayShiftMinutes,
    mode = mode.name,
    chosenAt = chosenAt,
)
