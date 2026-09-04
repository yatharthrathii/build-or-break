package com.buildorbreak.scheduler.oem

import android.content.Context
import android.content.Intent
import com.buildorbreak.scheduler.alarm.TierBlocker
import com.buildorbreak.scheduler.alarm.TierDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * One thing the user can do, and the screen that does it.
 *
 * [intent] is null when this phone has no such screen, which the caller shows as
 * plain instructions instead of a button that goes nowhere.
 */
data class GuidanceStep(
    val blocker: TierBlocker,
    val intent: Intent?,
)

/**
 * Turns "your alarms may not fire" into two taps.
 *
 * The README promises the app will say exactly which two settings would move it
 * up a tier, and this is where that promise is kept. A reliability screen that
 * says something is wrong without saying what to do about it is a complaint, not
 * a feature.
 *
 * Two is the limit on purpose. A list of six settings gets closed; the two that
 * would change the most get done.
 */
class OemGuide @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val tiers: TierDetector,
) {

    /**
     * The steps worth showing, most useful first.
     *
     * Ordering comes from `TierDetector`, which already sorts blockers by how
     * much fixing each would change. This adds the screen for each and stops at
     * two.
     */
    fun steps(limit: Int = 2): List<GuidanceStep> =
        tiers.detect().topBlockers(limit).map { GuidanceStep(it, intentFor(it)) }

    /** Whether this phone hides an autostart switch that has to be found by hand. */
    fun needsAutostartGuidance(): Boolean = VendorIntents.needsAutostartGuidance(context)

    /**
     * The autostart screen, which no capability check can see the state of.
     *
     * `PowerManager` reports the platform exemption and knows nothing about a
     * vendor's own list, so the app cannot tell whether this has been done. It
     * offers the screen and says why, and does not pretend to verify it. Claiming
     * to know would be worse than admitting the gap.
     */
    fun autostartIntent(): Intent? = VendorIntents.autostartIntent(context)

    private fun intentFor(blocker: TierBlocker): Intent? = when (blocker) {
        TierBlocker.NOTIFICATIONS_DENIED,
        TierBlocker.CHANNEL_SILENCED,
        -> VendorIntents.notificationSettingsIntent(context)

        TierBlocker.EXACT_ALARMS_DENIED -> VendorIntents.exactAlarmSettingsIntent(context)

        // There is no screen for this one. The permission is granted through the
        // notification settings page on some versions and nowhere on others, so
        // the app sends the user to its own settings rather than somewhere that
        // may not exist.
        TierBlocker.FULL_SCREEN_INTENT_DENIED -> VendorIntents.appSettingsIntent(context)

        TierBlocker.BATTERY_OPTIMISED ->
            VendorIntents.batterySettingsIntent(context) ?: VendorIntents.autostartIntent(context)
    }
}
