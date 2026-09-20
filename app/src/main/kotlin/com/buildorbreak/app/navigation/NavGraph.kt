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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import com.buildorbreak.app.R
import com.buildorbreak.app.feature.about.AboutScreen
import com.buildorbreak.app.feature.about.ContactScreen
import com.buildorbreak.app.feature.about.LegalScreen
import com.buildorbreak.app.feature.goal.GoalScreen
import com.buildorbreak.app.feature.goal.ReadingsScreen
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
import com.buildorbreak.core.designsystem.component.NavRail
import com.buildorbreak.core.designsystem.component.readablePage
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
    /** The vendor screen that lets an alarm show over a locked phone. */
    val openLockScreen: () -> Unit,
    val requestNotifications: () -> Unit,
    val share: (String) -> Unit,
    /** The alarm channel's own settings: sound, vibration, do not disturb. */
    val openAlarmChannel: () -> Unit,
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

    // Wide enough for a rail: a tablet, or a phone on its side. Below this
    // the bar across the bottom is the one a thumb reaches.
    val wide = LocalConfiguration.current.screenWidthDp >= WIDE_DP
    val onSelect = { index: Int -> backStack.openTab(TopLevelRoutes[index]) }

    // The ground colour under the nav host, so a cross fade between two
    // screens never shows the window behind them.
    if (wide) {
        Row(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            if (tabIndex >= 0) {
                NavRail(destinations = destinations(), selectedIndex = tabIndex, onSelect = onSelect)
            }

            Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                NavHostPage(backStack = backStack, actions = actions)
            }
        }
    } else {
        Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            // Centred and capped, so a phone on its side reads as one column
            // of a timetable rather than as a table with the ink pushed to
            // both edges. On a portrait phone the cap never bites.
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                NavHostPage(backStack = backStack, actions = actions)
            }

            if (tabIndex >= 0) {
                BottomNavBar(destinations = destinations(), selectedIndex = tabIndex, onSelect = onSelect)
            }
        }
    }
}

/** Material's medium width class. A rail from here; a bar below it. */
private const val WIDE_DP = 840

@Composable
private fun NavHostPage(backStack: NavBackStack<NavKey>, actions: ShellActions) {
    NavDisplay(
        backStack = backStack,
        modifier = Modifier.readablePage(),
        onBack = { backStack.removeLastOrNull() },
        // Saveable state per entry, so scroll positions survive a tab
        // switch. No per entry ViewModel store on purpose: a tab tap
        // clears the stack, and a ViewModel scoped to the entry would be
        // destroyed and rebuilt on every switch, re resolving the whole
        // day and showing an empty frame first. Scoped to the activity,
        // Today and Insights come back exactly as they were left.
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
        transitionSpec = { forward() },
        popTransitionSpec = { backward() },
        predictivePopTransitionSpec = { backward() },
        entryProvider = entries(backStack, actions),
    )
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

                    // A starter already wrote its day. Today is where it is.
                    StartChoice.MORNING, StartChoice.STUDY, StartChoice.FITNESS -> Unit
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
            onOpenGoal = { backStack.add(GoalRoute) },
        )
    }

    entry<PlanRoute> {
        PlanScreen(
            onEditItem = { backStack.add(ItemEditorRoute(it)) },
            onAddItem = { templateId -> backStack.add(ItemEditorRoute(ItemEditorRoute.NEW_ITEM, templateId)) },
            onImport = { backStack.add(ImportRoute) },
        )
    }

    entry<InsightsRoute> {
        InsightsScreen(onOpenGoal = { backStack.add(GoalRoute) })
    }

    entry<GoalRoute> {
        GoalScreen(
            onOpenReadings = { backStack.add(ReadingsRoute()) },
            onAddReading = { backStack.add(ReadingsRoute(addNow = true)) },
            onBack = { backStack.removeLastOrNull() },
        )
    }

    entry<ReadingsRoute> { key ->
        ReadingsScreen(onBack = { backStack.removeLastOrNull() }, startAdding = key.addNow)
    }

    entry<SettingsRoute> {
        SettingsScreen(
            onOpenReliability = { backStack.add(ReliabilityRoute) },
            onOpenGoal = { backStack.add(GoalRoute) },
            onOpenAbout = { backStack.add(AboutRoute) },
            onOpenLegal = { backStack.add(LegalRoute(it)) },
            onImport = { backStack.add(ImportRoute) },
            onShare = actions.share,
            onOpenAlarmChannel = actions.openAlarmChannel,
        )
    }

    entry<ReliabilityRoute> {
        ReliabilityScreen(
            onFix = actions.openSettingsFor,
            onOpenAutostart = actions.openAutostart,
            onOpenLockScreen = actions.openLockScreen,
            onBack = { backStack.removeLastOrNull() },
        )
    }

    entry<AboutRoute> {
        AboutScreen(
            onOpenLegal = { backStack.add(LegalRoute(it)) },
            onOpenContact = { backStack.add(ContactRoute) },
            onBack = { backStack.removeLastOrNull() },
        )
    }

    entry<ContactRoute> {
        ContactScreen(onBack = { backStack.removeLastOrNull() })
    }

    entry<LegalRoute> { route ->
        LegalScreen(document = route.document, onBack = { backStack.removeLastOrNull() })
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
        ItemEditorScreen(
            itemId = route.itemId,
            templateId = route.templateId,
            onDone = { backStack.removeLastOrNull() },
        )
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
