package com.buildorbreak.scheduler.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.time.TimeProvider
import com.buildorbreak.core.domain.gateway.NotificationGateway
import com.buildorbreak.core.domain.repository.DeliveryAuditRepository
import com.buildorbreak.core.domain.repository.ItemRepository
import com.buildorbreak.core.domain.repository.OccurrenceRepository
import com.buildorbreak.core.model.enums.Salience
import com.buildorbreak.core.model.execution.Occurrence
import com.buildorbreak.core.model.plan.Item
import com.buildorbreak.scheduler.receiver.finishAfter
import dagger.hilt.android.AndroidEntryPoint
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * The alarm went off.
 *
 * A broadcast receiver gets roughly ten seconds, and on a phone that has been
 * asleep all night it is competing with everything else that woke at the same
 * moment. So this does the least possible: record that it fired, read two rows,
 * post the notification, stop.
 *
 * Recording the fire time comes first, before the notification. That row is the
 * reliability figure the README promises to publish, and it has to be true even
 * when the work after it is killed. A missing notification is a bad morning; a
 * missing audit row is a number that quietly flatters the app.
 *
 * The snooze consequence preview is deliberately not computed here. Working out
 * what a snooze would cost means resolving the whole day, which is not ten
 * second work on a cold process. The notification offers snooze without the
 * consequence text and the app shows the full preview when it is opened.
 */
@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var occurrences: OccurrenceRepository

    @Inject lateinit var items: ItemRepository

    @Inject lateinit var notifications: NotificationGateway

    @Inject lateinit var audits: DeliveryAuditRepository

    @Inject lateinit var time: TimeProvider

    @Inject lateinit var dispatchers: AppDispatchers

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduling.ACTION_FIRE) return

        val occurrenceId = intent.getLongExtra(AlarmScheduling.EXTRA_OCCURRENCE_ID, NO_ID)
        if (occurrenceId == NO_ID) return

        val itemId = intent.getLongExtra(AlarmScheduling.EXTRA_ITEM_ID, NO_ID)

        goAsync().finishAfter(dispatchers.io) {
            audits.recordFired(occurrenceId, time.now())

            val occurrence = occurrences.observeForDate(time.today()).first()
                .firstOrNull { it.id == occurrenceId }
                ?: return@finishAfter

            // Already dealt with. This happens when a completion and the alarm
            // race, which they do whenever somebody finishes something a minute
            // early, and ringing for it would be the app arguing with the user.
            if (occurrence.isSettled) return@finishAfter

            val item = items.byId(itemId) ?: return@finishAfter

            // An alarm is handed to the ringer, which holds the alarm stream
            // open and posts its own notification. Anything quieter is just a
            // notification, and starting a foreground service for one would be
            // a battery complaint waiting to happen.
            if (item.salience == Salience.ALARM) {
                AlarmRingerService.start(context, ringIntent(context, occurrence, item))
                takeOverTheScreen(context, occurrence, item)
            } else {
                notifications.show(occurrence, item, preview = null)
            }
        }
    }

    /**
     * Puts the alarm screen in front, rather than hoping the notification will.
     *
     * A full screen intent only takes over the screen when the phone is idle or
     * locked. When it is in the user's hand the platform shows a heads up
     * banner instead, which is the right call for a reminder and the wrong one
     * for the thing that is supposed to stop what you are doing. So the alarm
     * is started directly as well.
     *
     * This is allowed here and almost nowhere else: an app that has just been
     * woken by its own exact alarm is one of the few cases the platform still
     * lets start an activity from the background. If a vendor blocks it anyway
     * the notification is still posted and still has every button on it, which
     * is why the failure is swallowed rather than reported.
     */
    private fun takeOverTheScreen(context: Context, occurrence: Occurrence, item: Item) {
        @Suppress("SwallowedException", "TooGenericExceptionCaught")
        try {
            context.startActivity(
                Intent(AlarmScheduling.ACTION_ALARM_SCREEN)
                    .setPackage(context.packageName)
                    .putExtra(AlarmScheduling.EXTRA_OCCURRENCE_ID, occurrence.id)
                    .putExtra(AlarmScheduling.EXTRA_ITEM_ID, item.id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
        } catch (blocked: Exception) {
            Unit
        }
    }

    /** Everything the ringer needs, so it never has to touch the database. */
    private fun ringIntent(context: Context, occurrence: Occurrence, item: Item) =
        Intent(AlarmRingerService.ACTION_RING)
            .putExtra(AlarmRingerService.EXTRA_OCCURRENCE_ID, occurrence.id)
            .putExtra(AlarmRingerService.EXTRA_ITEM_ID, item.id)
            .putExtra(AlarmRingerService.EXTRA_TITLE, item.title)
            .putExtra(AlarmRingerService.EXTRA_DETAIL, item.detail)
            .putExtra(AlarmRingerService.EXTRA_TIME, occurrence.effectiveAt.format(clock(context)))
            .putExtra(AlarmRingerService.EXTRA_HAS_MINIMUM, item.hasMinimum)

    private fun clock(context: Context): DateTimeFormatter = if (DateFormat.is24HourFormat(context)) {
        DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    } else {
        DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    }

    private companion object {
        const val NO_ID = -1L
    }
}
