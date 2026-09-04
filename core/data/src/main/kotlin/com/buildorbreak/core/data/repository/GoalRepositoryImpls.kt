package com.buildorbreak.core.data.repository

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.common.time.TimeProvider
import com.buildorbreak.core.data.dao.DayCloseDao
import com.buildorbreak.core.data.dao.GoalDao
import com.buildorbreak.core.data.dao.MilestoneDao
import com.buildorbreak.core.data.mapper.toEntity
import com.buildorbreak.core.data.mapper.toModel
import com.buildorbreak.core.data.mapper.toModelOrNull
import com.buildorbreak.core.domain.error.DomainError.DataError
import com.buildorbreak.core.domain.repository.DayCloseRepository
import com.buildorbreak.core.domain.repository.GoalRepository
import com.buildorbreak.core.domain.repository.MilestoneRepository
import com.buildorbreak.core.model.enums.Milestone
import com.buildorbreak.core.model.goal.DayClose
import com.buildorbreak.core.model.goal.Goal
import com.buildorbreak.core.model.goal.GoalProgress
import com.buildorbreak.core.model.goal.MilestoneAward
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private const val DAYS_IN_WEEK = 6L

class GoalRepositoryImpl @Inject constructor(
    private val goals: GoalDao,
    private val dispatchers: AppDispatchers,
) : GoalRepository {

    override fun observeActive(planId: Long): Flow<Goal?> =
        goals.observeActive(planId).map { it?.toModel() }.flowOn(dispatchers.io)

    override fun observeProgress(goalId: Long): Flow<List<GoalProgress>> =
        goals.observeProgress(goalId).map { rows -> rows.map { it.toModel() } }.flowOn(dispatchers.io)

    override suspend fun upsertProgress(progress: GoalProgress): Outcome<Unit, DataError> =
        sqlOutcome(dispatchers.io) { goals.upsertProgress(progress.toEntity()) }

    /**
     * [week] is the first day of the week, and the range is closed at both ends.
     *
     * Marking a week as not counting is what keeps an illness or a fortnight
     * away from permanently bending a projection the user never agreed to. It
     * excludes the days from the rate, never deletes them: what happened still
     * happened, it just stops being evidence of a trend.
     */
    override suspend fun setWeekCounted(goalId: Long, week: LocalDate, counted: Boolean): Outcome<Unit, DataError> =
        sqlOutcome(dispatchers.io) {
            goals.setRangeCounted(goalId, week, week.plusDays(DAYS_IN_WEEK), counted)
        }
}

class DayCloseRepositoryImpl @Inject constructor(
    private val closes: DayCloseDao,
    private val dispatchers: AppDispatchers,
) : DayCloseRepository {

    override fun observeRange(from: LocalDate, to: LocalDate): Flow<List<DayClose>> =
        closes.observeRange(from, to).map { rows -> rows.map { it.toModel() } }.flowOn(dispatchers.io)

    override suspend fun upsert(close: DayClose): Outcome<Unit, DataError> =
        sqlOutcome(dispatchers.io) { closes.upsert(close.toEntity()) }

    override suspend fun lastClosedDate(): LocalDate? = withContext(dispatchers.io) { closes.lastClosedDate() }
}

class MilestoneRepositoryImpl @Inject constructor(
    private val milestones: MilestoneDao,
    private val time: TimeProvider,
    private val dispatchers: AppDispatchers,
) : MilestoneRepository {

    /**
     * Rows whose name this build does not recognise are dropped rather than
     * shown. An award exists only to stop something firing twice, and a name
     * nothing can match is not suppressing anything.
     */
    override fun observeUnseen(): Flow<List<MilestoneAward>> = milestones.observeUnseen()
        .map { rows -> rows.mapNotNull { it.toModelOrNull() } }
        .flowOn(dispatchers.io)

    override suspend fun awarded(): List<MilestoneAward> = withContext(dispatchers.io) {
        milestones.awarded().mapNotNull { it.toModelOrNull() }
    }

    override suspend fun award(award: MilestoneAward): Outcome<Unit, DataError> =
        sqlOutcome(dispatchers.io) { milestones.award(award.toEntity()) }

    override suspend fun markSeen(milestone: Milestone): Outcome<Unit, DataError> =
        sqlOutcome(dispatchers.io) { milestones.markSeen(milestone.name, time.now()) }
}
