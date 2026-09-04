package com.buildorbreak.scheduler.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService

/**
 * The notification channels, and why there are exactly three.
 *
 * A channel is the only unit of control Android gives a user, and every one an
 * app creates is another switch somebody can turn off by accident. Three is the
 * fewest that lets somebody keep the alarms and silence the rest, which is the
 * distinction that actually matters:
 *
 * - **Alarm** is the one that wakes you. High importance, its own sound, allowed
 *   over the lock screen
 * - **Reminder** is an ordinary step arriving. Default importance
 * - **Quiet** is a timeline note. Low importance, no sound, never a heads up
 *
 * Importance is set once at creation and the platform will not raise it
 * afterwards, only lower it. That is deliberate on Android's part and it is why
 * `DeliveryCapabilities.alarmChannelAudible` exists: the app has to be able to
 * notice that the user turned this down, and say so, rather than carry on
 * claiming an alarm it can no longer make.
 */
object Channels {

    const val ALARM_ID = "alarm"
    const val REMINDER_ID = "reminder"
    const val QUIET_ID = "quiet"

    /**
     * Creating a channel that already exists updates its name and leaves the
     * user's own importance setting alone, so this is safe to call on every
     * launch and is the only way to be sure the channels exist before the first
     * alarm is posted.
     */
    fun ensureCreated(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return

        manager.createNotificationChannel(
            NotificationChannel(ALARM_ID, "Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Steps that are meant to interrupt you"
                setBypassDnd(false)
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            },
        )

        manager.createNotificationChannel(
            NotificationChannel(REMINDER_ID, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Ordinary steps arriving"
                enableVibration(true)
            },
        )

        manager.createNotificationChannel(
            NotificationChannel(QUIET_ID, "Quiet notes", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Timeline items that never make a sound"
                enableVibration(false)
            },
        )
    }
}
