package com.buildorbreak.core.data.repository

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.data.dao.DeliveryAuditDao
import com.buildorbreak.core.data.dao.TrackDao
import com.buildorbreak.core.data.mapper.toEntity
import com.buildorbreak.core.data.mapper.toModel
import com.buildorbreak.core.domain.error.DomainError.DataError
import com.buildorbreak.core.domain.repository.DeliveryAuditRepository
import com.buildorbreak.core.domain.repository.TrackRepository
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

    override fun observeUnits(trackId: Long): Flow<List<TrackUnit>> =
        tracks.observeUnits(trackId).map { rows -> rows.map { it.toModel() } }.flowOn(dispatchers.io)

    override suspend fun nextUnit(trackId: Long): TrackUnit? = withContext(dispatchers.io) {
        tracks.nextUnit(trackId, OPEN_UNIT_STATES)?.toModel()
    }

    override suspend fun upsertTrack(track: Track, units: List<TrackUnit>): Outcome<Long, DataError> =
        sqlOutcome(dispatchers.io) {
            tracks.upsertWithUnits(track.toEntity(), units.map { it.toEntity() })
        }

    /**
     * Advancing the unit is part of recording the session.
     *
     * They are written together because a session that says it finished a unit
     * while the unit still reads as pending would put the syllabus one step
     * behind reality, and the next sitting would reopen work already done.
     */
    override suspend fun recordSession(session: TrackSession): Outcome<Unit, DataError> = sqlOutcome(dispatchers.io) {
        tracks.upsertSession(session.toEntity())
        if (session.completedUnit) {
            tracks.setUnitState(session.trackUnitId, TrackUnitState.DONE.name)
        }
    }
}

class DeliveryAuditRepositoryImpl @Inject constructor(
    private val audits: DeliveryAuditDao,
    private val dispatchers: AppDispatchers,
) : DeliveryAuditRepository {

    override suspend fun recordScheduled(audit: DeliveryAudit): Outcome<Unit, DataError> =
        sqlOutcome(dispatchers.io) { audits.insert(audit.toEntity()) }

    override suspend fun recordFired(occurrenceId: Long, firedAt: Instant): Outcome<Unit, DataError> =
        sqlOutcome(dispatchers.io) { audits.recordFired(occurrenceId, firedAt) }

    override fun observeSince(instant: Instant): Flow<List<DeliveryAudit>> =
        audits.observeSince(instant).map { rows -> rows.map { it.toModel() } }.flowOn(dispatchers.io)

    override suspend fun pruneBefore(instant: Instant): Outcome<Unit, DataError> =
        sqlOutcome(dispatchers.io) { audits.pruneBefore(instant) }
}
