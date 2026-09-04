package com.buildorbreak.core.domain.repository

import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.domain.error.DomainError.DataError
import com.buildorbreak.core.model.audit.DeliveryAudit
import com.buildorbreak.core.model.track.Track
import com.buildorbreak.core.model.track.TrackSession
import com.buildorbreak.core.model.track.TrackUnit
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** An ordered syllabus a timeline slot advances through. See M7. */
interface TrackRepository {
    fun observeForPlan(planId: Long): Flow<List<Track>>

    fun observeUnits(trackId: Long): Flow<List<TrackUnit>>

    /** The next unit not yet done, which is what a session opens on. */
    suspend fun nextUnit(trackId: Long): TrackUnit?

    suspend fun upsertTrack(track: Track, units: List<TrackUnit>): Outcome<Long, DataError>

    suspend fun recordSession(session: TrackSession): Outcome<Unit, DataError>
}

/**
 * What time an alarm was supposed to fire, and what time it did.
 *
 * This is how the reliability claim in the README becomes a measured number
 * rather than a hope, so the write path has to be as cheap and as certain as
 * the alarm path itself.
 */
interface DeliveryAuditRepository {
    suspend fun recordScheduled(audit: DeliveryAudit): Outcome<Unit, DataError>

    suspend fun recordFired(occurrenceId: Long, firedAt: Instant): Outcome<Unit, DataError>

    /** Rows in a window, for the Reliability screen. */
    fun observeSince(instant: Instant): Flow<List<DeliveryAudit>>

    /** The daily close prunes anything older than the retention window. */
    suspend fun pruneBefore(instant: Instant): Outcome<Unit, DataError>
}
