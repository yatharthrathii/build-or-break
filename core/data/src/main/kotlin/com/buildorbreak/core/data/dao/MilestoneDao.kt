package com.buildorbreak.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.buildorbreak.core.data.entity.MilestoneAwardEntity
import java.time.Instant
import kotlinx.coroutines.flow.Flow

@Dao
interface MilestoneDao {

    @Query("SELECT * FROM milestone_award WHERE seen_at IS NULL ORDER BY awarded_on")
    fun observeUnseen(): Flow<List<MilestoneAwardEntity>>

    @Query("SELECT * FROM milestone_award ORDER BY awarded_on")
    suspend fun awarded(): List<MilestoneAwardEntity>

    /**
     * Ignores a milestone that has already fired, rather than replacing it.
     *
     * The row is the anti repeat mechanism, and it must keep its original date:
     * overwriting it would let the same milestone appear again and would move the
     * record of when it first happened. Each fires once in the lifetime of an
     * install, and this line is what enforces that.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun award(award: MilestoneAwardEntity)

    @Query("UPDATE milestone_award SET seen_at = :at WHERE milestone = :milestone")
    suspend fun markSeen(milestone: String, at: Instant)
}
