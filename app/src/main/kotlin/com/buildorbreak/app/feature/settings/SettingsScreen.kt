package com.buildorbreak.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buildorbreak.app.BuildConfig
import com.buildorbreak.app.R
import com.buildorbreak.core.designsystem.component.Badge
import com.buildorbreak.core.designsystem.component.FillButton
import com.buildorbreak.core.designsystem.component.GhostButton
import com.buildorbreak.core.designsystem.component.HairlineRule
import com.buildorbreak.core.designsystem.component.Panel
import com.buildorbreak.core.designsystem.component.ScreenHeader
import com.buildorbreak.core.designsystem.component.SectionLabel
import com.buildorbreak.core.designsystem.component.SegmentedTabs
import com.buildorbreak.core.designsystem.theme.BuildOrBreakTheme
import com.buildorbreak.core.designsystem.theme.Theme
import com.buildorbreak.core.model.enums.ThemeMode

/**
 * Plain rows, no chrome.
 *
 * Every row here either opens a screen, flips a setting, or does something to
 * the data, and says which in its subtitle. There are no rows for things the
 * app does not do yet: a setting with no effect behind it is a lie with a
 * switch on it.
 */
@Composable
fun SettingsScreen(
    onOpenReliability: () -> Unit,
    onImport: () -> Unit,
    onShare: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    SettingsContent(
        state = state,
        onOpenReliability = onOpenReliability,
        onThemeMode = viewModel::onThemeMode,
        onExport = { viewModel.onExport(onShare) },
        onImport = onImport,
        onWipe = { viewModel.onWipe { } },
        modifier = modifier,
    )
}

@Composable
fun SettingsContent(
    state: SettingsUiState,
    onOpenReliability: () -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onWipe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmingWipe by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding(),
    ) {
        ScreenHeader(
            kicker = stringResource(R.string.settings_kicker, BuildConfig.VERSION_NAME),
            title = stringResource(R.string.settings_title),
        )

        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            SectionLabel(text = stringResource(R.string.settings_section_alarms), underlined = true)
            ReliabilityRow(state = state, onClick = onOpenReliability)

            SectionLabel(text = stringResource(R.string.settings_section_day), underlined = true)
            ThemeRow(mode = state.themeMode, onThemeMode = onThemeMode)

            SectionLabel(text = stringResource(R.string.settings_section_data), underlined = true)
            DataRows(
                exporting = state.exporting,
                onExport = onExport,
                onImport = onImport,
                onDelete = { confirmingWipe = true },
            )

            Footer()
        }
    }

    if (confirmingWipe) {
        WipeDialog(
            onConfirm = {
                confirmingWipe = false
                onWipe()
            },
            onDismiss = { confirmingWipe = false },
        )
    }
}

@Composable
private fun DataRows(
    exporting: Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDelete: () -> Unit,
) {
    SettingsRow(
        title = stringResource(R.string.settings_export),
        body = stringResource(R.string.settings_export_body),
        enabled = !exporting,
        onClick = onExport,
    ) { Chevron() }

    SettingsRow(title = stringResource(R.string.settings_import), onClick = onImport) { Chevron() }

    SettingsRow(
        title = stringResource(R.string.settings_delete),
        body = stringResource(R.string.settings_delete_body),
        titleColor = MaterialTheme.colorScheme.error,
        onClick = onDelete,
        last = true,
    )
}

/** The tier in a line, and how many things the next screen would change. */
@Composable
private fun ReliabilityRow(state: SettingsUiState, onClick: () -> Unit) {
    SettingsRow(
        title = stringResource(R.string.settings_reliability),
        body = stringResource(tierHeadline(state.tier)),
        onClick = onClick,
        last = true,
    ) {
        if (state.fixCount > 0) {
            Badge(text = pluralStringResource(R.plurals.settings_fixes, state.fixCount, state.fixCount), accent = true)
        }
        Chevron()
    }
}

@Composable
private fun ThemeRow(mode: ThemeMode, onThemeMode: (ThemeMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.settings_theme),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )

        SegmentedTabs(
            options = ThemeMode.entries.map { stringResource(themeLabel(it)) },
            selectedIndex = ThemeMode.entries.indexOf(mode),
            onSelect = { onThemeMode(ThemeMode.entries[it]) },
        )
    }
}

@Composable
private fun SettingsRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    body: String? = null,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    last: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = titleColor)

                body?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }

            trailing()
        }

        if (!last) HairlineRule()
    }
}

@Composable
private fun Chevron() {
    Icon(
        imageVector = Icons.Outlined.ChevronRight,
        contentDescription = null,
        tint = Theme.colours.faint,
        modifier = Modifier.size(18.dp),
    )
}

@Composable
private fun Footer() {
    Text(
        text = stringResource(R.string.settings_footer),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(top = 6.dp, bottom = 24.dp)
            .fillMaxWidth()
            .background(Theme.colours.raised)
            .padding(horizontal = 13.dp, vertical = 12.dp),
    )
}

/**
 * The one dialog in the app, because the one thing that cannot be undone is
 * the one thing worth interrupting for.
 */
@Composable
private fun WipeDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Panel {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_delete_confirm_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    text = stringResource(R.string.settings_delete_confirm_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton(text = stringResource(R.string.settings_delete_cancel), onClick = onDismiss)
                    FillButton(text = stringResource(R.string.settings_delete_confirm), onClick = onConfirm)
                }
            }
        }
    }
}

private fun themeLabel(mode: ThemeMode): Int = when (mode) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

@Preview(name = "Settings", showBackground = true)
@Composable
private fun SettingsPreview() {
    BuildOrBreakTheme {
        SettingsContent(
            state = SettingsUiState.Initial.copy(fixCount = 1),
            onOpenReliability = {},
            onThemeMode = {},
            onExport = {},
            onImport = {},
            onWipe = {},
        )
    }
}
