package com.buildorbreak.scheduler.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.time.TimeProvider
import com.buildorbreak.core.domain.gateway.NotificationGateway
import com.buildorbreak.core.domain.repository.DeliveryAuditRepository
import com.buildorbreak.core.domain.repository.ItemRepository
import com.buildorbreak.core.domain.repository.OccurrenceRepository
import com.buildorbreak.scheduler.receiver.finishAfter
import dagger.hilt.android.AndroidEntryPoint
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

            notifications.show(occurrence, item, preview = null)
        }
    }

    private companion object {
        const val NO_ID = -1L
    }
}
