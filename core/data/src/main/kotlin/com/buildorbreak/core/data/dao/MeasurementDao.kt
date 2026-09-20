package com.buildorbreak.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.buildorbreak.core.data.entity.MeasurementEntity
import com.buildorbreak.core.data.entity.SkipReasonEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasurementDao {

    @Query("SELECT * FROM measurement WHERE item_id = :itemId ORDER BY date")
    fun observeForItem(itemId: Long): Flow<List<MeasurementEntity>>

    /**
     * The series a measured goal smooths, oldest first.
     *
     * Ordered in SQL rather than in the caller because the moving average walks
     * it in one pass and would otherwise have to sort a year of readings on every
     * recalculation.
     */
    @Query(
        """
        SELECT * FROM measurement
        WHERE kind = :kind AND date BETWEEN :from AND :to AND (:itemId IS NULL OR item_id = :itemId)
        ORDER BY date
        """,
    )
    suspend fun readings(
        kind: String,
        from: LocalDate,
        to: LocalDate,
        itemId: Long?,
    ): List<MeasurementEntity>

    @Query(
        """
        SELECT * FROM measurement
        WHERE kind = :kind AND date BETWEEN :from AND :to AND (:itemId IS NULL OR item_id = :itemId)
        ORDER BY date
        """,
    )
    fun observeReadings(
        kind: String,
        from: LocalDate,
        to: LocalDate,
        itemId: Long?,
    ): Flow<List<MeasurementEntity>>

    /**
     * The whole series, newest first, for the screen that lists it.
     *
     * Unbounded by date on purpose. The other two queries feed the average,
     * which only ever wants a window; this one feeds a list somebody scrolls
     * to find the day they typed wrong, and that day can be any day.
     */
    @Query(
        """
        SELECT * FROM measurement
        WHERE kind = :kind AND (:itemId IS NULL OR item_id = :itemId)
        ORDER BY date DESC, id DESC
        """,
    )
    fun observeSeries(kind: String, itemId: Long?): Flow<List<MeasurementEntity>>

    @Upsert
    suspend fun upsert(measurement: MeasurementEntity): Long

    @Query("DELETE FROM measurement WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM measurement WHERE occurrence_id = :occurrenceId")
    suspend fun deleteForOccurrence(occurrenceId: Long)

    @Upsert
    suspend fun upsertSkipReason(reason: SkipReasonEntity): Long

    /** Undoing a skip has to take its reason with it, or the review counts a skip that no longer exists. */
    @Query("DELETE FROM skip_reason WHERE occurrence_id = :occurrenceId")
    suspend fun deleteSkipReasonFor(occurrenceId: Long)

    @Query("SELECT * FROM skip_reason WHERE occurrence_id IN (:occurrenceIds)")
    suspend fun skipReasonsFor(occurrenceIds: List<Long>): List<SkipReasonEntity>
}
