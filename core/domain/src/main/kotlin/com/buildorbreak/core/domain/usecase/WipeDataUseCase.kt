package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.common.time.TimeProvider
import com.buildorbreak.core.domain.error.DomainError.DataError
import com.buildorbreak.core.domain.gateway.AlarmGateway
import com.buildorbreak.core.domain.gateway.WidgetGateway
import com.buildorbreak.core.domain.repository.OccurrenceRepository
import com.buildorbreak.core.domain.repository.ResetRepository
import javax.inject.Inject
import kotlinx.coroutines.withContext

/**
 * Delete everything.
 *
 * Alarms are cancelled first. They live in `AlarmManager`, not in the database,
 * and a wipe that left them in place would have the phone ringing at 06:40 for
 * a plan that no longer exists, with a receiver that cannot find the row.
 *
 * Cancelled by row rather than by sweeping a range of request codes. Alarms
 * are only ever set for the day being rescheduled, so the rows around today
 * are the complete list, and a sweep with a ceiling is a list that stops
 * being complete the day the ceiling is passed.
 */
class WipeDataUseCase @Inject constructor(
    private val reset: ResetRepository,
    private val occurrences: OccurrenceRepository,
    private val alarms: AlarmGateway,
    private val widget: WidgetGateway,
    private val time: TimeProvider,
    private val dispatchers: AppDispatchers,
) {

    suspend operator fun invoke(): Outcome<Unit, DataError> = withContext(dispatchers.io) {
        val today = time.today()
        occurrences.between(today.minusDays(1), today.plusDays(1)).forEach { alarms.cancel(it.id) }

        val wiped = reset.wipeEverything()

        widget.refresh()

        wiped
    }
}
