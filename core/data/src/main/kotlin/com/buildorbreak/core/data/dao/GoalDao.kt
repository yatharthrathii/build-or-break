package com.buildorbreak.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.buildorbreak.core.data.entity.GoalEntity
import com.buildorbreak.core.data.entity.GoalProgressEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {

    @Query("SELECT * FROM goal WHERE plan_id = :planId AND is_active = 1 LIMIT 1")
    fun observeActive(planId: Long): Flow<GoalEntity?>

    @Query("SELECT * FROM goal WHERE id = :id")
    suspend fun byId(id: Long): GoalEntity?

    @Upsert
    suspend fun upsert(goal: GoalEntity): Long

    @Query("SELECT * FROM goal_progress WHERE goal_id = :goalId ORDER BY date")
    fun observeProgress(goalId: Long): Flow<List<GoalProgressEntity>>

    @Query("SELECT * FROM goal_progress WHERE goal_id = :goalId ORDER BY date")
    suspend fun progress(goalId: Long): List<GoalProgressEntity>

    @Upsert
    suspend fun upsertProgress(progress: GoalProgressEntity)

    /**
     * Marks a whole week as not counting.
     *
     * Illness and travel should not permanently bend a projection the user never
     * agreed to, and letting them exclude a week is cheaper and more honest than
     * trying to detect one automatically.
     */
    @Query("UPDATE goal_progress SET counted = :counted WHERE goal_id = :goalId AND date BETWEEN :from AND :to")
    suspend fun setRangeCounted(
        goalId: Long,
        from: LocalDate,
        to: LocalDate,
        counted: Boolean,
    )
}
