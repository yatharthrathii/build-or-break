package com.buildorbreak.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.buildorbreak.core.data.entity.PointEntryEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface PointEntryDao {

    /** Newest first, because the ledger is read from the top. */
    @Query("SELECT * FROM point_entry ORDER BY at DESC, id DESC")
    fun observeAll(): Flow<List<PointEntryEntity>>

    /** The total ever spent. Null when nothing has been, which the caller reads as zero. */
    @Query("SELECT SUM(delta) FROM point_entry WHERE delta < 0")
    fun observeSpent(): Flow<Int?>

    /** The total granted outside the daily close, which today means ads. */
    @Query("SELECT SUM(delta) FROM point_entry WHERE delta > 0")
    fun observeGranted(): Flow<Int?>

    /** The days a freeze was bought for, so the run can carry on across them. */
    @Query("SELECT date FROM point_entry WHERE reason = :reason")
    fun observeDatesFor(reason: String): Flow<List<LocalDate>>

    /** How many of one kind were written on a day. The ad grant is capped by this. */
    @Query("SELECT COUNT(*) FROM point_entry WHERE reason = :reason AND date = :date")
    suspend fun countFor(reason: String, date: LocalDate): Int

    @Insert
    suspend fun insert(entry: PointEntryEntity)
}
