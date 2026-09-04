package com.buildorbreak.scheduler.alarm

import com.buildorbreak.core.model.enums.DeliveryTier
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Every combination that matters, without a device.
 *
 * This is the reason the capability queries sit behind an interface. The rules
 * below are the product claim in code form, and a rule that can only be checked
 * by installing on a phone and revoking a permission by hand is a rule nobody
 * checks twice.
 */
class TierDetectorTest {

    private class FakeCapabilities(
        var notifications: Boolean = true,
        var audible: Boolean = true,
        var exact: Boolean = true,
        var fullScreen: Boolean = true,
        var unrestricted: Boolean = true,
    ) : DeliveryCapabilities {
        override fun notificationsEnabled() = notifications
        override fun alarmChannelAudible() = audible
        override fun canScheduleExactAlarms() = exact
        override fun canUseFullScreenIntent() = fullScreen
        override fun ignoringBatteryOptimisations() = unrestricted
    }

    private fun detect(
        notifications: Boolean = true,
        audible: Boolean = true,
        exact: Boolean = true,
        fullScreen: Boolean = true,
        unrestricted: Boolean = true,
    ): DeliveryStatus = TierDetector(
        FakeCapabilities(notifications, audible, exact, fullScreen, unrestricted),
    ).detect()

    // The four tiers ----------------------------------------------------------

    @Test
    fun `everything granted is the full screen alarm tier`() {
        val status = detect()

        assertThat(status.tier).isEqualTo(DeliveryTier.FULL_SCREEN_ALARM)
        assertThat(status.isFullyCapable).isTrue()
        assertThat(status.blockers).isEmpty()
    }

    @Test
    fun `without a full screen intent the alarm still arrives audibly and on time`() {
        val status = detect(fullScreen = false)

        assertThat(status.tier).isEqualTo(DeliveryTier.EXACT_HEADS_UP)
        assertThat(status.blockers).containsExactly(TierBlocker.FULL_SCREEN_INTENT_DENIED)
    }

    @Test
    fun `without exact alarms the app drops to whatever doze allows`() {
        val status = detect(exact = false)

        assertThat(status.tier).isEqualTo(DeliveryTier.INEXACT_NOTIFICATION)
        assertThat(status.blockers).contains(TierBlocker.EXACT_ALARMS_DENIED)
    }

    @Test
    fun `without notifications the app can only speak when it is opened`() {
        val status = detect(notifications = false)

        assertThat(status.tier).isEqualTo(DeliveryTier.IN_APP_ONLY)
        assertThat(status.isSilent).isTrue()
    }

    // The one that catches people out -----------------------------------------

    @Test
    fun `a silenced channel drops the tier even with every permission granted`() {
        val status = detect(audible = false)

        // Permission is not audibility. A silent notification arriving at exactly
        // the right minute is still a silent notification, and calling that an
        // alarm would put a number on the Reliability screen that the user's own
        // morning contradicts.
        assertThat(status.tier).isEqualTo(DeliveryTier.INEXACT_NOTIFICATION)
        assertThat(status.blockers).contains(TierBlocker.CHANNEL_SILENCED)
    }

    @Test
    fun `a silenced channel cannot be rescued by a full screen intent`() {
        assertThat(detect(audible = false, fullScreen = true).tier)
            .isEqualTo(DeliveryTier.INEXACT_NOTIFICATION)
    }

    // Battery optimisation ----------------------------------------------------

    @Test
    fun `battery optimisation never lowers the tier on its own`() {
        val status = detect(unrestricted = false)

        // The app cannot tell whether a vendor will actually kill it, so it does
        // not claim to know. It says so and leaves the tier alone.
        assertThat(status.tier).isEqualTo(DeliveryTier.FULL_SCREEN_ALARM)
        assertThat(status.blockers).containsExactly(TierBlocker.BATTERY_OPTIMISED)
        assertThat(status.isFullyCapable).isFalse()
    }

    @Test
    fun `battery optimisation is reported alongside whatever tier was reached`() {
        val status = detect(exact = false, unrestricted = false)

        assertThat(status.tier).isEqualTo(DeliveryTier.INEXACT_NOTIFICATION)
        assertThat(status.blockers)
            .containsExactly(TierBlocker.EXACT_ALARMS_DENIED, TierBlocker.BATTERY_OPTIMISED)
            .inOrder()
    }

    // What the user is actually shown -----------------------------------------

    @Test
    fun `with notifications off nothing downstream of that switch is listed`() {
        val status = detect(notifications = false, audible = false, exact = false, fullScreen = false)

        // Burying the one thing that has to be done first under three that cannot
        // be done yet is how a help screen stops being read.
        assertThat(status.blockers).containsExactly(TierBlocker.NOTIFICATIONS_DENIED)
    }

    @Test
    fun `blockers come back with the one that matters most first`() {
        val status = detect(audible = false, exact = false, fullScreen = false, unrestricted = false)

        assertThat(status.blockers).containsExactly(
            TierBlocker.CHANNEL_SILENCED,
            TierBlocker.EXACT_ALARMS_DENIED,
            TierBlocker.FULL_SCREEN_INTENT_DENIED,
            TierBlocker.BATTERY_OPTIMISED,
        ).inOrder()
    }

    @Test
    fun `the screen is offered the two worth fixing rather than all of them`() {
        val status = detect(audible = false, exact = false, fullScreen = false, unrestricted = false)

        assertThat(status.topBlockers()).containsExactly(
            TierBlocker.CHANNEL_SILENCED,
            TierBlocker.EXACT_ALARMS_DENIED,
        ).inOrder()
    }

    @Test
    fun `a phone with nothing granted still reports the battery manager`() {
        val status = detect(notifications = false, unrestricted = false)

        assertThat(status.tier).isEqualTo(DeliveryTier.IN_APP_ONLY)
        assertThat(status.blockers)
            .containsExactly(TierBlocker.NOTIFICATIONS_DENIED, TierBlocker.BATTERY_OPTIMISED)
            .inOrder()
    }

    // Every combination resolves to something --------------------------------

    @Test
    fun `no combination of settings produces an undefined tier`() {
        // Sixteen settings combinations, read off the bits of a counter so the
        // loop stays flat and every one of them is visited exactly once.
        (0 until 16).forEach { bits ->
            val status = detect(
                notifications = bits and 1 != 0,
                audible = bits and 2 != 0,
                exact = bits and 4 != 0,
                fullScreen = bits and 8 != 0,
            )

            assertThat(status.tier).isIn(DeliveryTier.entries)
            // Anything short of the top tier has to be able to say why.
            val explained = status.tier == DeliveryTier.FULL_SCREEN_ALARM || status.blockers.isNotEmpty()
            assertThat(explained).isTrue()
        }
    }
}
