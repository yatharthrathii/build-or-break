package com.buildorbreak.app.feature.today

import com.buildorbreak.core.domain.goal.ConsistencyScore
import com.buildorbreak.core.domain.goal.GoalSnapshot
import com.buildorbreak.core.domain.goal.PointsTally
import com.buildorbreak.core.domain.usecase.LogMeasurementUseCase
import com.buildorbreak.core.domain.usecase.MarkMilestoneSeenUseCase
import com.buildorbreak.core.domain.usecase.ObserveConsistencyUseCase
import com.buildorbreak.core.domain.usecase.ObserveEarnedMilestoneUseCase
import com.buildorbreak.core.domain.usecase.ObserveGoalUseCase
import com.buildorbreak.core.domain.usecase.ObservePointsUseCase
import com.buildorbreak.core.domain.usecase.ObserveRunUseCase
import com.buildorbreak.core.domain.usecase.ObserveWalletUseCase
import com.buildorbreak.core.domain.usecase.TrackHead
import com.buildorbreak.core.model.enums.Milestone
import com.buildorbreak.core.model.goal.MilestoneAward
import com.buildorbreak.core.model.goal.Wallet
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
    private val observeGoal: ObserveGoalUseCase,
    private val observePoints: ObservePointsUseCase,
    private val observeWallet: ObserveWalletUseCase,
    private val markSeen: MarkMilestoneSeenUseCase,
    private val logNumber: LogMeasurementUseCase,
    private val trackWatch: TrackWatch,
) {
    /** Every syllabus on the plan, keyed by id. */
    fun tracks(): Flow<Map<Long, TrackHead>> = trackWatch.heads()

    suspend fun recordSession(
        occurrenceId: Long,
        unitId: Long,
        minutes: Int,
        finished: Boolean,
        leftOff: String,
    ) {
        trackWatch.recordSession(occurrenceId, unitId, minutes, finished, leftOff)
    }

    /** No sitting for an undone step. A no op when there was none. */
    suspend fun forgetSession(occurrenceId: Long) {
        trackWatch.forgetSession(occurrenceId)
    }

    fun run(on: LocalDate): Flow<Int> = observeRun(on)

    fun consistency(on: LocalDate): Flow<ConsistencyScore> = observeConsistency(on)

    fun milestone(): Flow<MilestoneAward?> = observeMilestone()

    /** Every running goal, oldest first. Two at most. */
    fun goals(): Flow<List<GoalSnapshot>> = observeGoal.all()

    fun points(on: LocalDate): Flow<PointsTally> = observePoints(on)

    fun wallet(): Flow<Wallet> = observeWallet()

    suspend fun markMilestoneSeen(milestone: Milestone) {
        markSeen(milestone)
    }

    suspend fun logMeasurement(itemId: Long, occurrenceId: Long, value: Double) {
        logNumber(itemId, occurrenceId, value)
    }
}
