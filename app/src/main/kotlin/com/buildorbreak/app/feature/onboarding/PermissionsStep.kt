package com.buildorbreak.app.feature.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.buildorbreak.app.R
import com.buildorbreak.app.feature.settings.tierShortText
import com.buildorbreak.core.designsystem.component.FillButton
import com.buildorbreak.core.designsystem.component.HairlineRule
import com.buildorbreak.core.designsystem.component.HeavyRule
import com.buildorbreak.core.designsystem.component.OutlineButton
import com.buildorbreak.core.designsystem.theme.Theme
import com.buildorbreak.scheduler.alarm.TierBlocker
import java.util.Locale

private val RowIconSize = 20.dp
private val RowIndent = 31.dp

/**
 * Three permissions, one reason each, and a fourth row on phones that need it.
 *
 * Each row says what the permission is for in one sentence, then offers the
 * dialog or the settings screen. Granted rows say so and stop asking. Nothing
 * here is required: the footer button works whatever the state, and the line
 * at the bottom says plainly what the app can do right now.
 */
@Composable
internal fun PermissionsStep(
    facts: PermissionFacts,
    onRequestNotifications: () -> Unit,
    onFix: (TierBlocker) -> Unit,
    onOpenAutostart: () -> Unit,
    onAutostartDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 22.dp, bottom = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.onboarding_permissions_title).uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Text(
            text = stringResource(R.string.onboarding_permissions_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 9.dp, bottom = 20.dp),
        )

        HeavyRule()

        PermissionRows(
            facts = facts,
            onRequestNotifications = onRequestNotifications,
            onFix = onFix,
            onOpenAutostart = onOpenAutostart,
            onAutostartDone = onAutostartDone,
        )

        RightNow(facts = facts)
    }
}

@Composable
private fun PermissionRows(
    facts: PermissionFacts,
    onRequestNotifications: () -> Unit,
    onFix: (TierBlocker) -> Unit,
    onOpenAutostart: () -> Unit,
    onAutostartDone: () -> Unit,
) {
    PermissionRow(
        icon = Icons.Outlined.Alarm,
        title = R.string.perm_alarms_title,
        body = R.string.perm_alarms_body,
        state = facts.exactAlarms,
        action = R.string.perm_allow,
        onAction = { onFix(TierBlocker.EXACT_ALARMS_DENIED) },
    )

    PermissionRow(
        icon = Icons.Outlined.Notifications,
        title = R.string.perm_notifications_title,
        body = R.string.perm_notifications_body,
        state = facts.notifications,
        action = R.string.perm_allow,
        onAction = onRequestNotifications,
    )

    PermissionRow(
        icon = Icons.Outlined.BatteryAlert,
        title = R.string.perm_battery_title,
        body = R.string.perm_battery_body,
        state = facts.battery,
        action = R.string.perm_open_settings,
        onAction = { onFix(TierBlocker.BATTERY_OPTIMISED) },
    )

    if (facts.autostart) {
        // No API can read this one, so the app cannot tick it off. The user
        // can, and until somebody does the row keeps offering the screen.
        PermissionRow(
            icon = Icons.Outlined.PowerSettingsNew,
            title = R.string.perm_autostart_title,
            body = R.string.perm_autostart_body,
            state = PermissionState.NEEDED,
            action = R.string.perm_open_settings,
            onAction = onOpenAutostart,
            secondary = R.string.perm_autostart_done,
            onSecondary = onAutostartDone,
        )
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    @StringRes title: Int,
    @StringRes body: Int,
    state: PermissionState,
    @StringRes action: Int,
    onAction: () -> Unit,
    @StringRes secondary: Int? = null,
    onSecondary: () -> Unit = {},
) {
    Column(modifier = Modifier.padding(vertical = 15.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .size(RowIconSize)
                    .padding(top = 1.dp),
            )

            Column(modifier = Modifier.padding(start = 11.dp)) {
                Text(
                    text = stringResource(title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    text = stringResource(body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        Row(
            modifier = Modifier.padding(start = RowIndent, top = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state == PermissionState.GRANTED) {
                Granted()
            } else {
                FillButton(text = stringResource(action), onClick = onAction)

                secondary?.let { OutlineButton(text = stringResource(it), onClick = onSecondary, muted = true) }
            }
        }
    }

    HairlineRule()
}

@Composable
private fun Granted() {
    OutlineButton(text = stringResource(R.string.perm_granted), onClick = {}, enabled = false)
}

/** The honest line: what will actually reach the user with things as they are. */
@Composable
private fun RightNow(facts: PermissionFacts) {
    Text(
        text = stringResource(R.string.onboarding_right_now, stringResource(tierShortText(facts.tier))),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .background(Theme.colours.raised)
            .padding(horizontal = 13.dp, vertical = 12.dp),
    )

    Text(
        text = stringResource(R.string.onboarding_privacy_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp),
    )
}
