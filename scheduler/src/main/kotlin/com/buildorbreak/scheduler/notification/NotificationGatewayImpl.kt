package com.buildorbreak.scheduler.notification

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.domain.gateway.NotificationGateway
import com.buildorbreak.core.model.enums.Milestone
import com.buildorbreak.core.model.execution.Occurrence
import com.buildorbreak.core.model.plan.Item
import com.buildorbreak.core.model.resolved.CascadePreview
import com.buildorbreak.scheduler.R
import com.buildorbreak.scheduler.alarm.AlarmRingerService
import com.buildorbreak.scheduler.alarm.AlarmScheduling
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.withContext

/** Milestones share one id, so a second one replaces the first rather than stacking. */
private const val MILESTONE_NOTIFICATION_ID = 1

/**
 * Puts a step in front of the user, as loudly as the plan asked and the phone
 * allows.
 *
 * The gateway does no deciding. Which channel, which buttons and whether the
 * notification takes over the screen all follow from the item's salience, which
 * the domain set. That separation is what lets the whole scheduling flow be
 * tested without a device: substitute a fake and assert what it was asked to do.
 */
class NotificationGatewayImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val look: AlarmNotification,
    private val dispatchers: AppDispatchers,
) : NotificationGateway {

    private val manager: NotificationManager?
        get() = context.getSystemService()

    override suspend fun show(occurrence: Occurrence, item: Item, preview: CascadePreview?) =
        withContext(dispatchers.io) {
            Channels.ensureCreated(context)
            post(AlarmScheduling.requestCode(occurrence.id), look.due(occurrence, item, preview))
        }

    /**
     * Takes the notification down and stops the sound with it.
     *
     * Every way of settling a step calls this: the buttons on the notification,
     * the alarm screen, and a tap in the app. Stopping the ringer here rather
     * than at each of those is what makes it impossible to add a fourth way and
     * leave the phone ringing.
     */
    override suspend fun dismiss(occurrenceId: Long) = withContext(dispatchers.io) {
        manager?.cancel(AlarmScheduling.requestCode(occurrenceId))
        AlarmRingerService.stop(context, occurrenceId)

        Unit
    }

    override suspend fun showMilestone(milestone: Milestone) = withContext(dispatchers.io) {
        Channels.ensureCreated(context)
        post(
            MILESTONE_NOTIFICATION_ID,
            NotificationCompat.Builder(context, Channels.QUIET_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(context.getString(R.string.milestone_title))
                .setContentText(milestone.name)
                .setAutoCancel(true)
                .build(),
        )
    }

    /**
     * The guard and the post, in one place.
     *
     * The lint suppression is narrow and deliberate. Lint wants to see a literal
     * `checkSelfPermission(POST_NOTIFICATIONS)` next to the call, and that check
     * cannot be written correctly here: the permission does not exist before
     * Android 13, so it would report denied on every older phone.
     * `areNotificationsEnabled` is the version aware answer to the same question.
     */
    @SuppressLint("MissingPermission")
    private fun post(id: Int, notification: Notification) {
        val manager = NotificationManagerCompat.from(context)

        // Covers both the runtime permission from Android 13 and the plain
        // notification switch on every version before it.
        if (!manager.areNotificationsEnabled()) return

        manager.notify(id, notification)
    }

    /**
     * One question, answered correctly on every version.
     *
     * `areNotificationsEnabled` already accounts for the runtime permission from
     * Android 13 and for the notification switch below it. Checking
     * `POST_NOTIFICATIONS` as well would be wrong rather than merely redundant:
     * the permission does not exist before Android 13, so asking for it on an
     * Android 8 phone returns denied and the app would decide it could not post
     * anything at all.
     */
    override fun canPostNotifications(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    override fun canUseFullScreenIntent(): Boolean = look.canUseFullScreenIntent()
}
