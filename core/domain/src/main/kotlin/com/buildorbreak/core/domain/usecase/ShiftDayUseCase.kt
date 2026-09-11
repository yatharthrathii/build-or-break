package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.common.time.TimeProvider
import com.buildorbreak.core.domain.error.DomainError.DataError
import com.buildorbreak.core.domain.gateway.WidgetGateway
import com.buildorbreak.core.domain.repository.DayLogRepository
import com.buildorbreak.core.model.enums.DayMode
import com.buildorbreak.core.model.execution.DayLog
import java.time.LocalDate
import javax.inject.Inject
import kotlin.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Woke up ninety minutes late. Move the day.
 *
 * The shift is stored on the day rather than applied to the items, which is what
 * makes it undoable and what keeps tomorrow untouched. Pinned items ignore it
 * inside the resolver, so a booked class stays where it is without this having
 * to know anything about which items those are.
 */
class ShiftDayUseCase @Inject constructor(
    private val dayLogs: DayLogRepository,
    private val today: ObserveTodayUseCase,
    private val reschedule: RescheduleAllUseCase,
    private val widget: WidgetGateway,
    private val time: TimeProvider,
    private val dispatchers: AppDispatchers,
) {

    suspend operator fun invoke(shift: Duration, date: LocalDate = time.today()): Outcome<Unit, DataError> =
        withContext(dispatchers.io) {
            val existing = dayLogs.observe(date).first()
            val written = if (existing == null) startDay(date, shift) else dayLogs.setShift(date, shift)

            reschedule(date)
            widget.refresh()

            written
        }

    /**
     * The first thing to happen to a day has to write the row it is kept in.
     *
     * Most days never get one: the template comes from the weekday and there is
     * nothing to store. So the first "running late" of the morning has no row to
     * update, and an update that matches nothing succeeds while changing
     * nothing, which is how a button ends up moving no steps at all.
     *
     * The template written is the one the day was already running, read back
     * from the resolver rather than guessed, so recording a shift cannot
     * quietly change which routine is on.
     */
    private suspend fun startDay(date: LocalDate, shift: Duration): Outcome<Unit, DataError> {
        val template = today.inputFor(date)?.template ?: return Outcome.Failure(DataError.NotFound)
        val minutes = shift.inWholeMinutes.toInt()

        return dayLogs.upsert(
            DayLog(
                date = date,
                planId = template.planId,
                templateId = template.id,
                dayShiftMinutes = minutes,
                mode = if (minutes == 0) DayMode.NORMAL else DayMode.SHIFTED,
                chosenAt = time.now(),
            ),
        )
    }
}

/**
 * One tap in the morning reshapes the whole timeline.
 *
 * Office day, working from home, rest day, sick day. This is the answer to the
 * loudest complaint in the category: a routine bound to fixed clock times forces
 * two routines when your day starts at six on Monday and eight on Saturday.
 *
 * Switching resets the shift. A day that was moved ninety minutes and is then
 * declared a rest day is a different day, and carrying the old offset into it
 * would silently move a template the user has only just chosen.
 */
class SwitchDayTemplateUseCase @Inject constructor(
    private val dayLogs: DayLogRepository,
    private val reschedule: RescheduleAllUseCase,
    private val widget: WidgetGateway,
    private val time: TimeProvider,
    private val dispatchers: AppDispatchers,
) {

    suspend operator fun invoke(
        planId: Long,
        templateId: Long,
        mode: DayMode = DayMode.NORMAL,
        date: LocalDate = time.today(),
    ): Outcome<Unit, DataError> = withContext(dispatchers.io) {
        // A different template is a different day and the shift goes with the
        // old one. The same template in a different mode is the same day: a
        // morning that started ninety minutes late and then turned into a
        // sick day is still ninety minutes late.
        val existing = dayLogs.observe(date).first()
        val shift = existing?.dayShiftMinutes?.takeIf { existing.templateId == templateId } ?: 0

        val written = dayLogs.upsert(
            DayLog(
                date = date,
                planId = planId,
                templateId = templateId,
                dayShiftMinutes = shift,
                mode = mode,
                chosenAt = time.now(),
            ),
        )

        reschedule(date)
        widget.refresh()

        written
    }
}
