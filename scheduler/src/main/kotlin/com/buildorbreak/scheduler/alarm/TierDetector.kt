package com.buildorbreak.scheduler.alarm

import com.buildorbreak.core.model.enums.DeliveryTier
import javax.inject.Inject

/**
 * Something standing between the app and the tier above the one it is on.
 *
 * Each of these maps to exactly one screen the user can be sent to. That is the
 * point of naming them rather than returning a boolean: the README promises the
 * app will say which two settings would move it up, and it can only do that if
 * it knows which two.
 */
enum class TierBlocker {
    /** Notifications are switched off entirely. Nothing can be delivered. */
    NOTIFICATIONS_DENIED,

    /** Notifications are allowed, but the alarm channel was set to silent. */
    CHANNEL_SILENCED,

    /** Exact alarms are denied, so every time is at the mercy of Doze batching. */
    EXACT_ALARMS_DENIED,

    /** A notification cannot take over the screen. */
    FULL_SCREEN_INTENT_DENIED,

    /**
     * A battery manager may stop the app before an alarm is due.
     *
     * Never blocks a tier on its own, because the app cannot tell whether a
     * vendor will actually kill it. It is reported alongside whatever tier was
     * reached, since on many phones it is the real reason a morning is missed.
     */
    BATTERY_OPTIMISED,
}

/**
 * The tier the app is on, and what is holding it there.
 *
 * [blockers] is ordered by how much difference fixing it would make, so a screen
 * can show the first one or two and be showing the ones that matter.
 */
data class DeliveryStatus(
    val tier: DeliveryTier,
    val blockers: List<TierBlocker>,
) {
    /** Nothing to fix. Alarms will fire the way the plan says. */
    val isFullyCapable: Boolean get() = tier == DeliveryTier.FULL_SCREEN_ALARM && blockers.isEmpty()

    /** The app cannot reach the user outside itself at all. */
    val isSilent: Boolean get() = tier == DeliveryTier.IN_APP_ONLY

    /** The one or two things worth putting in front of the user. */
    fun topBlockers(limit: Int = 2): List<TierBlocker> = blockers.take(limit)
}

/**
 * Works out what the scheduler can actually promise right now.
 *
 * Never assumed, always detected. This is the piece the whole product claim
 * rests on, and it is deliberately a pure function of five booleans so that
 * every combination of them can be tested without a device.
 *
 * The tiers degrade in the order below, and each one is genuinely useful:
 *
 * ```
 * FULL_SCREEN_ALARM     takes over the screen, rings, ramps       everything granted
 * EXACT_HEADS_UP        arrives at the right minute, with sound   no full screen intent
 * INEXACT_NOTIFICATION  arrives, within a Doze window             no exact alarms
 * IN_APP_ONLY           visible when the app is opened            no notifications
 * ```
 *
 * The important design decision is that the app never silently pretends. An
 * alarm scheduled inexactly is not an alarm, and a routine app that says it will
 * wake somebody at six and delivers at six twenty has done worse than one that
 * said it could not.
 */
class TierDetector @Inject constructor(
    private val capabilities: DeliveryCapabilities,
) {

    fun detect(): DeliveryStatus {
        val notifications = capabilities.notificationsEnabled()
        val audible = notifications && capabilities.alarmChannelAudible()
        val exact = capabilities.canScheduleExactAlarms()
        val fullScreen = capabilities.canUseFullScreenIntent()

        return DeliveryStatus(
            tier = tierFrom(notifications, audible, exact, fullScreen),
            blockers = blockersFrom(notifications, audible, exact, fullScreen),
        )
    }

    /** Convenience for `AlarmGateway.currentTier`, which only needs the verdict. */
    fun currentTier(): DeliveryTier = detect().tier

    /**
     * A silenced channel drops the app to the inexact tier even when exact alarms
     * are granted.
     *
     * That looks harsh and is not. The two tiers above it both promise sound: one
     * takes over the screen and one arrives as an audible heads up. A silent
     * notification arriving at exactly the right minute is still a silent
     * notification, and claiming an alarm tier for it would put a number on the
     * Reliability screen that the user's own experience contradicts.
     */
    private fun tierFrom(
        notifications: Boolean,
        audible: Boolean,
        exact: Boolean,
        fullScreen: Boolean,
    ): DeliveryTier = when {
        !notifications -> DeliveryTier.IN_APP_ONLY
        !exact || !audible -> DeliveryTier.INEXACT_NOTIFICATION
        fullScreen -> DeliveryTier.FULL_SCREEN_ALARM
        else -> DeliveryTier.EXACT_HEADS_UP
    }

    /**
     * Ordered by how much fixing each one would change, not by how easy it is.
     *
     * Notifications first because nothing works without them, then the channel,
     * then exact timing, then the full screen. Battery optimisation is always
     * last and always reported: it blocks no tier, and on a phone with an
     * aggressive vendor battery manager it is nonetheless the reason the alarm
     * did not arrive.
     */
    private fun blockersFrom(
        notifications: Boolean,
        audible: Boolean,
        exact: Boolean,
        fullScreen: Boolean,
    ): List<TierBlocker> = buildList {
        if (!notifications) {
            // With notifications off, the channel and the full screen intent are
            // downstream of a switch the user has already turned off. Listing
            // them would bury the one thing that needs doing under two that
            // cannot be done yet.
            add(TierBlocker.NOTIFICATIONS_DENIED)
        } else {
            if (!audible) add(TierBlocker.CHANNEL_SILENCED)
            if (!exact) add(TierBlocker.EXACT_ALARMS_DENIED)
            if (!fullScreen) add(TierBlocker.FULL_SCREEN_INTENT_DENIED)
        }

        if (!capabilities.ignoringBatteryOptimisations()) add(TierBlocker.BATTERY_OPTIMISED)
    }
}
