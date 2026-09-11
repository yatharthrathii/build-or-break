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
import com.buildorbreak.core.model.execution.Occurrence
import com.buildorbreak.core.model.execution.SkipReason
import java.time.Duration as JavaDuration
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
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
    private val time: TimeProvider,
    private val dispatchers: AppDispatchers,
) {

    suspend operator fun invoke(occurrenceId: Long, by: Duration): Outcome<Unit, DataError> =
        withContext(dispatchers.io) {
            val row = occurrences.byId(occurrenceId) ?: return@withContext Outcome.Failure(DataError.NotFound)
            val shifted = occurrences.shift(occurrenceId, snoozeShift(row, by, time.localNow()))

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
 * How far a row has to move for "ten more minutes" to mean ten minutes from now.
 *
 * Measured from where the step is rather than from the clock, a snooze on an
 * alarm that fired late or rang for a while lands in the past: the step is
 * dropped by the rescheduling pass as already gone, the notification has been
 * taken down, and the user who asked for ten more minutes gets nothing at all.
 * So the time already lost is added to the shift, and a step that is not yet
 * due moves by exactly what was asked.
 */
internal fun snoozeShift(row: Occurrence, by: Duration, now: LocalDateTime): Duration {
    val late = JavaDuration.between(row.effectiveAt, now).toMinutes().coerceAtLeast(0)

    return by + late.minutes
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

/**
 * Takes back the last thing that was settled.
 *
 * The tap that needs this is the one nobody plans for: Done pressed on the row
 * above the one meant, on a phone held in one hand on a bus. Without an undo the
 * only way out is the editor, and the honest user ends up with a day that says
 * they did something they did not. A history that quietly drifts from the truth
 * is worse than no history, because every figure in the app is built on it.
 *
 * The reason goes with the skip. A reason left attached to a step that was
 * never skipped would be counted by the weekly review, and next Sunday's report
 * would explain something that did not happen.
 *
 * Nothing is re notified. The reschedule pass decides whether the step still has
 * a future, and for one whose time has passed the answer is no, which is right:
 * putting the row back is not the same as pretending the morning is still ahead.
 */
class UndoSettleUseCase @Inject constructor(
    private val occurrences: OccurrenceRepository,
    private val measurements: MeasurementRepository,
    private val reschedule: RescheduleAllUseCase,
    private val widget: WidgetGateway,
    private val dispatchers: AppDispatchers,
) {

    suspend operator fun invoke(occurrenceId: Long): Outcome<Unit, DataError> = withContext(dispatchers.io) {
        val restored = occurrences.unsettle(occurrenceId)

        measurements.clearSkipReason(occurrenceId)
        // The number goes with the settle it was logged against. Left behind,
        // sixty minutes of study would keep counting toward a duration goal
        // for a step that was later closed as missed.
        measurements.clearMeasurementFor(occurrenceId)
        reschedule()
        widget.refresh()

        restored
    }
}
