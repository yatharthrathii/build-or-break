package com.buildorbreak.core.domain.repository

import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.domain.error.DomainError.DataError
import com.buildorbreak.core.model.audit.DeliveryAudit
import com.buildorbreak.core.model.enums.TrackUnitState
import com.buildorbreak.core.model.track.Track
import com.buildorbreak.core.model.track.TrackSession
import com.buildorbreak.core.model.track.TrackUnit
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** An ordered syllabus a timeline slot advances through. */
interface TrackRepository {
    fun observeForPlan(planId: Long): Flow<List<Track>>

    fun observeTrack(trackId: Long): Flow<Track?>

    fun observeUnits(trackId: Long): Flow<List<TrackUnit>>

    /** The next unit not yet done, which is what a session opens on. */
    suspend fun nextUnit(trackId: Long): TrackUnit?

    /**
     * Writes the track and its parts, in order, and drops the parts past the
     * end of [units]. A part that keeps its id keeps its state.
     */
    suspend fun upsertTrack(track: Track, units: List<TrackUnit>): Outcome<Long, DataError>

    suspend fun setUnitState(unitId: Long, state: TrackUnitState): Outcome<Unit, DataError>

    /** Takes the syllabus and every part and sitting with it. */
    suspend fun delete(trackId: Long): Outcome<Unit, DataError>
}

/**
 * The sittings: one row each time a track step was done.
 *
 * Apart from [TrackRepository] because a session points at an occurrence as
 * much as at a part, and the one place that takes a sitting back, the undo
 * on Today, knows the occurrence and nothing else.
 */
interface TrackSessionRepository {
    fun observeForTrack(trackId: Long): Flow<List<TrackSession>>

    suspend fun record(session: TrackSession): Outcome<Unit, DataError>

    suspend fun forOccurrence(occurrenceId: Long): List<TrackSession>

    suspend fun delete(sessionId: Long): Outcome<Unit, DataError>

    suspend fun countForUnit(unitId: Long): Int
}

/**
 * What time an alarm was supposed to fire, and what time it did.
 *
 * This is how the reliability claim in the README becomes a measured number
 * rather than a hope, so the write path has to be as cheap and as certain as
 * the alarm path itself.
 */
interface DeliveryAuditRepository {
    /**
     * One open row per occurrence. Scheduling the same step again moves the
     * row it already has; a step whose alarm has fired gets a fresh one, so a
     * snooze after a fire is a second delivery rather than an overwrite.
     */
    suspend fun recordScheduled(audit: DeliveryAudit): Outcome<Unit, DataError>

    suspend fun recordFired(occurrenceId: Long, firedAt: Instant): Outcome<Unit, DataError>

    /** Forgets an alarm that was cancelled before it fired. Fired rows are kept. */
    suspend fun discardUnfired(occurrenceId: Long): Outcome<Unit, DataError>

    /** Rows in a window, for the Reliability screen. */
    fun observeSince(instant: Instant): Flow<List<DeliveryAudit>>

    /** The daily close prunes anything older than the retention window. */
    suspend fun pruneBefore(instant: Instant): Outcome<Unit, DataError>
}
