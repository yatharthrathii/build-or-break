package com.buildorbreak.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.buildorbreak.core.data.entity.OccurrenceEntity
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface OccurrenceDao {

    @Query("SELECT * FROM occurrence WHERE date = :date ORDER BY planned_at, sequence_in_day")
    fun observeForDate(date: LocalDate): Flow<List<OccurrenceEntity>>

    @Query("SELECT * FROM occurrence WHERE date BETWEEN :from AND :to ORDER BY date, planned_at")
    suspend fun between(from: LocalDate, to: LocalDate): List<OccurrenceEntity>

    @Query("SELECT * FROM occurrence WHERE id = :id")
    suspend fun byId(id: Long): OccurrenceEntity?

    /**
     * Ignores rows that already exist, which is what makes materialising a day
     * idempotent. The unique index on item, date and sequence does the work; this
     * conflict strategy is what turns a second call into a no op rather than a
     * crash. The reschedule pass runs on boot, on timezone change and on every
     * completion, so this is the common path, not the edge case.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringExisting(occurrences: List<OccurrenceEntity>)

    @Query(
        """
        UPDATE occurrence SET state = :state, settled_at = :at
        WHERE id = :id
        """,
    )
    suspend fun settle(id: Long, state: String, at: Instant)

    @Query(
        """
        UPDATE occurrence
        SET shift_minutes = shift_minutes + :minutes, snooze_count = snooze_count + 1, state = :state
        WHERE id = :id
        """,
    )
    suspend fun shift(id: Long, minutes: Int, state: String)

    @Query("UPDATE occurrence SET scheduled_at = :at WHERE id = :id")
    suspend fun markScheduled(id: Long, at: Instant?)

    @Query("UPDATE occurrence SET fired_at = :at WHERE id = :id")
    suspend fun markFired(id: Long, at: Instant)

    /** Anything that should have fired and did not. Drives the reconcile pass. */
    @Query(
        """
        SELECT * FROM occurrence
        WHERE state = :pendingState AND scheduled_at IS NOT NULL AND scheduled_at < :instant
        ORDER BY scheduled_at
        """,
    )
    suspend fun pendingBefore(instant: Instant, pendingState: String): List<OccurrenceEntity>

    /** Settles a whole day at once, which is what the midnight close does. */
    @Query(
        """
        UPDATE occurrence SET state = :missedState, settled_at = :at
        WHERE date = :date AND state IN (:openStates)
        """,
    )
    suspend fun settleOpen(
        date: LocalDate,
        openStates: List<String>,
        missedState: String,
        at: Instant,
    )
}
