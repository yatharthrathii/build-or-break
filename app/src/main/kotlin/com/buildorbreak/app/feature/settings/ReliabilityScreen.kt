package com.buildorbreak.app.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buildorbreak.app.R
import com.buildorbreak.core.designsystem.component.QuietCard
import com.buildorbreak.core.designsystem.component.Rule
import com.buildorbreak.core.designsystem.theme.BuildOrBreakTheme
import com.buildorbreak.core.designsystem.theme.Theme
import com.buildorbreak.core.model.enums.DeliveryTier
import com.buildorbreak.scheduler.alarm.TierBlocker
import kotlinx.collections.immutable.persistentListOf

/**
 * Why an alarm might not arrive, and what to do about it.
 *
 * The whole screen is written from the user's side. Nothing here says
 * "SCHEDULE_EXACT_ALARM" or names a tier, because somebody reading this at seven
 * in the morning after a missed alarm wants to know whether it will happen again
 * and what to press.
 */
@Composable
fun ReliabilityScreen(
    onFix: (TierBlocker) -> Unit,
    onOpenAutostart: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReliabilityViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Android has no callback for a permission being revoked, so the only
    // reliable moment to re read is coming back from settings.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    ReliabilityContent(
        state = state,
        onFix = onFix,
        onOpenAutostart = onOpenAutostart,
        modifier = modifier,
    )
}

@Composable
fun ReliabilityContent(
    state: ReliabilityUiState,
    onFix: (TierBlocker) -> Unit,
    onOpenAutostart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { insets ->
        Column(
            modifier = Modifier
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(Theme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.medium),
        ) {
            Text(
                text = stringResource(headlineFor(state.tier)),
                style = MaterialTheme.typography.headlineMedium,
            )

            Text(
                text = stringResource(bodyFor(state.tier)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.blockers.isNotEmpty()) {
                Rule()

                Text(
                    text = stringResource(R.string.reliability_what_would_change),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            state.blockers.forEach { blocker ->
                FixCard(blocker = blocker, onFix = onFix)
            }

            if (state.needsAutostart) {
                AutostartCard(onOpenAutostart = onOpenAutostart)
            }
        }
    }
}

@Composable
private fun FixCard(blocker: TierBlocker, onFix: (TierBlocker) -> Unit) {
    QuietCard {
        Column(
            modifier = Modifier.padding(Theme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.small),
        ) {
            Text(text = stringResource(whatFor(blocker)), style = MaterialTheme.typography.titleMedium)

            Text(
                text = stringResource(whyFor(blocker)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(onClick = { onFix(blocker) }) { Text(stringResource(actionFor(blocker))) }
        }
    }
}

/**
 * The switch no API can see the state of.
 *
 * Every vendor battery manager keeps its own autostart list, none of them are
 * reachable through the framework, and `PowerManager` knows nothing about them.
 * So this card offers the screen and says plainly that the app cannot check
 * whether it was done. Claiming to know would be worse than admitting the gap.
 */
@Composable
private fun AutostartCard(onOpenAutostart: () -> Unit) {
    QuietCard {
        Column(
            modifier = Modifier.padding(Theme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.small),
        ) {
            Text(text = stringResource(R.string.autostart_title), style = MaterialTheme.typography.titleMedium)

            Text(
                text = stringResource(R.string.autostart_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(onClick = onOpenAutostart) { Text(stringResource(R.string.autostart_action)) }
        }
    }
}

// Copy lookups -----------------------------------------------------------------
//
// Kept as small mappings rather than fields on the state, so the ViewModel stays
// free of user visible words and the whole screen can be translated by editing
// one resource file.

@StringRes
private fun headlineFor(tier: DeliveryTier): Int = when (tier) {
    DeliveryTier.FULL_SCREEN_ALARM -> R.string.tier_full_screen_headline
    DeliveryTier.EXACT_HEADS_UP -> R.string.tier_heads_up_headline
    DeliveryTier.INEXACT_NOTIFICATION -> R.string.tier_inexact_headline
    DeliveryTier.IN_APP_ONLY -> R.string.tier_in_app_headline
}

@StringRes
private fun bodyFor(tier: DeliveryTier): Int = when (tier) {
    DeliveryTier.FULL_SCREEN_ALARM -> R.string.tier_full_screen_body
    DeliveryTier.EXACT_HEADS_UP -> R.string.tier_heads_up_body
    DeliveryTier.INEXACT_NOTIFICATION -> R.string.tier_inexact_body
    DeliveryTier.IN_APP_ONLY -> R.string.tier_in_app_body
}

@StringRes
private fun whatFor(blocker: TierBlocker): Int = when (blocker) {
    TierBlocker.NOTIFICATIONS_DENIED -> R.string.fix_notifications_what
    TierBlocker.CHANNEL_SILENCED -> R.string.fix_channel_what
    TierBlocker.EXACT_ALARMS_DENIED -> R.string.fix_exact_what
    TierBlocker.FULL_SCREEN_INTENT_DENIED -> R.string.fix_full_screen_what
    TierBlocker.BATTERY_OPTIMISED -> R.string.fix_battery_what
}

@StringRes
private fun whyFor(blocker: TierBlocker): Int = when (blocker) {
    TierBlocker.NOTIFICATIONS_DENIED -> R.string.fix_notifications_why
    TierBlocker.CHANNEL_SILENCED -> R.string.fix_channel_why
    TierBlocker.EXACT_ALARMS_DENIED -> R.string.fix_exact_why
    TierBlocker.FULL_SCREEN_INTENT_DENIED -> R.string.fix_full_screen_why
    TierBlocker.BATTERY_OPTIMISED -> R.string.fix_battery_why
}

@StringRes
private fun actionFor(blocker: TierBlocker): Int = when (blocker) {
    TierBlocker.NOTIFICATIONS_DENIED, TierBlocker.CHANNEL_SILENCED -> R.string.fix_open_notification_settings
    TierBlocker.EXACT_ALARMS_DENIED -> R.string.fix_open_alarm_settings
    TierBlocker.FULL_SCREEN_INTENT_DENIED -> R.string.fix_open_app_settings
    TierBlocker.BATTERY_OPTIMISED -> R.string.fix_open_battery_settings
}

// Previews ---------------------------------------------------------------------

@Preview(name = "Reliability, degraded", showBackground = true)
@Composable
private fun ReliabilityPreview() {
    BuildOrBreakTheme {
        ReliabilityContent(
            state = ReliabilityUiState(
                tier = DeliveryTier.INEXACT_NOTIFICATION,
                blockers = persistentListOf(TierBlocker.EXACT_ALARMS_DENIED, TierBlocker.BATTERY_OPTIMISED),
                needsAutostart = true,
            ),
            onFix = {},
            onOpenAutostart = {},
        )
    }
}

@Preview(name = "Reliability, all good", showBackground = true)
@Composable
private fun ReliabilityHealthyPreview() {
    BuildOrBreakTheme {
        ReliabilityContent(
            state = ReliabilityUiState(
                tier = DeliveryTier.FULL_SCREEN_ALARM,
                blockers = persistentListOf(),
                needsAutostart = false,
            ),
            onFix = {},
            onOpenAutostart = {},
        )
    }
}
