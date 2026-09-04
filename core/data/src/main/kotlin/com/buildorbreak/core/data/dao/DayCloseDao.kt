package com.buildorbreak.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.buildorbreak.core.data.entity.DayCloseEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface DayCloseDao {

    @Query("SELECT * FROM day_close WHERE date BETWEEN :from AND :to ORDER BY date")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<DayCloseEntity>>

    @Query("SELECT * FROM day_close WHERE date BETWEEN :from AND :to ORDER BY date")
    suspend fun range(from: LocalDate, to: LocalDate): List<DayCloseEntity>

    @Upsert
    suspend fun upsert(close: DayCloseEntity)

    /**
     * Where the close should resume from.
     *
     * The app may not be opened for a week, and the daily job may not have run
     * either. Closing from here forward is what stops a gap in the history from
     * quietly becoming a gap in every figure built on it.
     */
    @Query("SELECT MAX(date) FROM day_close")
    suspend fun lastClosedDate(): LocalDate?
}
