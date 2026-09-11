package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.time.TimeProvider
import com.buildorbreak.core.domain.gateway.NotificationGateway
import com.buildorbreak.core.domain.goal.DayQualityClassifier
import com.buildorbreak.core.domain.goal.GoalCloseResult
import com.buildorbreak.core.domain.goal.GoalCloser
import com.buildorbreak.core.domain.goal.ItemRuns
import com.buildorbreak.core.domain.goal.MilestoneContext
import com.buildorbreak.core.domain.goal.MilestoneEvaluator
import com.buildorbreak.core.domain.repository.DayCloseRepository
import com.buildorbreak.core.domain.repository.MilestoneRepository
import com.buildorbreak.core.domain.repository.OccurrenceRepository
import com.buildorbreak.core.domain.repository.PlanRepository
import com.buildorbreak.core.model.enums.OccurrenceState
import com.buildorbreak.core.model.enums.Salience
import com.buildorbreak.core.model.execution.Occurrence
import com.buildorbreak.core.model.goal.DayClose
import com.buildorbreak.core.model.goal.MilestoneAward
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** How far back the close looks when the app has not been opened for a while. */
private const val MAX_CATCH_UP_DAYS = 30L

/** The window the consistency figure and the run length are read from. */
private const val HISTORY_DAYS = 60L

/** Wide enough for the thirty day run, with room to prove it broke before that. */
private const val RUN_WINDOW_DAYS = 40L

/**
 * Ends a day and writes down how it went.
 *
 * architecture.md section 6.3. Runs at 00:05 from the daily job, and again on
 * launch for any day the job did not get to. A phone that was off overnight, or
 * a battery manager that stopped the worker, must not leave a permanent hole in
 * the history: every figure the app shows is built on these rows.
 *
 * The order is deliberate. Settling the open occurrences first means the counts
 * are right even if everything after it fails, and everything after it can be
 * recomputed from those rows on the next run.
 */
/**
 * The four tables a close reads and writes, in one injectable bag.
 *
 * They always travel together. Injecting them one by one alongside the four
 * services below gave this class a constructor nobody could read.
 */
class CloseSources @Inject constructor(
    val plans: PlanRepository,
    val occurrences: OccurrenceRepository,
    val closes: DayCloseRepository,
    val milestones: MilestoneRepository,
)

class CloseDayUseCase @Inject constructor(
    private val sources: CloseSources,
    private val observeToday: ObserveTodayUseCase,
    private val quality: DayQualityClassifier,
    private val evaluator: MilestoneEvaluator,
    private val goals: GoalCloser,
    private val notifications: NotificationGateway,
    private val time: TimeProvider,
    private val dispatchers: AppDispatchers,
) {

    private val plans get() = sources.plans
    private val occurrences get() = sources.occurrences
    private val closes get() = sources.closes
    private val milestones get() = sources.milestones

    /**
     * Closes [date], or every unclosed day up to yesterday when called with
     * nothing.
     *
     * Capped at thirty days. Somebody returning after three months does not need
     * ninety rows of zeroes written before their app opens, and a run of empty
     * days is not history worth keeping.
     */
    suspend operator fun invoke(date: LocalDate? = null): List<DayClose> = withContext(dispatchers.io) {
        val plan = plans.observeActive().first() ?: return@withContext emptyList()
        // Nothing before the plan existed. A fresh install closes the two days
        // before it was made, and with the planned steps now counted those
        // would be two missed days written against somebody who had no plan.
        val began = plan.createdAt.atZone(time.zone()).toLocalDate()
        val dates = (date?.let(::listOf) ?: pendingDates(began)).filter { !it.isBefore(began) }

        dates.map { day -> close(day, plan.id) }
    }

    /**
     * From the day after the last close, or from the day the plan began when
     * nothing has been closed yet. A first close that only looked at
     * yesterday left every earlier day of a new plan neither kept nor
     * missed, a hole at the start of the history that nothing ever filled.
     */
    private suspend fun pendingDates(began: LocalDate): List<LocalDate> {
        val yesterday = time.today().minusDays(1)
        val from = maxOf(closes.lastClosedDate()?.plusDays(1) ?: began, yesterday.minusDays(MAX_CATCH_UP_DAYS))

        return generateSequence(from) { it.plusDays(1) }.takeWhile { it <= yesterday }.toList()
    }

    private suspend fun close(date: LocalDate, planId: Long): DayClose {
        val settled = settleOpenOccurrences(date)
        val close = closeFrom(date, planId, settled)

        closes.upsert(close)

        // The goal row is written before the milestone is judged. Crossing
        // half way is one of the nine things that can earn one, and asking the
        // evaluator to decide from a row that has not been written yet would
        // mean every goal milestone fired a day late.
        val goal = goals.close(planId, date)

        awardMilestoneFor(close, goal)

        return close
    }

    /**
     * Anything still pending at the end of a day did not happen.
     *
     * Recorded rather than deleted. A missed step is data: the pattern detectors
     * and the weekly review are built entirely out of what was missed and when.
     */
    private suspend fun settleOpenOccurrences(date: LocalDate): List<Occurrence> {
        val rows = occurrences.observeForDate(date).first()
        val now = time.now()

        rows.filterNot { it.isSettled }.forEach { occurrences.settle(it.id, OccurrenceState.MISSED, now) }

        return occurrences.observeForDate(date).first()
    }

    /**
     * The total is what the plan asked for, not what happened to be written.
     *
     * Rows only exist for a day the app was opened on, or that the daily job
     * ran for. A phone left in a drawer from Monday to Friday has no rows for
     * any of those days, and a close that counted rows alone recorded five
     * days of nothing as five perfect days: the run grew, the thirty day
     * figure climbed, and a consistency goal advanced through a week in which
     * nothing was done. The resolver still knows what those days held.
     */
    private suspend fun closeFrom(date: LocalDate, planId: Long, rows: List<Occurrence>): DayClose {
        val done = rows.count { it.state == OccurrenceState.DONE }
        val minimum = rows.count { it.state == OccurrenceState.DONE_MINIMUM }
        val planned = observeToday(date).first()?.entries?.count { it.salience != Salience.TIMELINE } ?: 0
        val total = maxOf(rows.size, planned)
        val missed = total - done - minimum

        return DayClose(
            date = date,
            planId = planId,
            itemsDone = done,
            itemsMinimum = minimum,
            itemsMissed = missed,
            itemsTotal = total,
            quality = quality.classify(done, minimum, total),
            closedAt = time.now(),
        )
    }

    /**
     * At most one milestone, and only if the day earned it.
     *
     * `MilestoneEvaluator` returning null on a poor day is enforced in the
     * domain rather than on a screen, so nothing can accidentally congratulate
     * somebody on a day that went badly. This method simply respects that.
     */
    private suspend fun awardMilestoneFor(close: DayClose, goal: GoalCloseResult?) {
        val history = closes.observeRange(close.date.minusDays(HISTORY_DAYS), close.date.minusDays(1)).first()

        val earned = evaluator.evaluate(
            MilestoneContext(
                date = close.date,
                today = close,
                history = history,
                goalPercent = goal?.percent,
                goalId = goal?.goalId,
                longestRun = ItemRuns.longest(
                    occurrences.between(close.date.minusDays(RUN_WINDOW_DAYS), close.date),
                    close.date,
                ),
                awarded = milestones.awarded(),
            ),
        ) ?: return

        milestones.award(
            MilestoneAward(
                milestone = earned,
                goalId = goal?.goalId,
                itemId = null,
                awardedOn = close.date,
                seenAt = null,
            ),
        )
        notifications.showMilestone(earned)
    }
}
