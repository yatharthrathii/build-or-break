package com.buildorbreak.scheduler.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.buildorbreak.scheduler.alarm.AlarmScheduling

/**
 * The buttons on a notification, and the intents behind them.
 *
 * appflow.md requires eighty percent of interactions to complete without opening
 * the app, and this is where that requirement becomes concrete. Every action here
 * finishes the job from the shade: nothing starts an activity, nothing waits for
 * the user to find the app, and the routine can be run entirely from the lock
 * screen on a phone that is face down on a table.
 */
object NotificationActions {

    const val ACTION_DONE = "com.buildorbreak.scheduler.ACTION_DONE"
    const val ACTION_DONE_MINIMUM = "com.buildorbreak.scheduler.ACTION_DONE_MINIMUM"
    const val ACTION_SNOOZE = "com.buildorbreak.scheduler.ACTION_SNOOZE"
    const val ACTION_SKIP = "com.buildorbreak.scheduler.ACTION_SKIP"

    const val EXTRA_OCCURRENCE_ID = AlarmScheduling.EXTRA_OCCURRENCE_ID
    const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"

    /** The default offer when somebody taps snooze without choosing a length. */
    const val DEFAULT_SNOOZE_MINUTES = 10

    /**
     * Request codes are the occurrence id offset per action.
     *
     * Four buttons on one notification need four distinct pending intents, and
     * two that collide would leave the same button doing two different things.
     * Offsetting a derived code keeps them distinct without storing anything.
     */
    private fun requestCode(occurrenceId: Long, action: String): Int =
        AlarmScheduling.requestCode(occurrenceId) * ACTION_SLOTS + slotOf(action)

    /** One slot per button. Skip is the fallback so an unknown action cannot collide. */
    private fun slotOf(action: String): Int = when (action) {
        ACTION_DONE -> SLOT_DONE
        ACTION_DONE_MINIMUM -> SLOT_MINIMUM
        ACTION_SNOOZE -> SLOT_SNOOZE
        else -> SLOT_SKIP
    }

    fun pendingIntent(
        context: Context,
        occurrenceId: Long,
        action: String,
        snoozeMinutes: Int = DEFAULT_SNOOZE_MINUTES,
    ): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
            // In the data URI as well as in an extra, because extras do not take
            // part in PendingIntent equality and two actions differing only by
            // extra would silently become the same pending intent.
            data = "buildorbreak://action/$action/$occurrenceId".toUri()
            putExtra(EXTRA_OCCURRENCE_ID, occurrenceId)
            putExtra(EXTRA_SNOOZE_MINUTES, snoozeMinutes)
        }

        return PendingIntent.getBroadcast(
            context,
            requestCode(occurrenceId, action),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private const val SLOT_DONE = 0
    private const val SLOT_MINIMUM = 1
    private const val SLOT_SNOOZE = 2
    private const val SLOT_SKIP = 3

    private const val ACTION_SLOTS = 4
}
