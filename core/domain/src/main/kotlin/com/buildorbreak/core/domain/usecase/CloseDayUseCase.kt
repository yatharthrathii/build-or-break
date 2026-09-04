package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.time.TimeProvider
import com.buildorbreak.core.domain.gateway.NotificationGateway
import com.buildorbreak.core.domain.goal.DayQualityClassifier
import com.buildorbreak.core.domain.goal.ItemRun
import com.buildorbreak.core.domain.goal.MilestoneContext
import com.buildorbreak.core.domain.goal.MilestoneEvaluator
import com.buildorbreak.core.domain.repository.DayCloseRepository
import com.buildorbreak.core.domain.repository.MilestoneRepository
import com.buildorbreak.core.domain.repository.OccurrenceRepository
import com.buildorbreak.core.domain.repository.PlanRepository
import com.buildorbreak.core.model.enums.Milestone
import com.buildorbreak.core.model.enums.OccurrenceState
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
class CloseDayUseCase @Inject constructor(
    private val plans: PlanRepository,
    private val occurrences: OccurrenceRepository,
    private val closes: DayCloseRepository,
    private val milestones: MilestoneRepository,
    private val quality: DayQualityClassifier,
    private val evaluator: MilestoneEvaluator,
    private val notifications: NotificationGateway,
    private val time: TimeProvider,
    private val dispatchers: AppDispatchers,
) {

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
        val dates = date?.let(::listOf) ?: pendingDates()

        dates.map { day -> close(day, plan.id) }
    }

    private suspend fun pendingDates(): List<LocalDate> {
        val yesterday = time.today().minusDays(1)
        val lastClosed = closes.lastClosedDate() ?: yesterday.minusDays(1)
        val from = maxOf(lastClosed.plusDays(1), yesterday.minusDays(MAX_CATCH_UP_DAYS))

        return generateSequence(from) { it.plusDays(1) }.takeWhile { it <= yesterday }.toList()
    }

    private suspend fun close(date: LocalDate, planId: Long): DayClose {
        val settled = settleOpenOccurrences(date)
        val close = closeFrom(date, planId, settled)

        closes.upsert(close)
        awardMilestoneFor(close)

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

    private fun closeFrom(date: LocalDate, planId: Long, rows: List<Occurrence>): DayClose {
        val done = rows.count { it.state == OccurrenceState.DONE }
        val minimum = rows.count { it.state == OccurrenceState.DONE_MINIMUM }
        val missed = rows.count { it.state == OccurrenceState.MISSED || it.state == OccurrenceState.SKIPPED }

        return DayClose(
            date = date,
            planId = planId,
            itemsDone = done,
            itemsMinimum = minimum,
            itemsMissed = missed,
            itemsTotal = rows.size,
            quality = quality.classify(done, minimum, rows.size),
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
    private suspend fun awardMilestoneFor(close: DayClose) {
        val history = closes.observeRange(close.date.minusDays(HISTORY_DAYS), close.date.minusDays(1)).first()

        val earned = evaluator.evaluate(
            MilestoneContext(
                date = close.date,
                today = close,
                history = history,
                longestRun = longestRun(history, close),
                awarded = milestones.awarded(),
            ),
        ) ?: return

        milestones.award(
            MilestoneAward(milestone = earned, goalId = null, itemId = null, awardedOn = close.date, seenAt = null),
        )
        notifications.showMilestone(earned)
    }

    /**
     * How many days in a row have gone well, as a stand in for a per item run.
     *
     * A true per item run needs occurrence history for every item and is worth
     * the query once there are screens that show it. Until then this feeds
     * [Milestone.ITEM_THIRTY_DAY_RUN] with the closest honest number available
     * rather than leaving the milestone unreachable.
     */
    private fun longestRun(history: List<DayClose>, today: DayClose): ItemRun? {
        val run = (history + today).reversed().takeWhile { it.isFullDay }.count()

        return if (run == 0) null else ItemRun(itemId = 0, days = run)
    }
}
