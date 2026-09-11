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
     * One row per delivery, never one per scheduling pass.
     *
     * The rescheduling pass runs on every app open, every completion and every
     * boot, and each pass sets the same alarm again. Inserting each time made
     * an alarm that fired once look like three that were scheduled, and moved
     * the measured number toward whatever the user did that morning rather
     * than what the phone did. So a pass that finds an open row for the
     * occurrence moves that row; only a step with no open row gets a new one.
     * A row that has fired is never touched, which is the one number the audit
     * exists to produce.
     */
    @Insert
    suspend fun insert(audit: DeliveryAuditEntity): Long

    /** Moves the open row, if there is one. Returns how many rows that was. */
    @Query(
        """
        UPDATE delivery_audit
        SET scheduled_for = :scheduledFor, tier = :tier, was_device_idle = :wasDeviceIdle
        WHERE occurrence_id = :occurrenceId AND fired_at IS NULL
        """,
    )
    suspend fun moveOpen(
        occurrenceId: Long,
        scheduledFor: Instant,
        tier: String,
        wasDeviceIdle: Boolean,
    ): Int

    /** An alarm cancelled before it fired was never given the chance, and is not a missed delivery. */
    @Query("DELETE FROM delivery_audit WHERE occurrence_id = :occurrenceId AND fired_at IS NULL")
    suspend fun deleteUnfired(occurrenceId: Long)

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
