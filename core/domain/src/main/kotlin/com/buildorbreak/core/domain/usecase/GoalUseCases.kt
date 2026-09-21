package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.common.time.TimeProvider
import com.buildorbreak.core.domain.error.DomainError.DataError
import com.buildorbreak.core.domain.goal.GoalCalculator
import com.buildorbreak.core.domain.goal.GoalSnapshot
import com.buildorbreak.core.domain.goal.GoalWeek
import com.buildorbreak.core.domain.goal.currentFor
import com.buildorbreak.core.domain.repository.GoalRepository
import com.buildorbreak.core.domain.repository.ItemRepository
import com.buildorbreak.core.domain.repository.MeasurementRepository
import com.buildorbreak.core.domain.repository.OccurrenceRepository
import com.buildorbreak.core.domain.repository.PlanRepository
import com.buildorbreak.core.model.enums.GoalKind
import com.buildorbreak.core.model.enums.ValueKind
import com.buildorbreak.core.model.goal.Goal
import com.buildorbreak.core.model.goal.GoalProgress
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** How many points the sparkline draws. Wider than this and the trend is unreadable. */
private const val TRAIL_LENGTH = 30

/** A month back. Further than that and nobody remembers which week they were ill. */
private const val WEEKS_SHOWN = 4

/**
 * The active goal, and every number a screen would want about it.
 *
 * Emits null when there is no plan or no goal, which is the ordinary state for
 * most of this app's users: a goal is optional and the routine works without
 * one. Nothing here nags about setting one.
 *
 * The arithmetic is `GoalCalculator`'s and the rows are the daily close's. This
 * assembles them, which is exactly the amount of work a use case is allowed to
 * do: no screen can now show a projection that disagrees with the pace line
 * beside it, because both come from here.
 */
