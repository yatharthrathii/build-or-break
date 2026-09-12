package com.buildorbreak.app.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buildorbreak.app.R
import com.buildorbreak.core.designsystem.component.FillButton
import com.buildorbreak.core.designsystem.component.HairlineRule
import com.buildorbreak.core.designsystem.component.HeavyRule
import com.buildorbreak.core.designsystem.component.Kicker
import com.buildorbreak.core.designsystem.component.OutlineButton
import com.buildorbreak.core.designsystem.component.SectionLabel
import com.buildorbreak.core.designsystem.theme.BuildOrBreakTheme
import com.buildorbreak.core.designsystem.theme.Theme
import com.buildorbreak.core.designsystem.theme.TimeStyle
import com.buildorbreak.core.domain.review.DeliveryStats
import com.buildorbreak.core.model.enums.DeliveryTier
import com.buildorbreak.scheduler.alarm.TierBlocker
import java.util.Locale
import kotlinx.collections.immutable.persistentListOf

/**
 * What the alarms actually did, counted rather than claimed.
 *
 * The rest of this screen explains what the app is permitted to do. This is
 * the evidence. Every figure is a plain count somebody can check against
 * their own week, and lateness is kept separate from a miss because they are
 * different problems with different causes and only one of them is usually
 * the user's to fix.
 */
@Composable
private fun MeasuredSection(stats: DeliveryStats) {
    SectionLabel(text = stringResource(R.string.reliability_measured), underlined = true)

    if (!stats.hasData) {
        Text(
            text = stringResource(R.string.reliability_measured_none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Theme.spacing.medium, vertical = 12.dp),
        )

        return
    }

    Column(modifier = Modifier.padding(horizontal = Theme.spacing.medium, vertical = 4.dp)) {
        Text(
            text = pluralStringResource(R.plurals.reliability_measured_window, stats.days, stats.days),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        MeasuredRow(
            label = stringResource(R.string.reliability_on_time),
            value = stringResource(R.string.reliability_of, stats.onTime, stats.scheduled),
            accent = true,
        )
        MeasuredRow(
            label = stringResource(R.string.reliability_late),
            value = stats.late.toString(),
            note = stats.medianLateSeconds?.let {
                stringResource(R.string.reliability_typically_late, it / SECONDS_PER_MINUTE)
            },
        )
        MeasuredRow(
            label = stringResource(R.string.reliability_never_arrived),
            value = stats.missed.toString(),
            note = stringResource(R.string.reliability_never_arrived_body).takeIf { stats.missed > 0 },
        )
    }
}

@Composable
private fun MeasuredRow(
    label: String,
    value: String,
    note: String? = null,
    accent: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Theme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            note?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        Text(
            text = value,
            style = TimeStyle,
            color = if (accent) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }

    HairlineRule()
}

private const val SECONDS_PER_MINUTE = 60

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
    onOpenLockScreen: () -> Unit,
    onBack: () -> Unit,
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
        onOpenLockScreen = onOpenLockScreen,
        onAutostartDone = viewModel::onAutostartDone,
        onLockScreenDone = viewModel::onLockScreenDone,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun ReliabilityContent(
    state: ReliabilityUiState,
    onFix: (TierBlocker) -> Unit,
    onOpenAutostart: () -> Unit,
    onOpenLockScreen: () -> Unit,
    onAutostartDone: () -> Unit,
    onLockScreenDone: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding(),
    ) {
        BackHeader(title = stringResource(R.string.reliability_title), onBack = onBack)

        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            TierSummary(tier = state.tier)

            MeasuredSection(stats = state.measured)

            if (state.blockers.isNotEmpty() || state.needsAutostart || state.needsLockScreen) {
                SectionLabel(text = stringResource(R.string.reliability_what_would_change), underlined = true)
            }

            state.blockers.forEach { blocker ->
                FixRow(
                    what = whatFor(blocker),
                    why = whyFor(blocker),
                    action = actionFor(blocker),
                    onAction = { onFix(blocker) },
                )
            }

            VendorRows(
                state = state,
                onOpenLockScreen = onOpenLockScreen,
                onOpenAutostart = onOpenAutostart,
                onLockScreenDone = onLockScreenDone,
                onAutostartDone = onAutostartDone,
            )
        }
    }
}

/**
 * The two switches no API can read.
 *
 * Both live on a vendor screen, both decide whether an alarm arrives, and
 * neither can be checked from code. So each offers the screen and a way for
 * the user to say they have done it, which is the only source of truth there
 * is for either.
 */
@Composable
private fun VendorRows(
    state: ReliabilityUiState,
    onOpenLockScreen: () -> Unit,
    onOpenAutostart: () -> Unit,
    onLockScreenDone: () -> Unit,
    onAutostartDone: () -> Unit,
) {
    if (state.needsLockScreen) {
        // The one that decides whether an alarm can be answered
        // without unlocking. Above autostart because a missed alarm
        // is worse than a late one.
        FixRow(
            what = R.string.lock_screen_title,
            why = R.string.lock_screen_body,
            action = R.string.lock_screen_action,
            onAction = onOpenLockScreen,
            secondary = R.string.perm_autostart_done,
            onSecondary = onLockScreenDone,
        )
    }

    if (state.needsAutostart) {
        // The switch no API can see the state of. The app cannot tick
        // it off, so the user is given the way to.
        FixRow(
            what = R.string.autostart_title,
            why = R.string.autostart_body,
            action = R.string.autostart_action,
            onAction = onOpenAutostart,
            secondary = R.string.perm_autostart_done,
            onSecondary = onAutostartDone,
        )
    }
}

@Composable
private fun TierSummary(tier: DeliveryTier) {
    Column(modifier = Modifier.padding(horizontal = Theme.spacing.medium, vertical = 18.dp)) {
        Kicker(text = stringResource(R.string.reliability_kicker), color = MaterialTheme.colorScheme.primary)

        Text(
            text = stringResource(tierHeadline(tier)).uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp),
        )

        Text(
            text = stringResource(tierBody(tier)),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/** A back arrow and a title, on a heavy rule. Shared by the screens that stack. */
@Composable
internal fun BackHeader(title: String, onBack: () -> Unit) {
    Column {
        Row(
            modifier = Modifier.padding(horizontal = Theme.spacing.medium, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(role = Role.Button, onClick = onBack),
            )

            Text(
                text = title.uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = Theme.spacing.inset),
            )
        }

        HeavyRule()
    }
}

@Composable
private fun FixRow(
    @StringRes what: Int,
    @StringRes why: Int,
    @StringRes action: Int,
    onAction: () -> Unit,
    @StringRes secondary: Int? = null,
    onSecondary: () -> Unit = {},
) {
    Column(modifier = Modifier.padding(horizontal = Theme.spacing.medium, vertical = 15.dp)) {
        Text(
            text = stringResource(what),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Text(
            text = stringResource(why),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = Theme.spacing.inset),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FillButton(text = stringResource(action), onClick = onAction)

            secondary?.let { OutlineButton(text = stringResource(it), onClick = onSecondary, muted = true) }
        }
    }

    HairlineRule(Modifier.padding(horizontal = Theme.spacing.medium))
}

// Copy lookups -----------------------------------------------------------------

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
            onOpenLockScreen = {},
            onAutostartDone = {},
            onLockScreenDone = {},
            onBack = {},
        )
    }
}
