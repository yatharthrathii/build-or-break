package com.buildorbreak.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.buildorbreak.core.data.entity.DeliveryAuditEntity
import java.time.Instant
import kotlinx.coroutines.flow.Flow

@Dao
interface DeliveryAuditDao {

    /**
     * Insert rather than upsert. One row per scheduled alarm, written once at
     * schedule time and updated once at fire time. An upsert here would let a
     * reschedule overwrite the record of an alarm that had already fired, which
     * is the one number the audit exists to produce.
     */
    @Insert
    suspend fun insert(audit: DeliveryAuditEntity): Long

    /**
     * The latency is written at the same moment as the fire time so the two can
     * never disagree. It is denormalised precisely so a reliability figure is one
     * aggregate query rather than a scan.
     */
    @Query(
        """
        UPDATE delivery_audit
        SET fired_at = :firedAt, latency_seconds = (:firedAt - scheduled_for) / 1000
        WHERE occurrence_id = :occurrenceId AND fired_at IS NULL
        """,
    )
    suspend fun recordFired(occurrenceId: Long, firedAt: Instant)

    @Query("SELECT * FROM delivery_audit WHERE scheduled_for >= :instant ORDER BY scheduled_for DESC")
    fun observeSince(instant: Instant): Flow<List<DeliveryAuditEntity>>

    @Query("DELETE FROM delivery_audit WHERE scheduled_for < :instant")
    suspend fun pruneBefore(instant: Instant)
}
