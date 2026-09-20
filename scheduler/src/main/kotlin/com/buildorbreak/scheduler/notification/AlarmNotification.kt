package com.buildorbreak.scheduler.notification

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.buildorbreak.core.model.enums.Salience
import com.buildorbreak.core.model.execution.Occurrence
import com.buildorbreak.core.model.plan.Item
import com.buildorbreak.core.model.resolved.CascadePreview
import com.buildorbreak.scheduler.R
import com.buildorbreak.scheduler.alarm.AlarmRingerService
import com.buildorbreak.scheduler.alarm.AlarmScheduling
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * What a step looks like when it arrives.
 *
 * Pulled out of the gateway because two callers need the same notification and
 * they must not drift: the gateway posts it when a step is due, and the ringer
 * service posts it as the one the platform demands while it holds the alarm
 * stream open. A second, slightly different builder for the second case is how
 * an app ends up with two notification designs nobody chose.
 *
 * The shape is the design system's, as far as a notification allows one. The
 * app owns the small icon, the accent colour and the wording; the frame around
 * them belongs to the phone and arguing with it produces something that looks
 * wrong on every version but the one it was drawn against.
 */
class AlarmNotification @Inject constructor(@param:ApplicationContext private val context: Context) {

    /** The one the ringer service posts while it holds the alarm open. */
    fun ringing(intent: Intent): Notification {
        val id = intent.getLongExtra(AlarmRingerService.EXTRA_OCCURRENCE_ID, 0)
        val itemId = intent.getLongExtra(AlarmRingerService.EXTRA_ITEM_ID, 0)
        val title = intent.getStringExtra(AlarmRingerService.EXTRA_TITLE).orEmpty()
        val detail = intent.getStringExtra(AlarmRingerService.EXTRA_DETAIL)
        val time = intent.getStringExtra(AlarmRingerService.EXTRA_TIME).orEmpty()
        val hasMinimum = intent.getBooleanExtra(AlarmRingerService.EXTRA_HAS_MINIMUM, false)

        return base(Channels.ALARM_ID, title, detail, time)
            // Colorized only applies to an ongoing notification, which is
            // exactly what this is. It turns the whole row the app's accent
            // while the alarm is live, so a glance at a lock screen full of
            // notifications finds this one first.
            .setColorized(true)
            .setOngoing(true)
            .setUsesChronometer(false)
            .setFullScreenIntent(screenIntent(id, itemId), true)
            .setContentIntent(screenIntent(id, itemId))
            .also { withActions(it, id, hasMinimum) }
            .build()
    }

    /**
     * The least a foreground service can post, for the starts that do not ring.
     *
     * A service started with `startForegroundService` has a few seconds to
     * promote itself or the platform kills the whole process, and that is true
     * even for a start that turns out to have nothing to do. This exists so the
     * ringer can satisfy that obligation and stop, rather than crash the app on
     * a malformed alarm at six in the morning. It is up for a few milliseconds.
     */
    fun placeholder(): Notification = base(Channels.ALARM_ID, context.getString(R.string.alarm_starting), null, "")
        .setOngoing(true)
        .build()

    /** The one a due step arrives as when it is not an alarm, or the alarm has stopped ringing. */
    fun due(occurrence: Occurrence, item: Item, preview: CascadePreview?): Notification {
        val time = occurrence.effectiveAt.format(clock)
        val builder = base(channelFor(item.salience), item.title, item.detail ?: consequence(preview), time)
            .setPriority(priorityFor(item.salience))
            .setContentIntent(screenIntent(occurrence.id, item.id))

        withActions(builder, occurrence.id, item.hasMinimum)

        // Only an alarm takes over the screen. A reminder that woke the display
        // at eleven at night is a reminder whose channel gets muted.
        if (item.salience == Salience.ALARM && canUseFullScreenIntent()) {
            builder.setFullScreenIntent(screenIntent(occurrence.id, item.id), true)
        }

        return builder.build()
    }

