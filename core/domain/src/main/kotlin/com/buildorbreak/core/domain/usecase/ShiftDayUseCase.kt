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
    private val reschedule: RescheduleAllUseCase,
    private val widget: WidgetGateway,
    private val time: TimeProvider,
    private val dispatchers: AppDispatchers,
) {

    suspend operator fun invoke(shift: Duration, date: LocalDate = time.today()): Outcome<Unit, DataError> =
        withContext(dispatchers.io) {
            val written = dayLogs.setShift(date, shift)

            reschedule(date)
            widget.refresh()

            written
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
        val written = dayLogs.upsert(
            DayLog(
                date = date,
                planId = planId,
                templateId = templateId,
                dayShiftMinutes = 0,
                mode = mode,
                chosenAt = time.now(),
            ),
        )

        reschedule(date)
        widget.refresh()

        written
    }
}