class ObserveGoalUseCase @Inject constructor(
    private val plans: PlanRepository,
    private val goals: GoalRepository,
    private val today: GoalToday,
    private val calculator: GoalCalculator,
    private val time: TimeProvider,
    private val dispatchers: AppDispatchers,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<GoalSnapshot?> = plans.observeActive()
        .flatMapLatest { plan -> if (plan == null) flowOf(null) else goals.observeActive(plan.id) }
        .flatMapLatest { goal -> if (goal == null) flowOf(null) else snapshots(goal) }
        .flowOn(dispatchers.default)

    private fun snapshots(goal: Goal): Flow<GoalSnapshot> = combine(
        goals.observeProgress(goal.id),
        today.contributionTo(goal),
        today.readingFor(goal),
    ) { rows, sinceMidnight, reading ->
        snapshotOf(goal, rows, sinceMidnight, reading)
    }

    private fun snapshotOf(
        goal: Goal,
        rows: List<GoalProgress>,
        sinceMidnight: Double,
        reading: Double?,
    ): GoalSnapshot {
        val today = time.today()

        // Today has no row until tonight, so it is added here. Without this a
        // counting goal sits at the same number all day however many times
        // the step is done, which is the single most deflating thing a goal
        // screen can do. The arithmetic is the writer's, so the number here
        // and the row written at midnight cannot disagree.
        val current = rows.currentFor(goal) + sinceMidnight
        val closed = rows.any { it.rawValue != null || it.cumulative > 0.0 }

        return GoalSnapshot(
            goal = goal,
            current = current,
            paceTarget = calculator.paceTarget(goal, today),
            // Every row, left out or not. The projector is what knows what
            // leaving a week out means, and it cannot take a week out of the
            // rate if the week was filtered away before it arrived.
            projected = calculator.project(goal, rows),
            percent = calculator.percentComplete(goal, current, today),
            daysLeft = goal.daysLeft(today),
            trail = trailOf(goal, rows),
            // The rows exist from the first close after the goal was set, and
            // a row with nothing in it is not evidence of anything. Without
            // this the screen would draw a confident "behind pace" over a goal
            // set yesterday that nobody has had a chance to work on.
            hasData = sinceMidnight > 0.0 || closed,
            // The projector reads the newest row, so that row is the one that
            // has to have time behind it.
            hasProjection = closed && rows.maxByOrNull { it.date }?.let { goal.daysElapsed(it.date) > 0 } == true,
            on = today,
            todayReading = reading,
            weeks = weeksOf(rows),
        )
    }

    /**
     * From every row, not only the counted ones, or a week that was left out
     * would vanish from the list and could never be put back.
     */
    private fun weeksOf(rows: List<GoalProgress>): List<GoalWeek> = rows
        .groupBy { it.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
        .map { (monday, days) -> GoalWeek(start = monday, counted = days.any { it.counted }) }
        .sortedByDescending { it.start }
        .take(WEEKS_SHOWN)

    private fun trailOf(goal: Goal, rows: List<GoalProgress>): List<Double> = rows
        .sortedBy { it.date }
        .mapNotNull { row ->
            when (goal.kind) {
                GoalKind.COUNT, GoalKind.DURATION -> row.cumulative
                GoalKind.NUMBER, GoalKind.CONSISTENCY -> row.smoothedValue ?: row.rawValue
            }
        }
        .takeLast(TRAIL_LENGTH)
}

/**
 * What today has added to a goal before the day has been closed.
 *
 * Only for the two kinds where the answer is a plain tally and cannot be
 * wrong: a count of completions and a sum of minutes. Both are exactly what
 * `GoalProgressWriter` will bank tonight, so the live number and the stored
 * one are the same arithmetic run twice.
 *
 * **A measured goal is deliberately not included.** Its honest value is the
 * seven day average, and one weigh in cannot be smoothed. Showing today's raw
 * reading here would put a number on screen that the app spends the rest of
 * its design refusing to act on, and it would move a progress bar by a kilo
 * of water. It waits for the close, which is the whole point of smoothing.
 */
class GoalToday @Inject constructor(
    private val occurrences: OccurrenceRepository,
    private val measurements: MeasurementRepository,
    private val items: ItemRepository,
    private val time: TimeProvider,
) {

    fun contributionTo(goal: Goal): Flow<Double> {
        val itemId = goal.itemId
        if (itemId == null || (goal.kind != GoalKind.COUNT && goal.kind != GoalKind.DURATION)) {
            return flowOf(0.0)
        }

        val date = time.today()
        if (date < goal.startDate || date > goal.targetDate) return flowOf(0.0)

        return occurrences.observeForDate(date).map { rows ->
            val done = rows.count { it.itemId == itemId && it.isDone }

            if (goal.kind == GoalKind.COUNT) done.toDouble() else minutesFor(itemId, done)
        }
    }

    /**
     * Today's raw reading for a measured goal, or null.
     *
     * Not a contribution: it is never added to the current value, which stays
     * the smoothed one. It exists so the card can say "today 50.5" next to
     * "49.8", which is the difference between a number that looks wrong and a
     * number that looks smoothed.
     */
    fun readingFor(goal: Goal): Flow<Double?> {
        if (goal.kind != GoalKind.NUMBER) return flowOf(null)

        val date = time.today()

        return measurements.observeReadings(goal.valueKind, date, date, goal.itemId).map { readings ->
            readings.lastOrNull()?.value
        }
    }

    /** What was logged today, or what the step is planned to take, as the writer does. */
    private suspend fun minutesFor(itemId: Long, done: Int): Double {
        if (done == 0) return 0.0

        val date = time.today()
        val logged = measurements.observeForItem(itemId).first()
            .filter { it.date == date && it.kind == ValueKind.MINUTES }
            .sumOf { it.value }
        if (logged > 0.0) return logged

        return done * (items.byId(itemId)?.duration?.inWholeMinutes?.toDouble() ?: 0.0)
    }
}

/**
 * Writes the one goal a plan is allowed.
 *
 * Saving a goal retires whichever one was active, in the same transaction, for
 * the reason in `GoalDao.upsertAsOnlyActive`. Refuses a goal that goes nowhere:
 * a start and a target that are equal on a NUMBER goal is a typo, and it would
 * produce a progress bar that is either empty or full forever.
 */
class SaveGoalUseCase @Inject constructor(
    private val plans: PlanRepository,
    private val goals: GoalRepository,
    private val dispatchers: AppDispatchers,
) {

    suspend operator fun invoke(goal: Goal): Outcome<Long, DataError> = withContext(dispatchers.io) {
        if (goal.targetDate <= goal.startDate || !goal.isWellFormed) {
            return@withContext Outcome.Failure(DataError.ConstraintViolation)
        }

        val planId = goal.planId.takeIf { it > 0 }
            ?: plans.observeActive().first()?.id
            ?: return@withContext Outcome.Failure(DataError.NotFound)

        goals.upsert(goal.copy(planId = planId, isActive = true))
    }
}

/** Retires the active goal. Its history stays, because it happened. */
class RetireGoalUseCase @Inject constructor(
    private val goals: GoalRepository,
    private val dispatchers: AppDispatchers,
) {

    suspend operator fun invoke(goalId: Long): Outcome<Unit, DataError> =
        withContext(dispatchers.io) { goals.deactivate(goalId) }
}

/**
 * Marks a week as not counting, or puts it back.
 *
 * Illness and a fortnight away should not permanently bend a projection the
 * user never agreed to. The days are excluded from the rate, never deleted:
 * what happened still happened, it just stops being evidence of a trend.
 */
class SetWeekCountedUseCase @Inject constructor(
    private val goals: GoalRepository,
    private val dispatchers: AppDispatchers,
) {

    suspend operator fun invoke(goalId: Long, weekStart: LocalDate, counted: Boolean): Outcome<Unit, DataError> =
        withContext(dispatchers.io) {
            goals.setWeekCounted(goalId, weekStart, counted)
        }
}
