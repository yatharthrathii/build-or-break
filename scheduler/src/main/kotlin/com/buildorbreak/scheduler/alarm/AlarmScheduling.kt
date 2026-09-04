package com.buildorbreak.scheduler.alarm

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/**
 * How an alarm is addressed, and why it can be cancelled reliably.
 *
 * `AlarmManager` has no way to list what is scheduled. The only handle on an
 * existing alarm is a `PendingIntent` that matches the one used to set it, so
 * cancelling depends entirely on being able to rebuild that intent exactly. Every
 * decision here exists to make that rebuild deterministic.
 */
object AlarmScheduling {

    const val ACTION_FIRE = "com.buildorbreak.scheduler.ACTION_FIRE"

    const val EXTRA_OCCURRENCE_ID = "occurrence_id"
    const val EXTRA_ITEM_ID = "item_id"

    /**
     * One request code per occurrence, derived rather than allocated.
     *
     * Deriving it means the same occurrence always maps to the same slot, on
     * this launch and on the next one after a reboot, with nothing stored in
     * between. A counter would need persisting and would drift the moment a
     * write was lost, and a drifted counter means an alarm nobody can cancel.
     *
     * The modulo is what fits a row id into the int a `PendingIntent` takes. Two
     * occurrences would have to be `Int.MAX_VALUE` apart to collide, which is
     * around two billion rows on one phone.
     */
    fun requestCode(occurrenceId: Long): Int = (occurrenceId % Int.MAX_VALUE).toInt()

    /**
     * The intent an alarm carries.
     *
     * The occurrence id goes in the data URI as well as in an extra. Extras are
     * not part of `PendingIntent` equality, so two alarms differing only by extra
     * would be the same intent as far as `AlarmManager` is concerned, and setting
     * the second would silently replace the first.
     */
    fun fireIntent(context: Context, occurrenceId: Long, itemId: Long): Intent =
        Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            data = "buildorbreak://occurrence/$occurrenceId".toUri()
            putExtra(EXTRA_OCCURRENCE_ID, occurrenceId)
            putExtra(EXTRA_ITEM_ID, itemId)
        }

    /**
     * [mutable] is false for everything the scheduler sets. An immutable pending
     * intent cannot have its extras rewritten by another app, and since Android
     * 12 one or the other flag has to be stated explicitly anyway.
     */
    fun pendingIntent(
        context: Context,
        occurrenceId: Long,
        itemId: Long,
        create: Boolean = true,
    ): PendingIntent? {
        val flags = PendingIntent.FLAG_IMMUTABLE or
            if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE

        return PendingIntent.getBroadcast(
            context,
            requestCode(occurrenceId),
            fireIntent(context, occurrenceId, itemId),
            flags,
        )
    }
}
