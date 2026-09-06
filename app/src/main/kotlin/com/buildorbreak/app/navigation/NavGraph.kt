package com.buildorbreak.app.navigation

import android.content.Intent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import com.buildorbreak.app.R
import com.buildorbreak.app.feature.insights.InsightsScreen
import com.buildorbreak.app.feature.onboarding.OnboardingScreen
import com.buildorbreak.app.feature.onboarding.StartChoice
import com.buildorbreak.app.feature.plan.ImportScreen
import com.buildorbreak.app.feature.plan.ItemEditorScreen
import com.buildorbreak.app.feature.plan.PlanScreen
import com.buildorbreak.app.feature.settings.ReliabilityScreen
import com.buildorbreak.app.feature.settings.SettingsScreen
import com.buildorbreak.app.feature.today.TodayScreen
import com.buildorbreak.core.designsystem.component.BottomNavBar
import com.buildorbreak.core.designsystem.component.NavDestination
import com.buildorbreak.scheduler.alarm.TierBlocker

/** How far a screen slides in. A fraction of its width, so it reads as a nudge. */
private const val SLIDE_FRACTION = 12
private const val FADE_MILLIS = 220

/**
 * What the screens need from the activity, and cannot do themselves.
 *
 * Every one of these opens another app's screen or asks the system for
 * something, which needs an `Intent` or an activity result launcher. Neither
 * belongs in a composable or a ViewModel, so the activity does it and hands
 * down a lambda.
 */
data class ShellActions(
    val openSettingsFor: (TierBlocker) -> Unit,
    val openAutostart: () -> Unit,
    val requestNotifications: () -> Unit,
    val share: (String) -> Unit,
)

/**
 * The whole app: a back stack, a bottom bar, and the screens between them.
 *
 * The bar is drawn here rather than inside each screen, so a screen cannot
 * forget it and the four tabs are the same four everywhere. It is shown only
 * when the top of the stack is a tab; the editor, the import and the first run
 * take the full height.
 */
@Composable
fun BuildOrBreakNavGraph(startRoute: NavKey, actions: ShellActions, modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(startRoute)
    val current = backStack.lastOrNull()
    val tabIndex = TopLevelRoutes.indexOf(current)

    Column(modifier = modifier.fillMaxSize()) {
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.weight(1f),
            onBack = { backStack.removeLastOrNull() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            transitionSpec = { forward() },
            popTransitionSpec = { backward() },
            predictivePopTransitionSpec = { backward() },
            entryProvider = entries(backStack, actions),
        )

        if (tabIndex >= 0) {
            BottomNavBar(
                destinations = destinations(),
                selectedIndex = tabIndex,
                onSelect = { backStack.openTab(TopLevelRoutes[it]) },
            )
        }
    }
}

@Composable
private fun destinations() = listOf(
    NavDestination(stringResource(R.string.nav_today), Icons.Outlined.Schedule),
    NavDestination(stringResource(R.string.nav_plan), Icons.AutoMirrored.Outlined.FormatListBulleted),
    NavDestination(stringResource(R.string.nav_insights), Icons.Outlined.BarChart),
    NavDestination(stringResource(R.string.nav_settings), Icons.Outlined.Settings),
)

/**
 * Today at the bottom, the chosen tab on top, nothing in between.
 *
 * Tapping a tab never stacks tabs. A stack of Plan, Insights, Plan, Settings
 * would make the back gesture walk through every tab ever tapped, which is the
 * one behaviour every Android user has learned to hate.
 */
private fun NavBackStack<NavKey>.openTab(route: NavKey) {
    if (lastOrNull() == route) return

    clear()
    add(TodayRoute)
    if (route != TodayRoute) add(route)
}

// A registry, not logic: one entry per screen, in one place, so a missing
// screen is visible at a glance. Splitting it would hide that.
@Suppress("LongMethod")
private fun entries(backStack: NavBackStack<NavKey>, actions: ShellActions) = entryProvider<NavKey> {
    entry<OnboardingRoute> {
        OnboardingScreen(
            onRequestNotifications = actions.requestNotifications,
            onFix = actions.openSettingsFor,
            onOpenAutostart = actions.openAutostart,
            onFinished = { choice ->
                backStack.clear()
                backStack.add(TodayRoute)
                when (choice) {
                    StartChoice.PASTE -> backStack.add(ImportRoute)
                    StartChoice.WRITE -> {
                        backStack.add(PlanRoute)
                        backStack.add(ItemEditorRoute(ItemEditorRoute.NEW_ITEM))
                    }

                    StartChoice.SAMPLE -> Unit
                }
            },
        )
    }

    entry<TodayRoute> {
        TodayScreen(
            onOpenReliability = { backStack.add(ReliabilityRoute) },
            onOpenPlan = { backStack.openTab(PlanRoute) },
            onImport = { backStack.add(ImportRoute) },
            onAddStep = { backStack.add(ItemEditorRoute(ItemEditorRoute.NEW_ITEM)) },
        )
    }

    entry<PlanRoute> {
        PlanScreen(
            onEditItem = { backStack.add(ItemEditorRoute(it)) },
            onAddItem = { backStack.add(ItemEditorRoute(ItemEditorRoute.NEW_ITEM)) },
            onImport = { backStack.add(ImportRoute) },
        )
    }

    entry<InsightsRoute> {
        InsightsScreen(onEditItem = { backStack.add(ItemEditorRoute(it)) })
    }

    entry<SettingsRoute> {
        SettingsScreen(
            onOpenReliability = { backStack.add(ReliabilityRoute) },
            onImport = { backStack.add(ImportRoute) },
            onShare = actions.share,
        )
    }

    entry<ReliabilityRoute> {
        ReliabilityScreen(
            onFix = actions.openSettingsFor,
            onOpenAutostart = actions.openAutostart,
            onBack = { backStack.removeLastOrNull() },
        )
    }

    entry<ImportRoute> {
        // Straight back to Today once a plan exists. Landing on the plan
        // editor after an import would show the same list somebody has
        // just approved on the review screen.
        ImportScreen(
            onImported = {
                backStack.clear()
                backStack.add(TodayRoute)
            },
            onBack = { backStack.removeLastOrNull() },
        )
    }

    entry<ItemEditorRoute> { route ->
        ItemEditorScreen(itemId = route.itemId, onDone = { backStack.removeLastOrNull() })
    }
}

/** A new screen slides in from the right over a fade. Shared axis, in short. */
private fun <T : Any> AnimatedContentTransitionScope<Scene<T>>.forward(): ContentTransform =
    (slideInHorizontally(spring()) { it / SLIDE_FRACTION } + fadeIn(tween(FADE_MILLIS)))
        .togetherWith(slideOutHorizontally(spring()) { -it / SLIDE_FRACTION } + fadeOut(tween(FADE_MILLIS)))

private fun <T : Any> AnimatedContentTransitionScope<Scene<T>>.backward(): ContentTransform =
    (slideInHorizontally(spring()) { -it / SLIDE_FRACTION } + fadeIn(tween(FADE_MILLIS)))
        .togetherWith(slideOutHorizontally(spring()) { it / SLIDE_FRACTION } + fadeOut(tween(FADE_MILLIS)))

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
