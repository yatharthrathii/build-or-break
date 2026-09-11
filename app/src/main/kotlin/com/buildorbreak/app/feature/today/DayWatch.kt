package com.buildorbreak.app.feature.today

import com.buildorbreak.core.domain.goal.ConsistencyScore
import com.buildorbreak.core.domain.usecase.LogMeasurementUseCase
import com.buildorbreak.core.domain.usecase.MarkMilestoneSeenUseCase
import com.buildorbreak.core.domain.usecase.ObserveConsistencyUseCase
import com.buildorbreak.core.domain.usecase.ObserveEarnedMilestoneUseCase
import com.buildorbreak.core.domain.usecase.ObserveRunUseCase
import com.buildorbreak.core.model.enums.Milestone
import com.buildorbreak.core.model.goal.MilestoneAward
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * The things Today watches beside the day itself, in one injectable bag.
 *
 * The run, the thirty day figure, the milestone that has been earned and not
 * yet said, and the number a completed step can be asked for. None of them is
 * about a single step and none is interesting on its own here. Injecting them
 * one by one gave the ViewModel a constructor nobody could read, which is the
 * same reason `DayActions` exists next door.
 */
class DayWatch @Inject constructor(
    private val observeRun: ObserveRunUseCase,
    private val observeConsistency: ObserveConsistencyUseCase,
    private val observeMilestone: ObserveEarnedMilestoneUseCase,
    private val markSeen: MarkMilestoneSeenUseCase,
    private val logNumber: LogMeasurementUseCase,
) {
    fun run(on: LocalDate): Flow<Int> = observeRun(on)

    fun consistency(on: LocalDate): Flow<ConsistencyScore> = observeConsistency(on)

    fun milestone(): Flow<MilestoneAward?> = observeMilestone()

    suspend fun markMilestoneSeen(milestone: Milestone) {
        markSeen(milestone)
    }

    suspend fun logMeasurement(itemId: Long, occurrenceId: Long, value: Double) {
        logNumber(itemId, occurrenceId, value)
    }
}
