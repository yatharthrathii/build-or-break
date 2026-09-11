package com.buildorbreak.core.domain.repository

import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.domain.error.DomainError.DataError
import com.buildorbreak.core.model.enums.Milestone
import com.buildorbreak.core.model.goal.DayClose
import com.buildorbreak.core.model.goal.Goal
import com.buildorbreak.core.model.goal.GoalProgress
import com.buildorbreak.core.model.goal.MilestoneAward
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Goals and their per day progress. architecture.md section 5.2. */
interface GoalRepository {
    fun observeActive(planId: Long): Flow<Goal?>

    suspend fun byId(goalId: Long): Goal?

    /**
     * Writes a goal and makes it the only active one on its plan.
     *
     * The two happen together because they are one decision. Two active goals
     * on the free tier would make every screen that says "the goal" ambiguous,
     * and a save that left the old one active would produce exactly that state
     * with no way for the user to see it had happened.
     */
    suspend fun upsert(goal: Goal): Outcome<Long, DataError>

    /** Retires a goal without deleting its history. */
    suspend fun deactivate(goalId: Long): Outcome<Unit, DataError>

    fun observeProgress(goalId: Long): Flow<List<GoalProgress>>

    suspend fun upsertProgress(progress: GoalProgress): Outcome<Unit, DataError>

    /**
     * Marks a week as not counting. Illness and travel should not permanently
     * bend a projection the user never agreed to.
     */
    suspend fun setWeekCounted(goalId: Long, week: LocalDate, counted: Boolean): Outcome<Unit, DataError>
}

interface DayCloseRepository {
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<DayClose>>

    suspend fun upsert(close: DayClose): Outcome<Unit, DataError>

    /** Where the daily close should resume from after the app was not opened. */
    suspend fun lastClosedDate(): LocalDate?
}

interface MilestoneRepository {
    fun observeUnseen(): Flow<List<MilestoneAward>>

    /**
     * The existence of a row is the entire anti repeat mechanism. There is no
     * counter and no date arithmetic to get wrong.
     */
    suspend fun awarded(): List<MilestoneAward>

    suspend fun award(award: MilestoneAward): Outcome<Unit, DataError>

    suspend fun markSeen(milestone: Milestone): Outcome<Unit, DataError>
}
