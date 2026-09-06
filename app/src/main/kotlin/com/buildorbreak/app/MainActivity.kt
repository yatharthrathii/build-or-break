package com.buildorbreak.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buildorbreak.app.navigation.BuildOrBreakNavGraph
import com.buildorbreak.app.navigation.OnboardingRoute
import com.buildorbreak.app.navigation.ShellActions
import com.buildorbreak.app.navigation.TodayRoute
import com.buildorbreak.app.navigation.startSettings
import com.buildorbreak.core.designsystem.theme.BuildOrBreakTheme
import com.buildorbreak.core.model.enums.ThemeMode
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

    private val viewModel: MainViewModel by viewModels()

    /**
     * The one runtime permission the app asks for directly.
     *
     * Nothing is done with the answer here. The first run screen re reads the
     * capabilities when the activity resumes, which happens as the system
     * dialog closes, so the row updates itself.
     */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Held until the first run flag and the theme have been read, so the
        // first drawn frame is the right screen in the right palette.
        splash.setKeepOnScreenCondition { viewModel.state.value == null }

        setContent {
            val shell by viewModel.state.collectAsStateWithLifecycle()
            val state = shell ?: return@setContent

            BuildOrBreakTheme(darkTheme = isDark(state.themeMode)) {
                BuildOrBreakNavGraph(
                    startRoute = if (state.onboardingComplete) TodayRoute else OnboardingRoute,
                    actions = ShellActions(
                        openSettingsFor = ::openSettingsFor,
                        openAutostart = { startSettings(guide.autostartIntent()) },
                        requestNotifications = ::requestNotifications,
                        share = ::share,
                    ),
                )
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun isDark(mode: ThemeMode): Boolean = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    /**
     * Asks for notifications where asking is possible.
     *
     * The runtime permission exists from Android 13. Before that, notifications
     * are on unless the user turned them off in settings, so the only thing to
     * do is open that screen.
     */
    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startSettings(VendorIntents.notificationSettingsIntent(this))
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

    /** The export, handed to whichever app the user picks. */
    private fun share(text: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/json")
            .putExtra(Intent.EXTRA_TEXT, text)

        startActivity(Intent.createChooser(send, getString(R.string.settings_export_chooser)))
    }
}
