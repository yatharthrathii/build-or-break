package com.buildorbreak.app.navigation

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.buildorbreak.app.feature.settings.ReliabilityScreen
import com.buildorbreak.app.feature.today.TodayScreen
import com.buildorbreak.scheduler.alarm.TierBlocker

/**
 * Two screens, and the back stack between them.
 *
 * Small on purpose. The plan editor, insights and onboarding arrive in later
 * milestones and will be added as entries here; nothing about this file has to
 * change shape to take them.
 *
 * [openSettings] is passed in rather than resolved here. Turning a blocker into
 * a settings screen needs `OemGuide` and a `Context`, and neither belongs in a
 * composable or in a ViewModel, so the activity does it and hands down a lambda.
 */
@Composable
fun BuildOrBreakNavGraph(
    openSettings: (TierBlocker) -> Unit,
    openAutostart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backStack = rememberNavBackStack(TodayRoute)

    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<TodayRoute> {
                TodayScreen(onOpenReliability = { backStack.add(ReliabilityRoute) })
            }

            entry<ReliabilityRoute> {
                ReliabilityScreen(onFix = openSettings, onOpenAutostart = openAutostart)
            }
        },
    )
}

/**
 * Starts a settings screen without letting a missing one crash the app.
 *
 * Every vendor intent is resolved before it is offered, but a phone can still
 * change under the app between resolving and launching, and an app that crashes
 * while trying to help somebody fix their alarms has made things considerably
 * worse.
 */
internal fun android.content.Context.startSettings(intent: Intent?) {
    val target = intent ?: return

    @Suppress("SwallowedException")
    try {
        startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (missing: android.content.ActivityNotFoundException) {
        // Nothing to show and nothing useful to say about it. The card that
        // offered this stays on screen, which is the honest outcome.
    }
}
