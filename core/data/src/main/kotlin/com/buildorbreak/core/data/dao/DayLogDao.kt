package com.buildorbreak.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.buildorbreak.core.data.entity.DayLogEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface DayLogDao {

    @Query("SELECT * FROM day_log WHERE date = :date")
    fun observe(date: LocalDate): Flow<DayLogEntity?>

    @Query("SELECT * FROM day_log WHERE date = :date")
    suspend fun forDate(date: LocalDate): DayLogEntity?

    @Upsert
    suspend fun upsert(log: DayLogEntity)

    /**
     * The shift is written on its own rather than through the whole row, so
     * moving the day cannot accidentally overwrite which template was chosen.
     * Both are edited from the same screen and a full upsert would make that
     * mistake easy to introduce and hard to notice.
     */
    @Query("UPDATE day_log SET day_shift_minutes = :minutes, mode = :mode WHERE date = :date")
    suspend fun setShift(date: LocalDate, minutes: Int, mode: String)
}
