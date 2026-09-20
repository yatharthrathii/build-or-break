package com.buildorbreak.app.feature.alarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buildorbreak.app.MainViewModel
import com.buildorbreak.core.designsystem.theme.BuildOrBreakTheme
import com.buildorbreak.core.model.enums.ThemeMode
import com.buildorbreak.scheduler.alarm.AlarmScheduling
import dagger.hilt.android.AndroidEntryPoint

/**
 * The window the alarm opens in.
 *
 * Shown over the lock screen with the display turned on, which is what makes
 * a full screen alarm a full screen alarm. Its own task and out of recents,
 * so dismissing it never leaves a stray "Build or Break" card behind and never
 * lands the user in the middle of the plan editor at six in the morning.
 */
@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {

    private val shell: MainViewModel by viewModels()

    /** Which step is on screen. State, because a second alarm can arrive while the first is up. */
    private val showing = mutableStateOf<Pair<Long, Long>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        showOverLockScreen()

        val step = stepIn(intent)
        if (step == null) {
            finish()
            return
        }
        showing.value = step

        setContent {
            val state by shell.state.collectAsStateWithLifecycle()
            val mode = state?.themeMode ?: ThemeMode.SYSTEM
            val (occurrenceId, itemId) = showing.value ?: return@setContent

            BuildOrBreakTheme(darkTheme = isDark(mode)) {
                // Keyed on the step, so a second alarm gets its own screen
                // rather than the first one's state with a new title.
                key(occurrenceId) {
                    AlarmScreen(occurrenceId = occurrenceId, itemId = itemId, onClosed = ::finish)
                }
            }
        }
    }

    /**
     * The activity is `singleInstance`, so a second alarm while the first is
     * still up arrives here rather than in a new window. The newer step takes
     * the screen; the older one still has its notification and every button
     * on it.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        stepIn(intent)?.let { showing.value = it }
    }

    private fun stepIn(intent: Intent): Pair<Long, Long>? {
        val occurrenceId = intent.getLongExtra(AlarmScheduling.EXTRA_OCCURRENCE_ID, NO_ID)
        val itemId = intent.getLongExtra(AlarmScheduling.EXTRA_ITEM_ID, NO_ID)

        return (occurrenceId to itemId).takeIf { occurrenceId != NO_ID && itemId != NO_ID }
    }

    /**
     * Over the lock screen, with the display on.
     *
     * The activity methods arrived in API 27. The app's minimum is 26, where
     * the same two things are window flags that were deprecated one release
     * later. Both paths do the same thing; only one exists on each version.
     */
    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
    }

    @androidx.compose.runtime.Composable
    private fun isDark(mode: ThemeMode): Boolean = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    private companion object {
        const val NO_ID = -1L
    }
}
