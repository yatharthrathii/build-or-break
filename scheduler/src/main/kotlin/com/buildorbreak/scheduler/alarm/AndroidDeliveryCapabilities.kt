package com.buildorbreak.scheduler.alarm

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.buildorbreak.scheduler.notification.Channels
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * The five questions, answered by the platform.
 *
 * Every answer is read fresh. Caching any of it would mean the Reliability
 * screen could show a permission the user revoked from the shade thirty seconds
 * ago, and the one thing this screen has to be is honest.
 *
 * The version checks are the interesting part, and each one is a real change
 * rather than defensive noise:
 *
 * - Android 12 introduced `canScheduleExactAlarms`; before it the permission was
 *   normal and always granted
 * - Android 13 made notifications a runtime permission
 * - Android 14 introduced `canUseFullScreenIntent` and made exact alarms denied
 *   by default
 */
class AndroidDeliveryCapabilities @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : DeliveryCapabilities {

    private val notifications: NotificationManager?
        get() = context.getSystemService()

    override fun notificationsEnabled(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * A channel the user has silenced, or blocked outright, cannot deliver an
     * alarm however many permissions are granted.
     *
     * A channel that does not exist yet reads as audible. It is created with high
     * importance the first time anything is posted, and reporting a lower tier
     * before the app has ever tried would tell a brand new install that it is
     * already broken.
     */
    override fun alarmChannelAudible(): Boolean {
        val channel = notifications?.getNotificationChannel(Channels.ALARM_ID) ?: return true

        return channel.importance >= NotificationManager.IMPORTANCE_DEFAULT
    }

    override fun canScheduleExactAlarms(): Boolean {
        val alarms = context.getSystemService<AlarmManager>() ?: return false

        // Below Android 12 the permission is normal and cannot be revoked.
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarms.canScheduleExactAlarms() else true
    }

    override fun canUseFullScreenIntent(): Boolean {
        // Below Android 14 holding the manifest permission is enough, and there
        // is no runtime query to ask.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true

        return notifications?.canUseFullScreenIntent() ?: false
    }

    /**
     * Answers the platform question only.
     *
     * A vendor battery manager is a separate matter this API knows nothing
     * about: a phone can report the app as exempt and still stop it overnight.
     * Guiding the user through those settings is `oem/`, and it is deliberately
     * not folded in here, because a guess dressed up as a capability check is
     * worse than an honest platform answer.
     */
    override fun ignoringBatteryOptimisations(): Boolean {
        val power = context.getSystemService<PowerManager>() ?: return false

        return power.isIgnoringBatteryOptimizations(context.packageName)
    }
}
