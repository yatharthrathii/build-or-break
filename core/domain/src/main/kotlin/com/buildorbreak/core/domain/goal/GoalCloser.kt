package com.buildorbreak.core.domain.goal

import com.buildorbreak.core.domain.repository.DayCloseRepository
import com.buildorbreak.core.domain.repository.GoalRepository
import com.buildorbreak.core.domain.repository.ItemRepository
import com.buildorbreak.core.domain.repository.MeasurementRepository
import com.buildorbreak.core.domain.repository.OccurrenceRepository
import com.buildorbreak.core.model.enums.GoalKind
import com.buildorbreak.core.model.execution.Measurement
import com.buildorbreak.core.model.goal.Goal
import com.buildorbreak.core.model.goal.Reading
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * The five tables a goal row is built from, in one injectable bag.
 *
 * They always travel together and none is interesting on its own here.
 * Injecting them one by one gave this class a constructor nobody could read.
 */
class GoalSources @Inject constructor(
    val goals: GoalRepository,
    val items: ItemRepository,
    val measurements: MeasurementRepository,
    val occurrences: OccurrenceRepository,
    val closes: DayCloseRepository,
)

/** Which goal, and how far along it is after a day was closed. */
data class GoalCloseResult(val goalId: Long, val percent: Float)

/**
 * Writes one day of goal history, at the daily close.
 *
 * Separate from `CloseDayUseCase` because closing a day and advancing a goal
 * are two jobs that happen to share a moment. The day close must succeed even
 * when there is no goal, which is the ordinary case, and a goal must be
 * advanced by exactly the same arithmetic whether it is being caught up from a
 * week ago or written tonight.
 *
 * Returns how far along the goal is afterwards, because that is what decides
 * whether a goal milestone was earned, and the evaluator must not have to go
 * and read the row that was written a line earlier.
 */
class GoalCloser @Inject constructor(
    private val sources: GoalSources,
    private val writer: GoalProgressWriter,
    private val calculator: GoalCalculator,
) {

    /**
     * One result for each running goal the date falls inside. Usually one, often none.
     *
     * Every goal, because each keeps its own history and a day belongs to
     * all of them. They are independent: one goal being outside its window
     * says nothing about the other.
     */
    suspend fun close(planId: Long, date: LocalDate): List<GoalCloseResult> =
        sources.goals.observeAllActive(planId).first().mapNotNull { closeOne(it, date) }

    private suspend fun closeOne(goal: Goal, date: LocalDate): GoalCloseResult? {
        if (date < goal.startDate || date > goal.targetDate) return null

        val history = sources.goals.observeProgress(goal.id).first()
        val row = writer.rowFor(
            GoalDay(
                goal = goal,
                date = date,
                readings = readingsFor(goal, date),
                closes = sources.closes.observeRange(goal.startDate, date).first(),
                occurrences = occurrencesFor(goal, date),
                measurements = measurementsFor(goal),
                plannedMinutes = plannedMinutesFor(goal),
                previous = history.filter { it.date < date }.maxByOrNull { it.date },
                leftOutWeeks = sources.goals.observeLeftOutWeeks(goal.id).first(),
            ),
        )

        sources.goals.upsertProgress(row)

        return GoalCloseResult(goal.id, calculator.percentComplete(goal, row.currentFor(goal), date))
    }

    /**
     * Only a measured goal has a series. The others count what was done, which
     * is a different question and a different table.
     */
    private suspend fun readingsFor(goal: Goal, date: LocalDate): List<Reading> = if (goal.kind != GoalKind.NUMBER) {
        emptyList()
    } else {
        sources.measurements.readings(goal.valueKind, goal.startDate, date, goal.itemId)
    }

    private suspend fun occurrencesFor(goal: Goal, date: LocalDate) = goal.itemId?.let { itemId ->
        sources.occurrences.between(date, date).filter { it.itemId == itemId }
    }.orEmpty()

    private suspend fun measurementsFor(goal: Goal): List<Measurement> =
        goal.itemId?.let { sources.measurements.observeForItem(it).first() }.orEmpty()

    private suspend fun plannedMinutesFor(goal: Goal): Int =
        goal.itemId?.let { sources.items.byId(it)?.duration?.inWholeMinutes?.toInt() } ?: 0
}
