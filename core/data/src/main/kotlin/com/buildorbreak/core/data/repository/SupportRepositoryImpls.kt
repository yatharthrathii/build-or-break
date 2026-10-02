package com.buildorbreak.core.data.repository

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.data.dao.DeliveryAuditDao
import com.buildorbreak.core.data.dao.TrackDao
import com.buildorbreak.core.data.dao.TrackSessionDao
import com.buildorbreak.core.data.mapper.toEntity
import com.buildorbreak.core.data.mapper.toModel
import com.buildorbreak.core.domain.error.DomainError.DataError
import com.buildorbreak.core.domain.repository.DeliveryAuditRepository
import com.buildorbreak.core.domain.repository.TrackRepository
import com.buildorbreak.core.domain.repository.TrackSessionRepository
import com.buildorbreak.core.model.audit.DeliveryAudit
import com.buildorbreak.core.model.enums.TrackUnitState
import com.buildorbreak.core.model.track.Track
import com.buildorbreak.core.model.track.TrackSession
import com.buildorbreak.core.model.track.TrackUnit
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** A unit still to be worked on. Skipped counts as finished with; pending and started do not. */
private val OPEN_UNIT_STATES = listOf(TrackUnitState.PENDING.name, TrackUnitState.IN_PROGRESS.name)

class TrackRepositoryImpl @Inject constructor(
    private val tracks: TrackDao,
    private val dispatchers: AppDispatchers,
) : TrackRepository {

    override fun observeForPlan(planId: Long): Flow<List<Track>> =
        tracks.observeForPlan(planId).map { rows -> rows.map { it.toModel() } }.flowOn(dispatchers.io)

    override fun observeTrack(trackId: Long): Flow<Track?> =
        tracks.observeTrack(trackId).map { it?.toModel() }.flowOn(dispatchers.io)

    override fun observeUnits(trackId: Long): Flow<List<TrackUnit>> =
        tracks.observeUnits(trackId).map { rows -> rows.map { it.toModel() } }.flowOn(dispatchers.io)

    override suspend fun nextUnit(trackId: Long): TrackUnit? = withContext(dispatchers.io) {
        tracks.nextUnit(trackId, OPEN_UNIT_STATES)?.toModel()
    }

    override suspend fun upsertTrack(track: Track, units: List<TrackUnit>): Outcome<Long, DataError> =
        sqlOutcome(dispatchers.io) {
            tracks.upsertWithUnits(track.toEntity(), units.map { it.toEntity() })
        }

    override suspend fun setUnitState(unitId: Long, state: TrackUnitState): Outcome<Unit, DataError> =
        sqlOutcome(dispatchers.io) { tracks.setUnitState(unitId, state.name) }

    override suspend fun delete(trackId: Long): Outcome<Unit, DataError> =
        sqlOutcome(dispatchers.io) { tracks.delete(trackId) }
}

class TrackSessionRepositoryImpl @Inject constructor(
    private val sessions: TrackSessionDao,
    private val dispatchers: AppDispatchers,
) : TrackSessionRepository {

    override fun observeForTrack(trackId: Long): Flow<List<TrackSession>> =
        sessions.observeForTrack(trackId).map { rows -> rows.map { it.toModel() } }.flowOn(dispatchers.io)

    override suspend fun record(session: TrackSession): Outcome<Unit, DataError> = sqlOutcome(dispatchers.io) {
        sessions.upsertSession(session.toEntity())
    }

    override suspend fun forOccurrence(occurrenceId: Long): List<TrackSession> = withContext(dispatchers.io) {
        sessions.forOccurrence(occurrenceId).map { it.toModel() }
    }

    override suspend fun delete(sessionId: Long): Outcome<Unit, DataError> =
        sqlOutcome(dispatchers.io) { sessions.delete(sessionId) }

    override suspend fun countForUnit(unitId: Long): Int = withContext(dispatchers.io) {
        sessions.countForUnit(unitId)
    }
}

class DeliveryAuditRepositoryImpl @Inject constructor(
    private val audits: DeliveryAuditDao,
    private val dispatchers: AppDispatchers,
) : DeliveryAuditRepository {

    override suspend fun recordScheduled(audit: DeliveryAudit): Outcome<Unit, DataError> = sqlOutcome(dispatchers.io) {
        val moved = audits.moveOpen(audit.occurrenceId, audit.scheduledFor, audit.tier.name, audit.wasDeviceIdle)
        if (moved == 0) audits.insert(audit.toEntity())
    }

    override suspend fun recordFired(occurrenceId: Long, firedAt: Instant): Outcome<Unit, DataError> =
        sqlOutcome(dispatchers.io) { audits.recordFired(occurrenceId, firedAt) }

    override suspend fun discardUnfired(occurrenceId: Long): Outcome<Unit, DataError> =
        sqlOutcome(dispatchers.io) { audits.deleteUnfired(occurrenceId) }

    override fun observeSince(instant: Instant): Flow<List<DeliveryAudit>> =
        audits.observeSince(instant).map { rows -> rows.map { it.toModel() } }.flowOn(dispatchers.io)

    override suspend fun pruneBefore(instant: Instant): Outcome<Unit, DataError> =
        sqlOutcome(dispatchers.io) { audits.pruneBefore(instant) }
}
