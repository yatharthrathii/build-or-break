package com.buildorbreak.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.buildorbreak.core.data.entity.GoalEntity
import com.buildorbreak.core.data.entity.GoalProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {

    @Query("SELECT * FROM goal WHERE plan_id = :planId AND is_active = 1 ORDER BY id LIMIT 1")
    fun observeActive(planId: Long): Flow<GoalEntity?>

    @Query("SELECT * FROM goal WHERE plan_id = :planId AND is_active = 1 ORDER BY id")
    fun observeAllActive(planId: Long): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goal WHERE id = :id")
    suspend fun byId(id: Long): GoalEntity?

    @Upsert
    suspend fun upsert(goal: GoalEntity): Long

    @Query("UPDATE goal SET is_active = 0 WHERE id = :goalId")
    suspend fun deactivate(goalId: Long)

    @Query("SELECT * FROM goal_progress WHERE goal_id = :goalId ORDER BY date")
    fun observeProgress(goalId: Long): Flow<List<GoalProgressEntity>>

    @Query("SELECT * FROM goal_progress WHERE goal_id = :goalId ORDER BY date")
    suspend fun progress(goalId: Long): List<GoalProgressEntity>

    @Upsert
    suspend fun upsertProgress(progress: GoalProgressEntity)
}
