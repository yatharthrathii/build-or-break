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

    @Query("SELECT * FROM track WHERE plan_id = :planId ORDER BY created_at")
    fun observeForPlan(planId: Long): Flow<List<TrackEntity>>

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
     */
    @Transaction
    suspend fun upsertWithUnits(track: TrackEntity, units: List<TrackUnitEntity>): Long {
        val trackId = upsertTrack(track)
        upsertUnits(units.map { if (it.trackId == trackId) it else it.copy(trackId = trackId) })
        return trackId
    }

    @Upsert
    suspend fun upsertTrack(track: TrackEntity): Long

    @Upsert
    suspend fun upsertUnits(units: List<TrackUnitEntity>)

    @Query("UPDATE track_unit SET state = :state WHERE id = :unitId")
    suspend fun setUnitState(unitId: Long, state: String)

    @Upsert
    suspend fun upsertSession(session: TrackSessionEntity): Long
}
