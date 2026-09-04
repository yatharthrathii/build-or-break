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
        WHERE kind = :kind AND date BETWEEN :from AND :to
        ORDER BY date
        """,
    )
    suspend fun readings(kind: String, from: LocalDate, to: LocalDate): List<MeasurementEntity>

    @Upsert
    suspend fun upsert(measurement: MeasurementEntity): Long

    @Query("DELETE FROM measurement WHERE id = :id")
    suspend fun delete(id: Long)

    @Upsert
    suspend fun upsertSkipReason(reason: SkipReasonEntity): Long

    @Query("SELECT * FROM skip_reason WHERE occurrence_id IN (:occurrenceIds)")
    suspend fun skipReasonsFor(occurrenceIds: List<Long>): List<SkipReasonEntity>
}
