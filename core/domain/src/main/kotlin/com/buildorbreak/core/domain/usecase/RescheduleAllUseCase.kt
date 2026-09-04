package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.time.TimeProvider
import com.buildorbreak.core.domain.gateway.AlarmGateway
import com.buildorbreak.core.domain.repository.OccurrenceRepository
import com.buildorbreak.core.model.enums.Salience
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

        val rows = occurrences.observeForDate(date).first().associateBy { it.itemId to it.sequenceInDay }
        var scheduled = 0

        schedulable(day).forEach { entry ->
            val row = rows[entry.item.id to entry.sequenceInDay] ?: return@forEach

            // Settled means done, skipped or missed. Rescheduling any of those
            // would ring for something the user has already dealt with, which is
            // the fastest way to teach somebody to mute an app.
            if (row.isSettled) {
                alarms.cancel(row.id)
            } else {
                alarms.schedule(row, entry.item)
                scheduled++
            }
        }

        RescheduleReport.Done(scheduled = scheduled, tier = alarms.currentTier().name)
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
