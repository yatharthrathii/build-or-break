package com.buildorbreak.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.buildorbreak.core.data.entity.TrackEntity
import com.buildorbreak.core.data.entity.TrackSessionEntity
import com.buildorbreak.core.data.entity.TrackUnitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {

    @Query("SELECT * FROM track WHERE plan_id = :planId ORDER BY created_at, id")
    fun observeForPlan(planId: Long): Flow<List<TrackEntity>>

    @Query("SELECT * FROM track WHERE id = :trackId")
    fun observeTrack(trackId: Long): Flow<TrackEntity?>

    @Query("SELECT * FROM track_unit WHERE track_id = :trackId ORDER BY ordinal")
    fun observeUnits(trackId: Long): Flow<List<TrackUnitEntity>>

    /** The next unit not yet finished, which is what a session opens on. */
    @Query(
        """
        SELECT * FROM track_unit
        WHERE track_id = :trackId AND state IN (:openStates)
        ORDER BY ordinal
        LIMIT 1
        """,
    )
    suspend fun nextUnit(trackId: Long, openStates: List<String>): TrackUnitEntity?

    /**
     * A track and its units are written together or not at all. Half a syllabus
     * is worse than none: the user would see progress against a plan that stops
     * in the middle with no way to tell that it did.
     *
     * Units not on the new list are dropped first, by id. A syllabus
     * shortened in the editor must not keep its old tail, and a new part
     * landing on an ordinal an old one still holds would otherwise be
     * silently refused by the unique index.
     */
    @Transaction
    suspend fun upsertWithUnits(track: TrackEntity, units: List<TrackUnitEntity>): Long {
        // An upsert that updated rather than inserted answers with minus one,
        // not with the row's id. The id of an existing track is its own.
        val trackId = upsertTrack(track).takeIf { it > 0 } ?: track.id
        dropUnitsExcept(trackId, units.map { it.id }.filter { it > 0 })
        upsertUnits(units.map { if (it.trackId == trackId) it else it.copy(trackId = trackId) })
        return trackId
    }

    @Upsert
    suspend fun upsertTrack(track: TrackEntity): Long

    @Upsert
    suspend fun upsertUnits(units: List<TrackUnitEntity>)

    @Query("DELETE FROM track_unit WHERE track_id = :trackId AND id NOT IN (:keptIds)")
    suspend fun dropUnitsExcept(trackId: Long, keptIds: List<Long>)

    @Query("UPDATE track_unit SET state = :state WHERE id = :unitId")
    suspend fun setUnitState(unitId: Long, state: String)

    @Query("DELETE FROM track WHERE id = :trackId")
    suspend fun delete(trackId: Long)
}

/** The sittings. Apart from [TrackDao] so neither grows past one idea. */
@Dao
interface TrackSessionDao {

    @Query(
        """
        SELECT * FROM track_session
        WHERE track_unit_id IN (SELECT id FROM track_unit WHERE track_id = :trackId)
        ORDER BY id
        """,
    )
    fun observeForTrack(trackId: Long): Flow<List<TrackSessionEntity>>

    @Query("SELECT * FROM track_session WHERE occurrence_id = :occurrenceId ORDER BY id")
    suspend fun forOccurrence(occurrenceId: Long): List<TrackSessionEntity>

    @Upsert
    suspend fun upsertSession(session: TrackSessionEntity): Long

    @Query("DELETE FROM track_session WHERE id = :sessionId")
    suspend fun delete(sessionId: Long)

    @Query("SELECT COUNT(*) FROM track_session WHERE track_unit_id = :unitId")
    suspend fun countForUnit(unitId: Long): Int
}
