package com.buildorbreak.app

import android.Manifest
import android.content.Intent
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import com.buildorbreak.scheduler.notification.Channels
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

    /**
     * **There is deliberately no `setKeepOnScreenCondition` here.**
     *
     * Holding the splash until the first run flag had been read looked like
     * the tidy answer and produced the worst bug in the app. The condition
     * installs a pre draw listener that refuses the draw while it holds; when
     * it let go, nothing asked for another one, so the window kept presenting
     * the last frame it had. The mark sat on top of a fully composed, fully
     * tappable app until something else forced a redraw, and the first tap
     * always did. Nothing catches that: the accessibility tree is correct, the
     * tests pass, the logs are clean, and the app looks hung.
     *
     * The flash it was added to prevent is solved below instead, by painting
     * the ground on the very first frame and only placing the navigation once
     * there is something to navigate to.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        splash.setOnExitAnimationListener(::handOver)

        setContent {
            val shell by viewModel.state.collectAsStateWithLifecycle()
            val mode = shell?.themeMode ?: ThemeMode.SYSTEM

            SplashThemeFor(mode)

            BuildOrBreakTheme(darkTheme = isDark(mode)) {
                // The ground is painted from the first frame, so the hand over
                // from the splash is one colour becoming the same colour. The
                // navigation waits for the first run flag rather than guessing:
                // drawing Today and then replacing it with the first run screen
                // is the flash this whole arrangement exists to avoid.
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                    shell?.let { state ->
                        BuildOrBreakNavGraph(
                            startRoute = if (state.onboardingComplete) TodayRoute else OnboardingRoute,
                            actions = ShellActions(
                                openSettingsFor = ::openSettingsFor,
                                openAutostart = { startSettings(guide.autostartIntent()) },
                                openLockScreen = { startSettings(guide.lockScreenIntent()) },
                                requestNotifications = ::requestNotifications,
                                share = ::share,
                                openAlarmChannel = {
                                    startSettings(
                                        VendorIntents.channelSettingsIntent(this@MainActivity, Channels.ALARM_ID),
                                    )
                                },
                            ),
                        )
                    }
                }
            }
        }
    }

    /**
     * The splash window follows the app's own theme from the next launch on.
     *
     * The system draws that window before the process exists, so it cannot ask
     * a database which palette the user picked and falls back to the phone's
     * dark setting. Somebody who chose light inside a dark phone therefore gets
     * a black flash before a white app. `setSplashScreenTheme` is the platform's
     * answer: it remembers a theme for the launches after this one. It arrived
     * in Android 12, and below that the phone's setting is all there is.
     */
    @androidx.compose.runtime.Composable
    private fun SplashThemeFor(mode: ThemeMode) {
        androidx.compose.runtime.LaunchedEffect(mode) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@LaunchedEffect

            splashScreen.setSplashScreenTheme(
                when (mode) {
                    ThemeMode.SYSTEM -> Resources.ID_NULL
                    ThemeMode.LIGHT -> R.style.Theme_BuildOrBreak_Starting_Light
                    ThemeMode.DARK -> R.style.Theme_BuildOrBreak_Starting_Dark
                },
            )
        }
    }

    /**
     * The splash leaves rather than disappearing.
     *
     * One frame the mark is there and the next it is gone reads as a stutter on
     * a mid range phone, because it is indistinguishable from a dropped frame.
     * Lifting and fading it over a fifth of a second reads as a hand over.
     *
     * **The removal is on a timer, not on the animation's end action.** Taking
     * this listener over makes the app responsible for getting rid of the
     * splash view, and a `ViewPropertyAnimator` does not start until its view
     * gets a draw pass. On a device where that pass never comes the end action
     * never runs, and the mark then sits on top of a fully drawn, fully
     * interactive app forever. It looks exactly like a hung launch, and the
     * accessibility tree says everything is fine, so nothing catches it except
     * looking at the screen. A timer cannot be starved that way: at worst the
     * fade is not seen and the splash still goes.
     */
    private fun handOver(splash: androidx.core.splashscreen.SplashScreenViewProvider) {
        val view = splash.view

        view.animate()
            .alpha(0f)
            .scaleX(SPLASH_EXIT_SCALE)
            .scaleY(SPLASH_EXIT_SCALE)
            .setDuration(SPLASH_EXIT_MILLIS)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .start()

        view.postDelayed(splash::remove, SPLASH_EXIT_MILLIS)
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
                VendorIntents.requestIgnoreBatteryIntent(this)
                    ?: VendorIntents.batterySettingsIntent(this)
                    ?: VendorIntents.autostartIntent(this)
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

    private companion object {
        const val SPLASH_EXIT_MILLIS = 220L
        const val SPLASH_EXIT_SCALE = 1.06f
    }
}
