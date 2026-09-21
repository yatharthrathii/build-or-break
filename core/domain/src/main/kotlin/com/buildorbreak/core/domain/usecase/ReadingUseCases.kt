package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.common.time.TimeProvider
import com.buildorbreak.core.domain.error.DomainError.DataError
import com.buildorbreak.core.domain.goal.GoalCloser
import com.buildorbreak.core.domain.repository.GoalRepository
import com.buildorbreak.core.domain.repository.ItemRepository
import com.buildorbreak.core.domain.repository.MeasurementRepository
import com.buildorbreak.core.domain.repository.PlanRepository
import com.buildorbreak.core.domain.repository.TemplateRepository
import com.buildorbreak.core.model.enums.GoalKind
import com.buildorbreak.core.model.enums.ValueKind
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

    /**
     * [goalId] says which goal, now that there can be two. Without it, the
     * first running goal that is measured: with one goal that is the goal,
     * and with a count and a weight it is the weight, which is the only one
     * of the two that has readings at all.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(goalId: Long? = null): Flow<GoalSeries?> = plans.observeActive()
        .flatMapLatest { plan -> if (plan == null) flowOf(emptyList()) else goals.observeAllActive(plan.id) }
        .map { running -> running.measured(goalId) }
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
 * Records a number for a day that never got one.
 *
 * A measured goal is fed by a step that asks for a figure, and the figure is
 * optional by design: a step ticked from a notification settles the day and
 * asks for nothing. That is the right trade at the moment of ticking and the
 * wrong one an hour later, when the weight has been taken and the app has
 * nowhere to put it. Without this the only way to record it is to un-tick a
 * step that was genuinely done, which trades one wrong record for another.
 *
 * One number a day. A date that already has a reading is corrected rather
 * than given a second row, for the same reason logging twice against one
 * settle replaces rather than averages.
 *
 * Writes through [RecomputeGoalHistoryUseCase] like a correction does,
 * because a number added for last Tuesday changes every average since.
 */
class AddReadingUseCase @Inject constructor(
    private val plans: PlanRepository,
    private val goals: GoalRepository,
    private val templates: TemplateRepository,
    private val items: ItemRepository,
    private val measurements: MeasurementRepository,
    private val recompute: RecomputeGoalHistoryUseCase,
    private val dispatchers: AppDispatchers,
) {

    suspend operator fun invoke(date: LocalDate, value: Double, goalId: Long? = null): Outcome<Unit, DataError> =
        withContext(dispatchers.io) {
            if (!value.isFinite() || value < 0.0) return@withContext Outcome.Failure(DataError.ConstraintViolation)

            val plan = plans.observeActive().first() ?: return@withContext Outcome.Failure(DataError.NotFound)
            val running = goals.observeAllActive(plan.id).first()
            if (running.isEmpty()) return@withContext Outcome.Failure(DataError.NotFound)

            val goal = running.measured(goalId) ?: return@withContext Outcome.Failure(DataError.ConstraintViolation)

            val series = measurements.observeSeries(goal.valueKind, goal.itemId).first()
            val existing = series.firstOrNull { it.date == date }
            val itemId = existing?.itemId
                ?: goal.itemId
                ?: series.lastOrNull()?.itemId
                ?: stepThatRecords(plan.id, goal.valueKind)
                ?: return@withContext Outcome.Failure(DataError.NotFound)

            val written = measurements.upsert(
                Measurement(
                    id = existing?.id ?: 0,
                    itemId = itemId,
                    // Kept, so undoing that settle still takes its number with it.
                    occurrenceId = existing?.occurrenceId,
                    date = date,
                    value = value,
                    kind = goal.valueKind,
                ),
            )

            if (written is Outcome.Success) recompute(date)

            written
        }

    /**
     * The step this goal's numbers normally arrive from.
     *
     * Only reached on the first reading of a goal whose series is still
     * empty. The reading has to hang off some step, because that is how the
     * export finds it again, and the step that asks for this kind of figure
     * is the one the user would have typed it into.
     *
     * Falls back to any step at all when nothing asks for this figure. A goal
     * can be measured without a step that collects the number, and refusing
     * the reading would leave somebody with a weight goal and nowhere to put
     * a weight. The series itself is found by kind, not by step, so the choice
     * changes nothing the user sees.
     */
    private suspend fun stepThatRecords(planId: Long, kind: ValueKind): Long? {
        val steps = templates.observeForPlan(planId)
            .first()
            .flatMap { items.observeForTemplate(it.id).first() }

        return steps.firstOrNull { it.valueKind == kind }?.id ?: steps.firstOrNull()?.id
    }
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
/** The goal asked for, or failing that the first one that is measured. */
private fun List<Goal>.measured(goalId: Long?): Goal? =
    (if (goalId != null) firstOrNull { it.id == goalId } else null)?.takeIf { it.kind == GoalKind.NUMBER }
        ?: firstOrNull { it.kind == GoalKind.NUMBER }.takeIf { goalId == null }

class RecomputeGoalHistoryUseCase @Inject constructor(
    private val plans: PlanRepository,
    private val goals: GoalRepository,
    private val closer: GoalCloser,
    private val time: TimeProvider,
) {

    suspend operator fun invoke(from: LocalDate) {
        val plan = plans.observeActive().first() ?: return
        val running = goals.observeAllActive(plan.id).first()
        if (running.isEmpty()) return

        // The widest window any running goal has. The closer leaves a goal
        // alone on a date outside its own, so the extra days cost nothing.
        var date = maxOf(from, running.minOf { it.startDate })
        val last = minOf(time.today().minusDays(1), running.maxOf { it.targetDate })

        while (date <= last) {
            closer.close(plan.id, date)
            date = date.plusDays(1)
        }
    }
}
