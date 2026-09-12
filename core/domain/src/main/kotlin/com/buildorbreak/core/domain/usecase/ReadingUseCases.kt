package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.common.time.TimeProvider
import com.buildorbreak.core.domain.error.DomainError.DataError
import com.buildorbreak.core.domain.goal.GoalCloser
import com.buildorbreak.core.domain.repository.GoalRepository
import com.buildorbreak.core.domain.repository.MeasurementRepository
import com.buildorbreak.core.domain.repository.PlanRepository
import com.buildorbreak.core.model.enums.GoalKind
import com.buildorbreak.core.model.execution.Measurement
import com.buildorbreak.core.model.goal.Goal
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Every number recorded against the goal, and the goal itself.
 *
 * Both together because a list of bare numbers cannot be read: forty nine
 * means nothing until you know the series is kilograms and the target is
 * fifty five.
 */
data class GoalSeries(val goal: Goal, val readings: List<Measurement>)

/**
 * The series behind a measured goal, newest first.
 *
 * Null when there is no plan, no goal, or a goal that counts rather than
 * measures. Only a NUMBER goal has readings; a count of gym sessions is a
 * tally of completions and lives in the occurrence table, where correcting
 * it means un-ticking the step rather than editing a figure.
 */
class ObserveGoalReadingsUseCase @Inject constructor(
    private val plans: PlanRepository,
    private val goals: GoalRepository,
    private val measurements: MeasurementRepository,
    private val dispatchers: AppDispatchers,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<GoalSeries?> = plans.observeActive()
        .flatMapLatest { plan -> if (plan == null) flowOf(null) else goals.observeActive(plan.id) }
        .flatMapLatest { goal ->
            if (goal == null || goal.kind != GoalKind.NUMBER) {
                flowOf(null)
            } else {
                measurements.observeSeries(goal.valueKind, goal.itemId).map { GoalSeries(goal, it) }
            }
        }
        .flowOn(dispatchers.default)
}

/**
 * Corrects one reading, and puts the history that was built on it right.
 *
 * A weigh in typed as 720 instead of 72 does not stop being wrong at
 * midnight. It has already been averaged into every goal row written since,
 * and those rows are what the bar, the projection and the whole Insights
 * page are drawn from, so fixing the number without rebuilding them would
 * leave the list saying 72 and the goal still acting on 720.
 *
 * `GoalProgressWriter` says a row is written once and never recomputed. That
 * rule is about the app recomputing behind the user's back, which would make
 * a tally of what you actually did wobble on its own. This is the user
 * saying the input was wrong, and it is the one case where the history has
 * to be rebuilt, so it is done here, explicitly, and only from the day that
 * changed forwards.
 */
class SaveReadingUseCase @Inject constructor(
    private val measurements: MeasurementRepository,
    private val recompute: RecomputeGoalHistoryUseCase,
    private val dispatchers: AppDispatchers,
) {

    suspend operator fun invoke(reading: Measurement, value: Double): Outcome<Unit, DataError> =
        withContext(dispatchers.io) {
            if (!value.isFinite() || value < 0.0) return@withContext Outcome.Failure(DataError.ConstraintViolation)

            val written = measurements.upsert(reading.copy(value = value))
            if (written is Outcome.Success) recompute(reading.date)

            written
        }
}

/** Removes a reading that never happened, and rebuilds what was averaged from it. */
class DeleteReadingUseCase @Inject constructor(
    private val measurements: MeasurementRepository,
    private val recompute: RecomputeGoalHistoryUseCase,
    private val dispatchers: AppDispatchers,
) {

    suspend operator fun invoke(reading: Measurement): Outcome<Unit, DataError> = withContext(dispatchers.io) {
        val removed = measurements.delete(reading.id)
        if (removed is Outcome.Success) recompute(reading.date)

        removed
    }
}

/**
 * Rewrites the goal's stored days from [from] to yesterday.
 *
 * Through `GoalCloser`, so a rebuilt row is written by exactly the same code
 * that wrote it the first time and cannot drift from it. Forwards in date
 * order, because each row reads the one before it for its running total.
 *
 * It stops at yesterday. Today has no row until tonight's close, and writing
 * one early would have the live screen add today's work to a row that
 * already contains it.
 */
class RecomputeGoalHistoryUseCase @Inject constructor(
    private val plans: PlanRepository,
    private val goals: GoalRepository,
    private val closer: GoalCloser,
    private val time: TimeProvider,
) {

    suspend operator fun invoke(from: LocalDate) {
        val plan = plans.observeActive().first() ?: return
        val goal = goals.observeActive(plan.id).first() ?: return

        var date = maxOf(from, goal.startDate)
        val last = minOf(time.today().minusDays(1), goal.targetDate)

        while (date <= last) {
            closer.close(plan.id, date)
            date = date.plusDays(1)
        }
    }
}
