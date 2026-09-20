package com.buildorbreak.core.data.mapper

import com.buildorbreak.core.data.entity.DeliveryAuditEntity
import com.buildorbreak.core.data.entity.TrackEntity
import com.buildorbreak.core.data.entity.TrackSessionEntity
import com.buildorbreak.core.data.entity.TrackUnitEntity
import com.buildorbreak.core.model.audit.DeliveryAudit
import com.buildorbreak.core.model.enums.DeliveryTier
import com.buildorbreak.core.model.enums.TrackUnitState
import com.buildorbreak.core.model.track.Track
import com.buildorbreak.core.model.track.TrackSession
import com.buildorbreak.core.model.track.TrackUnit

internal fun TrackEntity.toModel(): Track = Track(
    id = id,
    planId = planId,
    name = name,
    sourceText = sourceText,
    createdAt = createdAt,
)

internal fun Track.toEntity(): TrackEntity = TrackEntity(
    id = id,
    planId = planId,
    name = name,
    sourceText = sourceText,
    createdAt = createdAt,
)

internal fun TrackUnitEntity.toModel(): TrackUnit = TrackUnit(
    id = id,
    trackId = trackId,
    ordinal = ordinal,
    title = title,
    estimateMinutes = estimateMinutes,
    state = state.toEnum(TrackUnitState.PENDING),
)

internal fun TrackUnit.toEntity(): TrackUnitEntity = TrackUnitEntity(
    id = id,
    trackId = trackId,
    ordinal = ordinal,
    title = title,
    estimateMinutes = estimateMinutes,
    state = state.name,
)

internal fun TrackSessionEntity.toModel(): TrackSession = TrackSession(
    id = id,
    occurrenceId = occurrenceId,
    trackUnitId = trackUnitId,
    minutesSpent = minutesSpent,
    completedUnit = completedUnit,
    leftOffNote = leftOffNote,
)

internal fun TrackSession.toEntity(): TrackSessionEntity = TrackSessionEntity(
    id = id,
    occurrenceId = occurrenceId,
    trackUnitId = trackUnitId,
    minutesSpent = minutesSpent,
    completedUnit = completedUnit,
    leftOffNote = leftOffNote,
)

internal fun DeliveryAuditEntity.toModel(): DeliveryAudit = DeliveryAudit(
    id = id,
    occurrenceId = occurrenceId,
    scheduledFor = scheduledFor,
    firedAt = firedAt,
    // An unreadable tier reads as the weakest one. The reliability figure is the
    // product claim, and a row that cannot be trusted must not be able to flatter
    // it by pretending a full screen alarm was used.
    tier = tier.toEnum(DeliveryTier.IN_APP_ONLY),
    deviceModel = deviceModel,
    manufacturer = manufacturer,
    sdkInt = sdkInt,
    wasDeviceIdle = wasDeviceIdle,
    latencySeconds = latencySeconds,
)

internal fun DeliveryAudit.toEntity(): DeliveryAuditEntity = DeliveryAuditEntity(
    id = id,
    occurrenceId = occurrenceId,
    scheduledFor = scheduledFor,
    firedAt = firedAt,
    tier = tier.name,
    deviceModel = deviceModel,
    manufacturer = manufacturer,
    sdkInt = sdkInt,
    wasDeviceIdle = wasDeviceIdle,
    latencySeconds = latencySeconds,
)
