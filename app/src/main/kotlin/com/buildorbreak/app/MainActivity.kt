package com.buildorbreak.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.buildorbreak.app.navigation.BuildOrBreakNavGraph
import com.buildorbreak.app.navigation.startSettings
import com.buildorbreak.core.designsystem.theme.BuildOrBreakTheme
import com.buildorbreak.scheduler.alarm.TierBlocker
import com.buildorbreak.scheduler.oem.OemGuide
import com.buildorbreak.scheduler.oem.VendorIntents
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The only activity.
 *
 * Everything the user does day to day happens in a notification, so this is not
 * the main way the app is used. appflow.md wants eighty percent of interactions
 * to finish without opening it at all, and this screen exists for the twenty
 * percent that need to see the whole day, edit the plan, or find out why an
 * alarm did not arrive.
 *
 * [guide] is injected here rather than into a ViewModel because turning a
 * blocker into a settings screen needs an `Intent` and a `Context`, and
 * architecture.md keeps framework types out of a ViewModel. The activity is the
 * right place for a thing that opens another app's screen.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var guide: OemGuide

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            BuildOrBreakTheme {
                BuildOrBreakNavGraph(
                    openSettings = ::openSettingsFor,
                    openAutostart = { startSettings(guide.autostartIntent()) },
                )
            }
        }
    }

    /**
     * Sends the user to the one screen that fixes this blocker.
     *
     * The app's own settings page is the fallback rather than nothing happening.
     * A button that does nothing when pressed is worse than one that lands
     * somewhere approximate.
     */
    private fun openSettingsFor(blocker: TierBlocker) {
        val intent = when (blocker) {
            TierBlocker.NOTIFICATIONS_DENIED,
            TierBlocker.CHANNEL_SILENCED,
            -> VendorIntents.notificationSettingsIntent(this)

            TierBlocker.EXACT_ALARMS_DENIED -> VendorIntents.exactAlarmSettingsIntent(this)

            TierBlocker.FULL_SCREEN_INTENT_DENIED -> VendorIntents.appSettingsIntent(this)

            TierBlocker.BATTERY_OPTIMISED ->
                VendorIntents.batterySettingsIntent(this) ?: VendorIntents.autostartIntent(this)
        }

        startSettings(intent ?: VendorIntents.appSettingsIntent(this))
    }
}
