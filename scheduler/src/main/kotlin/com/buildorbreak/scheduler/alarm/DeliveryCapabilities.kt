package com.buildorbreak.scheduler.alarm

/**
 * What the operating system is currently letting this app do.
 *
 * An interface rather than a set of static calls, and that is the whole point.
 * The rules that turn these five answers into a delivery tier are the part worth
 * testing exhaustively, and they cannot be tested at all if they are tangled up
 * with `AlarmManager` and a build version check. Behind this line the answers are
 * facts; in front of it they are a decision.
 *
 * Every one of these can change while the app is running. The user revokes a
 * permission from the notification shade, or a battery manager takes something
 * away overnight. Nothing here is cached.
 */
interface DeliveryCapabilities {

    /** Whether notifications are permitted at all. Runtime permission from Android 13. */
    fun notificationsEnabled(): Boolean

    /**
     * Whether the alarm channel can still make a sound and appear over the lock
     * screen.
     *
     * Permission is not the same as audibility. A user who set the channel to
     * silent has an app with every permission granted and no working alarm, and
     * an app that reports itself as fully capable in that state is lying.
     */
    fun alarmChannelAudible(): Boolean

    /**
     * Whether an alarm can be set for an exact minute.
     *
     * Denied by default from Android 14 for apps that ask for
     * `SCHEDULE_EXACT_ALARM`. Without it every alarm is at the mercy of Doze
     * batching, which can be off by fifteen minutes or more.
     */
    fun canScheduleExactAlarms(): Boolean

    /**
     * Whether a notification may take over the screen.
     *
     * Auto granted only to calling and alarm apps since January 2025, and a
     * restricted permission that needs a Play Console declaration.
     */
    fun canUseFullScreenIntent(): Boolean

    /**
     * Whether the app is exempt from battery optimisation.
     *
     * The one that actually decides whether an alarm fires on most phones sold
     * in India. Every permission above can be granted and a vendor battery
     * manager will still stop the process overnight.
     */
    fun ignoringBatteryOptimisations(): Boolean
}
