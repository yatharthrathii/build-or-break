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
    /** The first goal that is running, oldest first. The one a screen with room for one shows. */
    fun observeActive(planId: Long): Flow<Goal?>

    /** Every goal that is running, oldest first. Never more than `Prices.MAX_GOALS`. */
    fun observeAllActive(planId: Long): Flow<List<Goal>>

    suspend fun byId(goalId: Long): Goal?

    /**
     * Writes a goal, and touches no other.
     *
     * It used to retire every other goal on the plan, because only one was
     * allowed. How many may run at once is a rule now, with a price attached,
     * and rules live in `SaveGoalUseCase`. Left here it would also have
     * retired the first goal every time a backup holding two was restored.
     */
    suspend fun upsert(goal: Goal): Outcome<Long, DataError>

    /** Retires a goal without deleting its history. */
    suspend fun deactivate(goalId: Long): Outcome<Unit, DataError>

    fun observeProgress(goalId: Long): Flow<List<GoalProgress>>

    suspend fun upsertProgress(progress: GoalProgress): Outcome<Unit, DataError>

    /**
     * Marks a week as not counting. Illness and travel should not permanently
     * bend a projection the user never agreed to.
     *
     * The week is remembered in its own right, not only on the days it
     * already has. Somebody who falls ill on a Monday morning has a week with
     * no days in it yet, and that is exactly the week they want to leave out.
     */
    suspend fun setWeekCounted(goalId: Long, week: LocalDate, counted: Boolean): Outcome<Unit, DataError>

    /** The Mondays of every week left out of this goal. */
    fun observeLeftOutWeeks(goalId: Long): Flow<Set<LocalDate>>
}

interface DayCloseRepository {
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<DayClose>>

    /** Every close there is, oldest first. One row a day, so this stays small. */
    fun observeAll(): Flow<List<DayClose>>

    suspend fun upsert(close: DayClose): Outcome<Unit, DataError>

    /** Where the daily close should resume from after the app was not opened. */
    suspend fun lastClosedDate(): LocalDate?
}

interface MilestoneRepository {
    fun observeUnseen(): Flow<List<MilestoneAward>>

    /** Everything ever awarded, seen or not, oldest first. */
    fun observeAwarded(): Flow<List<MilestoneAward>>

    /**
     * The existence of a row is the entire anti repeat mechanism. There is no
     * counter and no date arithmetic to get wrong.
     */
    suspend fun awarded(): List<MilestoneAward>

    suspend fun award(award: MilestoneAward): Outcome<Unit, DataError>

    suspend fun markSeen(milestone: Milestone): Outcome<Unit, DataError>
}
