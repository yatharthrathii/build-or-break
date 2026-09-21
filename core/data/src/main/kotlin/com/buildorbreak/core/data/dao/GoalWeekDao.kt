package com.buildorbreak.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.buildorbreak.core.data.entity.GoalWeekSkipEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * The weeks left out of a goal.
 *
 * Apart from `GoalDao` because it is one idea with its own table and its own
 * transaction, and the goal DAO was already as wide as one interface should
 * be.
 */
@Dao
interface GoalWeekDao {

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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun leaveWeekOut(week: GoalWeekSkipEntity)

    @Query("DELETE FROM goal_week_skip WHERE goal_id = :goalId AND week_start = :week")
    suspend fun putWeekBack(goalId: Long, week: LocalDate)

    @Query("SELECT week_start FROM goal_week_skip WHERE goal_id = :goalId")
    fun observeLeftOutWeeks(goalId: Long): Flow<List<LocalDate>>

    /**
     * The week itself, and the days it already has, in one write.
     *
     * Two places because they answer two questions. The week row is what a
     * day written later is checked against; the flag on each day is what
     * the projector reads. Written apart, a crash between them would leave
     * a week that says one thing and days that say another.
     */
    @Transaction
    suspend fun setWeekCounted(
        goalId: Long,
        week: LocalDate,
        weekEnd: LocalDate,
        counted: Boolean,
    ) {
        if (counted) putWeekBack(goalId, week) else leaveWeekOut(GoalWeekSkipEntity(goalId, week))

        setRangeCounted(goalId, week, weekEnd, counted)
    }
}
