package com.buildorbreak.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
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

    /**
     * Retires every other goal on the plan.
     *
     * Only one goal is active at a time on the free tier, so activating one has
     * to retire the rest. Done in SQL and in the same transaction as the write
     * that follows it, because a moment with two active goals is a moment where
     * every screen that says "the goal" is picking one at random.
     */
    @Query("UPDATE goal SET is_active = 0 WHERE plan_id = :planId AND id != :keepId")
    suspend fun deactivateOthers(planId: Long, keepId: Long)

    @Query("UPDATE goal SET is_active = 0 WHERE id = :goalId")
    suspend fun deactivate(goalId: Long)

    @Transaction
    suspend fun upsertAsOnlyActive(goal: GoalEntity): Long {
        val id = upsert(goal)
        val written = if (goal.id == 0L) id else goal.id
        if (goal.isActive) deactivateOthers(goal.planId, written)

        return written
    }

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
