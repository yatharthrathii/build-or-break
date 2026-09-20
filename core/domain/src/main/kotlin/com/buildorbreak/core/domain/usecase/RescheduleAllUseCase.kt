package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.common.time.TimeProvider
import com.buildorbreak.core.domain.gateway.AlarmGateway
import com.buildorbreak.core.domain.repository.OccurrenceRepository
import com.buildorbreak.core.model.enums.Salience
import com.buildorbreak.core.model.execution.Occurrence
import com.buildorbreak.core.model.resolved.ResolvedDay
import com.buildorbreak.core.model.resolved.ResolvedEntry
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Makes the alarms that exist match the alarms that should exist.
 *
 * architecture.md section 6.4. This runs on app open, on boot, on locked boot,
 * on package replaced, on timezone change, on time change, on date change, on
 * any completion, on any snooze, on any plan edit and on the daily job. That is
 * often, and sometimes twice in a second.
 *
 * **Running it twice in a row must produce no change and no duplicate alarms.**
 * Everything below exists to make that true:
 *
 * 1. The day is resolved rather than read, so the desired set is derived from
 *    the plan every time rather than from whatever was scheduled last
 * 2. Occurrences are materialised with an insert that ignores conflicts, so a
 *    second pass does not create a second set of rows
 * 3. Alarms are keyed by occurrence id, so scheduling the same occurrence twice
 *    replaces one alarm rather than adding another
 *
 * The one thing it deliberately does not do is cancel everything first.
 * Cancelling and rescheduling leaves a window, however short, in which an alarm
 * due in that instant does not exist, and this runs often enough for that to
 * eventually land on somebody's six in the morning.
 */
class RescheduleAllUseCase @Inject constructor(
    private val observeToday: ObserveTodayUseCase,
    private val occurrences: OccurrenceRepository,
    private val alarms: AlarmGateway,
    private val time: TimeProvider,
    private val dispatchers: AppDispatchers,
) {

    suspend operator fun invoke(date: LocalDate = time.today()): RescheduleReport = withContext(dispatchers.io) {
        val day = observeToday(date).first() ?: return@withContext RescheduleReport.NoPlan

        occurrences.materialise(day.entries, date)

        val rows = occurrences.observeForDate(date).first()
        dropOrphans(rows, day)

        val byKey = rows.associateBy { it.itemId to it.sequenceInDay }
        var scheduled = 0

        schedulable(day).forEach { entry ->
            val stored = byKey[entry.item.id to entry.sequenceInDay] ?: return@forEach

            // Settled means done, skipped or missed. Rescheduling any of those
            // would ring for something the user has already dealt with, which is
            // the fastest way to teach somebody to mute an app.
            if (stored.isSettled) {
                alarms.cancel(stored.id)
                return@forEach
            }

            // The entry's salience, not the item's. A step inside a group
            // takes the group's loudness, and every step after the first
            // one in that group runs silent; see `ResolvedEntry.salience`.
            // Handing the item's own salience here is what made the group
            // setting look like it did nothing.
            val set = alarms.schedule(reconciled(stored, entry), entry.item.copy(salience = entry.salience))
            if (set is Outcome.Success) scheduled++
        }

        RescheduleReport.Done(scheduled = scheduled, tier = alarms.currentTier().name)
    }

    /**
     * The row is brought to the entry, never the other way round.
     *
     * The plan is the truth and the row is a copy taken when the step was
     * first seen. A day shifted ninety minutes, an item moved to a new time, a
     * parent that happened late: all of them move the entry and none of them
     * used to move the row, and the alarm was set from the row. The screen
     * said 19:30 and the phone rang at 18:00.
     *
     * The snooze is the row's own and is kept out of the planned time, so a
     * snoozed step stays exactly as far past its plan as the user put it.
     */
    private suspend fun reconciled(stored: Occurrence, entry: ResolvedEntry): Occurrence {
        val planned = entry.at.minusMinutes(stored.shiftMinutes.toLong())
        if (planned == stored.plannedAt) return stored

        occurrences.replan(stored.id, planned)

        return stored.copy(plannedAt = planned)
    }

    /**
     * Open rows the resolved day has no entry for are cancelled and removed.
     *
     * A template switched at seven, an item archived at ten, a weekday taken
     * off a step: every one leaves rows behind for steps that are no longer on
     * today. Their alarms would still fire, the receiver would still find the
     * row, and at midnight each would be settled as missed against a day that
     * never asked for it.
     */
    private suspend fun dropOrphans(rows: List<Occurrence>, day: ResolvedDay) {
        val expected = day.entries
            .filter { it.salience != Salience.TIMELINE }
            .map { it.item.id to it.sequenceInDay }
            .toSet()

        val orphans = rows.filter { !it.isSettled && (it.itemId to it.sequenceInDay) !in expected }
        if (orphans.isEmpty()) return

        orphans.forEach { alarms.cancel(it.id) }
        occurrences.discard(orphans.map { it.id })
    }

    /**
     * `TIMELINE` items are never handed to the scheduler at all, and an item
     * whose moment has already passed is left to the daily close rather than
     * scheduled into the past.
     */
    private fun schedulable(day: ResolvedDay): List<ResolvedEntry> {
        val now = time.localNow()

        return day.entries.filter { it.salience != Salience.TIMELINE && it.at >= now }
    }
}

/** What the pass did, so a caller can log one line rather than guess. */
sealed interface RescheduleReport {
    data object NoPlan : RescheduleReport

    data class Done(val scheduled: Int, val tier: String) : RescheduleReport
}
