package com.buildorbreak.scheduler.alarm

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService
import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.domain.error.DomainError.AlarmError
import com.buildorbreak.core.domain.gateway.AlarmGateway
import com.buildorbreak.core.domain.repository.DeliveryAuditRepository
import com.buildorbreak.core.model.audit.DeliveryAudit
import com.buildorbreak.core.model.enums.DeliveryTier
import com.buildorbreak.core.model.enums.Salience
import com.buildorbreak.core.model.execution.Occurrence
import com.buildorbreak.core.model.plan.Item
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.withContext

/** How wide a window an inexact alarm is given. Doze may still widen it further. */
private const val INEXACT_WINDOW_MILLIS = 10 * 60 * 1000L

/**
 * Sets alarms, at the strongest strength the phone currently allows.
 *
 * The tier decides which `AlarmManager` call is used, and the three are not
 * interchangeable:
 *
 * - `setAlarmClock` is the only one Android treats as a real alarm. It survives
 *   Doze, shows the alarm icon in the status bar, and is what a user setting an
 *   alarm expects. It is also the one most likely to be granted, because the
 *   platform recognises the use case
 * - `setExactAndAllowWhileIdle` fires at the right minute but is rate limited in
 *   Doze and shows nothing in the status bar. Right for a reminder, wrong for
 *   something meant to wake somebody
 * - `setWindow` is what is left when exact alarms are denied. It says plainly
 *   that the time is approximate rather than pretending otherwise
 *
 * Every scheduled alarm writes an audit row. That row is how the reliability
 * claim in the README becomes a measured number rather than a hope, and it has
 * to be written here because this is the only place that knows both the intended
 * time and the tier it was set at.
 */
class AlarmGatewayImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val tiers: TierDetector,
    private val audits: DeliveryAuditRepository,
    private val dispatchers: AppDispatchers,
) : AlarmGateway {

    private val alarms: AlarmManager?
        get() = context.getSystemService()

    override fun currentTier(): DeliveryTier = tiers.currentTier()

    override suspend fun schedule(occurrence: Occurrence, item: Item): Outcome<Unit, AlarmError> =
        withContext(dispatchers.io) {
            val manager = alarms ?: return@withContext Outcome.Failure(AlarmError.ExactAlarmDenied)
            val tier = currentTier()

            if (tier == DeliveryTier.IN_APP_ONLY) {
                return@withContext Outcome.Failure(AlarmError.NotificationsDenied)
            }

            val at = occurrence.effectiveAt.atZone(ZoneId.systemDefault()).toInstant()
            val pending = AlarmScheduling.pendingIntent(context, occurrence.id, item.id)
                ?: return@withContext Outcome.Failure(AlarmError.TooManyScheduled)

            // The permission can be revoked between the tier check and this call.
            // It is a narrow window and it is real, and the honest answer is to
            // report the denial rather than to crash inside a broadcast receiver.
            @Suppress("SwallowedException")
            try {
                setAlarm(manager, tier, item.salience, at.toEpochMilli(), pending)
            } catch (denied: SecurityException) {
                return@withContext Outcome.Failure(AlarmError.ExactAlarmDenied)
            }

            audits.recordScheduled(auditFor(occurrence, at, tier))

            Outcome.Success(Unit)
        }

    override suspend fun cancel(occurrenceId: Long) = withContext(dispatchers.io) {
        // Rebuilt rather than remembered. The intent is reconstructed from the
        // occurrence id alone, which is why this still works after a reboot with
        // nothing kept in memory.
        AlarmScheduling.pendingIntent(context, occurrenceId, itemId = 0, create = false)
            ?.let { alarms?.cancel(it) }

        Unit
    }

    /**
     * Cancels a range of request codes rather than a list of known alarms.
     *
     * `AlarmManager` cannot be asked what it holds, so there is no list to walk.
     * Used only when the plan is being torn down, where cancelling a code that
     * was never set is a no op and missing one that was is not.
     */
    override suspend fun cancelAll() = withContext(dispatchers.io) {
        for (occurrenceId in 0L until CANCEL_SWEEP) {
            cancel(occurrenceId)
        }
    }

    private fun setAlarm(
        manager: AlarmManager,
        tier: DeliveryTier,
        salience: Salience,
        atMillis: Long,
        pending: android.app.PendingIntent,
    ) {
        val exact = tier == DeliveryTier.FULL_SCREEN_ALARM || tier == DeliveryTier.EXACT_HEADS_UP

        when {
            exact && salience == Salience.ALARM ->
                manager.setAlarmClock(AlarmManager.AlarmClockInfo(atMillis, pending), pending)

            exact ->
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)

            else ->
                manager.setWindow(AlarmManager.RTC_WAKEUP, atMillis, INEXACT_WINDOW_MILLIS, pending)
        }
    }

    private fun auditFor(occurrence: Occurrence, at: Instant, tier: DeliveryTier) = DeliveryAudit(
        id = 0,
        occurrenceId = occurrence.id,
        scheduledFor = at,
        firedAt = null,
        tier = tier,
        deviceModel = Build.MODEL,
        manufacturer = Build.MANUFACTURER,
        sdkInt = Build.VERSION.SDK_INT,
        wasDeviceIdle = context.getSystemService<android.os.PowerManager>()?.isDeviceIdleMode ?: false,
        latencySeconds = null,
    )

    private companion object {
        /**
         * How many request codes a teardown sweeps. Occurrence ids start at one
         * and a single phone will not reach this in a lifetime of use, so a plan
         * being deleted leaves nothing behind.
         */
        const val CANCEL_SWEEP = 10_000L
    }
}
