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
import com.buildorbreak.core.model.enums.Salience
import com.buildorbreak.core.model.execution.Occurrence
import com.buildorbreak.core.model.plan.Item
import com.buildorbreak.core.model.resolved.CascadePreview
import com.buildorbreak.scheduler.R
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
    private val dispatchers: AppDispatchers,
) : NotificationGateway {

    private val manager: NotificationManager?
        get() = context.getSystemService()

    override suspend fun show(occurrence: Occurrence, item: Item, preview: CascadePreview?) =
        withContext(dispatchers.io) {
            Channels.ensureCreated(context)
            post(AlarmScheduling.requestCode(occurrence.id), build(occurrence, item, preview))
        }

    override suspend fun dismiss(occurrenceId: Long) = withContext(dispatchers.io) {
        manager?.cancel(AlarmScheduling.requestCode(occurrenceId))

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

    override fun canUseFullScreenIntent(): Boolean =
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            true
        } else {
            manager?.canUseFullScreenIntent() ?: false
        }

    /**
     * The buttons are the feature.
     *
     * Done, the smaller version, snooze and skip all complete from the shade.
     * The smaller version only appears when one was declared in advance, which is
     * the entire point of declaring it: nobody having a bad day is in a state to
     * decide what a fair reduced version would be, so the decision is made when
     * the plan is written and offered when it is needed.
     *
     * The snooze consequence text is shown when a preview was handed in. It is
     * not computed here, because working out what a snooze costs means resolving
     * the whole day and this method runs inside a ten second broadcast budget.
     */
    private fun build(occurrence: Occurrence, item: Item, preview: CascadePreview?): Notification {
        val id = occurrence.id

        val builder = NotificationCompat.Builder(context, channelFor(item.salience))
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(item.title)
            .setContentText(item.detail ?: preview?.let(::consequenceText))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(priorityFor(item.salience))
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .addAction(0, context.getString(R.string.action_done), doneIntent(id))

        if (item.hasMinimum) {
            builder.addAction(0, context.getString(R.string.action_minimum), minimumIntent(id))
        }

        builder
            .addAction(0, context.getString(R.string.action_snooze), snoozeIntent(id))
            .addAction(0, context.getString(R.string.action_skip), skipIntent(id))

        return builder.build()
    }

    private fun consequenceText(preview: CascadePreview): String? =
        preview.moved.takeIf { it.size > 1 }?.let { "Moves ${it.size - 1} later steps" }

    private fun channelFor(salience: Salience): String = when (salience) {
        Salience.ALARM -> Channels.ALARM_ID
        Salience.NOTIFY -> Channels.REMINDER_ID
        Salience.SILENT, Salience.TIMELINE -> Channels.QUIET_ID
    }

    private fun priorityFor(salience: Salience): Int = when (salience) {
        Salience.ALARM -> NotificationCompat.PRIORITY_MAX
        Salience.NOTIFY -> NotificationCompat.PRIORITY_DEFAULT
        Salience.SILENT, Salience.TIMELINE -> NotificationCompat.PRIORITY_LOW
    }

    private fun doneIntent(id: Long) = NotificationActions.pendingIntent(context, id, NotificationActions.ACTION_DONE)

    private fun minimumIntent(id: Long) =
        NotificationActions.pendingIntent(context, id, NotificationActions.ACTION_DONE_MINIMUM)

    private fun snoozeIntent(id: Long) =
        NotificationActions.pendingIntent(context, id, NotificationActions.ACTION_SNOOZE)

    private fun skipIntent(id: Long) = NotificationActions.pendingIntent(context, id, NotificationActions.ACTION_SKIP)
}
