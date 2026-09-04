package com.buildorbreak.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.buildorbreak.core.data.entity.PlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanDao {

    @Query("SELECT * FROM plan WHERE is_active = 1 LIMIT 1")
    fun observeActive(): Flow<PlanEntity?>

    @Query("SELECT * FROM plan ORDER BY created_at")
    fun observeAll(): Flow<List<PlanEntity>>

    @Query("SELECT * FROM plan WHERE id = :id")
    suspend fun byId(id: Long): PlanEntity?

    @Upsert
    suspend fun upsert(plan: PlanEntity): Long

    /**
     * Exactly one plan is active. Clearing every flag before setting one is the
     * only way to guarantee that, and doing it inside a transaction is what stops
     * a crash between the two statements leaving no active plan at all.
     */
    @Transaction
    suspend fun setActive(planId: Long) {
        clearActive()
        markActive(planId)
    }

    @Query("UPDATE plan SET is_active = 0")
    suspend fun clearActive()

    @Query("UPDATE plan SET is_active = 1 WHERE id = :planId")
    suspend fun markActive(planId: Long)
}