    fun canUseFullScreenIntent(): Boolean =
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            true
        } else {
            context.getSystemService<NotificationManager>()?.canUseFullScreenIntent() ?: false
        }

    /**
     * Everything the two share.
     *
     * The time is the sub text rather than the title, because the title is the
     * one line somebody reads from across a room. `setShowWhen(false)` drops
     * the platform's own clock: two times on one notification, one of them the
     * moment it was posted, is a way of asking which one is the real one.
     */
    private fun base(
        channel: String,
        title: String,
        detail: String?,
        time: String,
    ): NotificationCompat.Builder {
        // Here rather than only at startup. A channel that does not exist makes
        // a notification the platform quietly refuses to show, and the one path
        // that reaches this before the app has ever been opened is the ringer
        // waking a cold process at six in the morning.
        Channels.ensureCreated(context)

        return NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setContentTitle(title)
            .setContentText(detail ?: time)
            .setSubText(time.takeIf { it.isNotEmpty() && detail != null })
            .setStyle(detail?.let { NotificationCompat.BigTextStyle().bigText(it).setSummaryText(time) })
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
    }

    /**
     * The buttons are the feature, and there is only room for three.
     *
     * Android shows at most three actions and silently drops the rest. This
     * used to add four whenever a step had a smaller version, so on exactly
     * those steps Skip vanished: the one control that records a decision, gone
     * from the surface where this app expects most decisions to be made. It
     * looked fine in code, it looked fine in a screenshot of a step with no
     * minimum, and it left people unable to say no without opening the app.
     *
     * So: Done first, Skip last, and the middle slot goes to the smaller
     * version when one was written, snooze otherwise. Writing a minimum in
     * advance is a deliberate act and the whole point of it is to be offered
     * on a bad day, which outranks a snooze the user can also get by ignoring
     * the notification for ten minutes.
     */
    private fun withActions(builder: NotificationCompat.Builder, id: Long, hasMinimum: Boolean) {
        builder.addAction(
            R.drawable.ic_action_done,
            context.getString(R.string.action_done),
            action(id, NotificationActions.ACTION_DONE),
        )

        if (hasMinimum) {
            builder.addAction(
                R.drawable.ic_action_minimum,
                context.getString(R.string.action_minimum),
                action(id, NotificationActions.ACTION_DONE_MINIMUM),
            )
        } else {
            builder.addAction(
                R.drawable.ic_action_snooze,
                context.getString(R.string.action_snooze),
                action(id, NotificationActions.ACTION_SNOOZE),
            )
        }

        builder.addAction(
            R.drawable.ic_action_skip,
            context.getString(R.string.action_skip),
            action(id, NotificationActions.ACTION_SKIP),
        )
    }

    private fun action(id: Long, name: String) = NotificationActions.pendingIntent(context, id, name)

    /** The app's own alarm screen, reached by action because it lives in :app. */
    private fun screenIntent(occurrenceId: Long, itemId: Long): PendingIntent {
        val intent = Intent(AlarmScheduling.ACTION_ALARM_SCREEN)
            .setPackage(context.packageName)
            .putExtra(AlarmScheduling.EXTRA_OCCURRENCE_ID, occurrenceId)
            .putExtra(AlarmScheduling.EXTRA_ITEM_ID, itemId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return PendingIntent.getActivity(
            context,
            AlarmScheduling.requestCode(occurrenceId),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun consequence(preview: CascadePreview?): String? = preview?.moved?.takeIf { it.size > 1 }?.let { moved ->
        context.resources.getQuantityString(R.plurals.notification_moves_later, moved.size - 1, moved.size - 1)
    }

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

    /**
     * The phone's own twelve or twenty four hour setting.
     *
     * Read per call rather than held. A notification that says 18:00 on a phone
     * whose clock says 6:00 PM is asking somebody half asleep to translate.
     */
    private val clock: DateTimeFormatter
        get() = if (DateFormat.is24HourFormat(context)) {
            DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
        } else {
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
        }
}
