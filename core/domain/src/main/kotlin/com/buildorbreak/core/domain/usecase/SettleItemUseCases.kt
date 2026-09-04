package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.common.time.TimeProvider
import com.buildorbreak.core.domain.error.DomainError.DataError
import com.buildorbreak.core.domain.gateway.AlarmGateway
import com.buildorbreak.core.domain.gateway.NotificationGateway
import com.buildorbreak.core.domain.gateway.WidgetGateway
import com.buildorbreak.core.domain.repository.MeasurementRepository
import com.buildorbreak.core.domain.repository.OccurrenceRepository
import com.buildorbreak.core.model.enums.OccurrenceState
import com.buildorbreak.core.model.execution.SkipReason
import javax.inject.Inject
import kotlin.time.Duration
import kotlinx.coroutines.withContext

/**
 * Marks something done and puts the rest of the day right.
 *
 * Four steps, and the order matters. Settling first means the row is correct
 * even if the process is killed straight afterwards, which on a phone with an
 * aggressive battery manager is a real possibility rather than a hypothetical.
 * Everything after it is recovery: the reschedule pass is idempotent and will
 * finish the job on the next launch if this call does not.
 *
 * **The activity is never launched for this.** appflow.md requires eighty
 * percent of interactions to complete without opening the app, which is what
 * forces this to be callable from a `BroadcastReceiver` rather than only from a
 * ViewModel. That requirement is the main reason the use case layer exists.
 */
class CompleteItemUseCase @Inject constructor(
    private val occurrences: OccurrenceRepository,
    private val reschedule: RescheduleAllUseCase,
    private val notifications: NotificationGateway,
    private val alarms: AlarmGateway,
    private val widget: WidgetGateway,
    private val time: TimeProvider,
    private val dispatchers: AppDispatchers,
) {

    /** [minimum] records that the smaller version was the one that happened. */
    suspend operator fun invoke(occurrenceId: Long, minimum: Boolean = false): Outcome<Unit, DataError> =
        withContext(dispatchers.io) {
            val state = if (minimum) OccurrenceState.DONE_MINIMUM else OccurrenceState.DONE
            val settled = occurrences.settle(occurrenceId, state, time.now())

            alarms.cancel(occurrenceId)
            notifications.dismiss(occurrenceId)
            // Downstream RELATIVE items now hang off a real completion time
            // rather than a planned one, so the rest of the day has moved.
            reschedule()
            widget.refresh()

            settled
        }
}

/**
 * Moves one thing later, and everything that hangs off it with it.
 *
 * The reschedule that follows is not a tidy up. A snooze changes the resolved
 * time of every `RELATIVE` child, so the alarms already set for them are now
 * wrong, and leaving them would ring for a step whose parent has not happened.
 */
class SnoozeItemUseCase @Inject constructor(
    private val occurrences: OccurrenceRepository,
    private val reschedule: RescheduleAllUseCase,
    private val notifications: NotificationGateway,
    private val widget: WidgetGateway,
    private val dispatchers: AppDispatchers,
) {

    suspend operator fun invoke(occurrenceId: Long, by: Duration): Outcome<Unit, DataError> =
        withContext(dispatchers.io) {
            val shifted = occurrences.shift(occurrenceId, by)

            notifications.dismiss(occurrenceId)
            reschedule()
            widget.refresh()

            when (shifted) {
                is Outcome.Success -> Outcome.Success(Unit)
                is Outcome.Failure -> shifted
            }
        }
}

/**
 * Settles something as not happening, with an optional reason.
 *
 * The reason is written after the state, and a failure to write it does not fail
 * the skip. Asking somebody to justify themselves at the moment they are already
 * having a bad day is how the data stops arriving at all, so the reason is always
 * optional and never blocks the thing it describes.
 */
class SkipItemUseCase @Inject constructor(
    private val occurrences: OccurrenceRepository,
    private val measurements: MeasurementRepository,
    private val reschedule: RescheduleAllUseCase,
    private val notifications: NotificationGateway,
    private val alarms: AlarmGateway,
    private val widget: WidgetGateway,
    private val time: TimeProvider,
    private val dispatchers: AppDispatchers,
) {

    suspend operator fun invoke(occurrenceId: Long, reason: SkipReason? = null): Outcome<Unit, DataError> =
        withContext(dispatchers.io) {
            val settled = occurrences.settle(occurrenceId, OccurrenceState.SKIPPED, time.now())

            reason?.let { measurements.recordSkipReason(it) }

            alarms.cancel(occurrenceId)
            notifications.dismiss(occurrenceId)
            // A skipped parent leaves its children on their planned time rather
            // than collapsing them, but they still have to be rescheduled: the
            // alarms currently set were built from a day that no longer applies.
            reschedule()
            widget.refresh()

            settled
        }
}
