package com.buildorbreak.core.domain.fake

import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.domain.error.DomainError.DataError
import com.buildorbreak.core.domain.repository.DeliveryAuditRepository
import com.buildorbreak.core.domain.repository.GoalRepository
import com.buildorbreak.core.domain.repository.MilestoneRepository
import com.buildorbreak.core.model.audit.DeliveryAudit
import com.buildorbreak.core.model.enums.Milestone
import com.buildorbreak.core.model.goal.Goal
import com.buildorbreak.core.model.goal.GoalProgress
import com.buildorbreak.core.model.goal.MilestoneAward
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Goals and their per day rows, in memory.
 *
 * Only one goal can be active on a plan, and this enforces it the same way the
 * real one does. A fake that allowed two would let a test pass over a state the
 * database cannot produce.
 */
class FakeGoalRepository : GoalRepository {
    val goals = MutableStateFlow<List<Goal>>(emptyList())
    val progress = MutableStateFlow<List<GoalProgress>>(emptyList())
    private var nextId = 1L

    override fun observeActive(planId: Long): Flow<Goal?> =
        goals.map { list -> list.firstOrNull { it.planId == planId && it.isActive } }

    override suspend fun byId(goalId: Long): Goal? = goals.value.firstOrNull { it.id == goalId }

    override suspend fun upsert(goal: Goal): Outcome<Long, DataError> {
        val id = if (goal.id == 0L) nextId++ else goal.id
        val written = goal.copy(id = id)

        goals.value = goals.value
            .filterNot { it.id == id }
            .map { if (written.isActive && it.planId == written.planId) it.copy(isActive = false) else it }
            .plus(written)

        return Outcome.Success(id)
    }

    override suspend fun deactivate(goalId: Long): Outcome<Unit, DataError> {
        goals.value = goals.value.map { if (it.id == goalId) it.copy(isActive = false) else it }

        return Outcome.Success(Unit)
    }

    override fun observeProgress(goalId: Long): Flow<List<GoalProgress>> =
        progress.map { rows -> rows.filter { it.goalId == goalId }.sortedBy { it.date } }

    override suspend fun upsertProgress(progress: GoalProgress): Outcome<Unit, DataError> {
        val rows = this.progress
        rows.value = rows.value.filterNot { it.goalId == progress.goalId && it.date == progress.date } + progress

        return Outcome.Success(Unit)
    }

    override suspend fun setWeekCounted(goalId: Long, week: LocalDate, counted: Boolean): Outcome<Unit, DataError> {
        val range = week..week.plusDays(6)
        progress.value = progress.value.map {
            if (it.goalId == goalId && it.date in range) it.copy(counted = counted) else it
        }

        return Outcome.Success(Unit)
    }
}

/** Awards, keyed by milestone, exactly as the primary key is. */
class FakeMilestoneRepository : MilestoneRepository {
    val awards = MutableStateFlow<List<MilestoneAward>>(emptyList())

    override fun observeUnseen(): Flow<List<MilestoneAward>> = awards.map { list -> list.filter { it.seenAt == null } }

    override suspend fun awarded(): List<MilestoneAward> = awards.value

    override suspend fun award(award: MilestoneAward): Outcome<Unit, DataError> {
        awards.value = awards.value.filterNot { it.milestone == award.milestone } + award

        return Outcome.Success(Unit)
    }

    override suspend fun markSeen(milestone: Milestone): Outcome<Unit, DataError> {
        awards.value = awards.value.map { if (it.milestone == milestone) it.copy(seenAt = Instant.EPOCH) else it }

        return Outcome.Success(Unit)
    }
}

/** What was scheduled and what fired, so the reliability figure can be asserted. */
class FakeDeliveryAuditRepository : DeliveryAuditRepository {
    val rows = MutableStateFlow<List<DeliveryAudit>>(emptyList())

    override suspend fun recordScheduled(audit: DeliveryAudit): Outcome<Unit, DataError> {
        val open = rows.value.firstOrNull { it.occurrenceId == audit.occurrenceId && !it.fired }

        rows.value = if (open == null) {
            rows.value + audit.copy(id = rows.value.size + 1L)
        } else {
            rows.value.map {
                if (it.id ==
                    open.id
                ) {
                    it.copy(scheduledFor = audit.scheduledFor, tier = audit.tier)
                } else {
                    it
                }
            }
        }

        return Outcome.Success(Unit)
    }

    override suspend fun discardUnfired(occurrenceId: Long): Outcome<Unit, DataError> {
        rows.value = rows.value.filterNot { it.occurrenceId == occurrenceId && !it.fired }

        return Outcome.Success(Unit)
    }

    override suspend fun recordFired(occurrenceId: Long, firedAt: Instant): Outcome<Unit, DataError> {
        rows.value = rows.value.map { row ->
            if (row.occurrenceId != occurrenceId || row.fired) {
                row
            } else {
                row.copy(
                    firedAt = firedAt,
                    latencySeconds = (firedAt.toEpochMilli() - row.scheduledFor.toEpochMilli()) / MILLIS_PER_SECOND,
                )
            }
        }

        return Outcome.Success(Unit)
    }

    override fun observeSince(instant: Instant): Flow<List<DeliveryAudit>> =
        rows.map { list -> list.filter { it.scheduledFor >= instant } }

    override suspend fun pruneBefore(instant: Instant): Outcome<Unit, DataError> {
        rows.value = rows.value.filter { it.scheduledFor >= instant }

        return Outcome.Success(Unit)
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}
