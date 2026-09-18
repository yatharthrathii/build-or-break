package com.buildorbreak.app.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.buildorbreak.app.feature.about.LegalDocument
import com.buildorbreak.core.designsystem.component.Badge
import com.buildorbreak.core.designsystem.component.FillButton
import com.buildorbreak.core.designsystem.component.GhostButton
import com.buildorbreak.core.designsystem.component.HairlineRule
import com.buildorbreak.core.designsystem.component.Panel
import com.buildorbreak.core.designsystem.component.ScreenHeader
import com.buildorbreak.core.designsystem.component.SectionLabel
import com.buildorbreak.core.designsystem.component.SegmentedTabs
import com.buildorbreak.core.designsystem.component.Stepper
import com.buildorbreak.core.designsystem.theme.BuildOrBreakTheme
import com.buildorbreak.core.designsystem.theme.Theme
import com.buildorbreak.core.domain.export.BackupProblem
import com.buildorbreak.core.domain.usecase.RestoreSummary
import com.buildorbreak.core.model.enums.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TOLERANCE_STEP = 5
private const val MAX_TOLERANCE = 60
private val ToleranceWidth = 150.dp

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
    onOpenGoal: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenLegal: (LegalDocument) -> Unit,
    onImport: () -> Unit,
    onShare: (String) -> Unit,
    onOpenAlarmChannel: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    // Picking a file needs a content resolver, so the file is read here and
    // the ViewModel is handed the text. The mirror of the export, which builds
    // its text here and hands it out for somebody else to send.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var offered by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                }.getOrNull()
            }

            if (text == null) viewModel.onRestoreUnreadable() else offered = text
        }
    }

    SettingsContent(
        state = state,
        onOpenReliability = onOpenReliability,
        onOpenGoal = onOpenGoal,
        onOpenAbout = onOpenAbout,
        onOpenLegal = onOpenLegal,
        onThemeMode = viewModel::onThemeMode,
        onLateTolerance = viewModel::onLateTolerance,
        onOpenAlarmChannel = onOpenAlarmChannel,
        onExport = { viewModel.onExport(onShare) },
        onImport = onImport,
        // Anything, because a file sent through a chat app often arrives typed
        // as plain text or as nothing at all, and a picker that greys out the
        // backup somebody was just sent is a picker that cannot be used.
        onRestore = { picker.launch(arrayOf("*/*")) },
        onDismissRestore = viewModel::onDismissRestore,
        onWipe = { viewModel.onWipe { } },
        modifier = modifier,
    )

    offered?.let { text ->
        RestoreDialog(
            onConfirm = {
                offered = null
                viewModel.onRestore(text)
            },
            onDismiss = { offered = null },
        )
    }
}

@Composable
fun SettingsContent(
    state: SettingsUiState,
    onOpenReliability: () -> Unit,
    onOpenGoal: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenLegal: (LegalDocument) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onLateTolerance: (Int) -> Unit,
    onOpenAlarmChannel: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onRestore: () -> Unit = {},
    onDismissRestore: () -> Unit = {},
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
            AlarmRows(state = state, onOpenAlarmChannel = onOpenAlarmChannel, onLateTolerance = onLateTolerance)

            DayRows(state = state, onOpenGoal = onOpenGoal, onThemeMode = onThemeMode)

            SectionLabel(text = stringResource(R.string.settings_section_data), underlined = true)
            DataRows(
                state = state,
                onExport = onExport,
                onImport = onImport,
                onRestore = onRestore,
                onDelete = { confirmingWipe = true },
            )

            AboutRows(onOpenAbout = onOpenAbout, onOpenLegal = onOpenLegal)

            Footer()
        }
    }

    state.restored?.let { RestoreReport(result = it, onDismiss = onDismissRestore) }

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

/** The goal and the palette: the two things that shape a day rather than deliver it. */
@Composable
private fun DayRows(state: SettingsUiState, onOpenGoal: () -> Unit, onThemeMode: (ThemeMode) -> Unit) {
    SectionLabel(text = stringResource(R.string.settings_section_day), underlined = true)

    SettingsRow(
        title = stringResource(R.string.settings_goal),
        body = stringResource(R.string.settings_goal_body),
        onClick = onOpenGoal,
    ) { Chevron() }

    ThemeRow(mode = state.themeMode, onThemeMode = onThemeMode)
}

@Composable
private fun DataRows(
    state: SettingsUiState,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    SettingsRow(
        title = stringResource(R.string.settings_export),
        body = stringResource(R.string.settings_export_body),
        enabled = !state.exporting,
        onClick = onExport,
    ) { Chevron() }

    // Directly under the export, because the two are one feature. An export
    // with nothing that reads it is a document, not a backup.
    SettingsRow(
        title = stringResource(R.string.settings_restore),
        body = stringResource(
            if (state.restoring) R.string.settings_restore_busy else R.string.settings_restore_body,
        ),
        enabled = !state.restoring,
        onClick = onRestore,
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

/**
 * Sound and do not disturb live in the system's channel settings, so both rows
 * open that page rather than keeping a copy the app cannot enforce. The late
 * tolerance is the app's own, and the stepper writes it straight through.
 */
@Composable
private fun AlarmRows(state: SettingsUiState, onOpenAlarmChannel: () -> Unit, onLateTolerance: (Int) -> Unit) {
    SettingsRow(
        title = stringResource(R.string.settings_alarm_sound),
        body = stringResource(R.string.settings_alarm_sound_body),
        onClick = onOpenAlarmChannel,
    ) { Chevron() }

    SettingsRow(
        title = stringResource(R.string.settings_dnd),
        body = stringResource(R.string.settings_dnd_body),
        onClick = onOpenAlarmChannel,
    ) { Chevron() }

    ToleranceRow(minutes = state.lateToleranceMinutes, onLateTolerance = onLateTolerance)
}

@Composable
private fun ToleranceRow(minutes: Int, onLateTolerance: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Theme.spacing.medium, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_tolerance),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = stringResource(R.string.settings_tolerance_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Theme.spacing.tight),
            )
        }

        Stepper(
            decrementLabel = stringResource(R.string.stepper_less),
            incrementLabel = stringResource(R.string.stepper_more),
            value = if (minutes == 0) {
                stringResource(R.string.settings_tolerance_off)
            } else {
                stringResource(R.string.editor_minutes_value, minutes)
            },
            onDecrement = { onLateTolerance((minutes - TOLERANCE_STEP).coerceAtLeast(0)) },
            onIncrement = {
                onLateTolerance((minutes + TOLERANCE_STEP).coerceAtMost(MAX_TOLERANCE))
            },
            modifier = Modifier
                .padding(start = 12.dp)
                .width(ToleranceWidth),
        )
    }
}

/** What the app is, and the two documents the store asks for. */
@Composable
private fun AboutRows(onOpenAbout: () -> Unit, onOpenLegal: (LegalDocument) -> Unit) {
    SectionLabel(text = stringResource(R.string.settings_section_about), underlined = true)
    SettingsRow(
        title = stringResource(R.string.settings_about),
        body = stringResource(R.string.settings_about_body),
        onClick = onOpenAbout,
    ) { Chevron() }
    SettingsRow(title = stringResource(R.string.about_privacy), onClick = { onOpenLegal(LegalDocument.PRIVACY) }) {
        Chevron()
    }
    SettingsRow(
        title = stringResource(R.string.about_terms),
        onClick = { onOpenLegal(LegalDocument.TERMS) },
        last = true,
    ) { Chevron() }
}

/** The tier in a line, and how many things the next screen would change. */
@Composable
private fun ReliabilityRow(state: SettingsUiState, onClick: () -> Unit) {
    SettingsRow(
        title = stringResource(R.string.settings_reliability),
        body = stringResource(tierHeadline(state.tier)),
        onClick = onClick,
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
            .padding(horizontal = Theme.spacing.medium, vertical = 14.dp),
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
    Column(modifier = modifier.padding(horizontal = Theme.spacing.medium)) {
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
                        modifier = Modifier.padding(top = Theme.spacing.tight),
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
            .padding(horizontal = Theme.spacing.medium)
            .padding(top = 6.dp, bottom = 24.dp)
            .fillMaxWidth()
            .background(Theme.colours.raised)
            .padding(horizontal = Theme.spacing.inset, vertical = 12.dp),
    )
}

/**
 * The one dialog in the app, because the one thing that cannot be undone is
 * the one thing worth interrupting for.
 */
/**
 * The one question a restore has to ask.
 *
 * A restore replaces rather than merges, which is the right behaviour and the
 * surprising one, so it is said in the words it will happen in before anything
 * is touched. Same shape as the wipe, because it is the same loss.
 */
@Composable
private fun RestoreDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Panel {
            Column(modifier = Modifier.padding(Theme.spacing.medium)) {
                Text(
                    text = stringResource(R.string.settings_restore_confirm_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    text = stringResource(R.string.settings_restore_confirm_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton(text = stringResource(R.string.settings_delete_cancel), onClick = onDismiss)
                    FillButton(text = stringResource(R.string.settings_restore_confirm), onClick = onConfirm)
                }
            }
        }
    }
}

/** What came back, in the user's own numbers, or which of three things was wrong. */
@Composable
private fun RestoreReport(result: RestoreResult, onDismiss: () -> Unit) {
    val title: String
    val body: String

    when (result) {
        is RestoreResult.Done -> {
            title = stringResource(R.string.settings_restore_done_title)
            body = stringResource(R.string.settings_restore_done_body, restoredParts(result.summary))
        }

        is RestoreResult.Failed -> {
            title = stringResource(R.string.settings_restore_failed_title)
            body = stringResource(problemText(result.problem))
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Panel {
            Column(modifier = Modifier.padding(Theme.spacing.medium)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                )

                FillButton(text = stringResource(R.string.action_close), onClick = onDismiss)
            }
        }
    }
}

/**
 * "9 steps, 40 days of history and 12 readings".
 *
 * Only the parts that came back. A restore of a plan with no history yet
 * should not report "0 days of history and 0 badges": zeroes read as things
 * that went missing, which is the one thing this sentence exists to rule out.
 */
@Composable
private fun restoredParts(summary: RestoreSummary): String {
    val parts = buildList {
        add(pluralStringResource(R.plurals.settings_restore_steps, summary.steps, summary.steps))

        if (summary.days > 0) {
            add(pluralStringResource(R.plurals.settings_restore_days, summary.days, summary.days))
        }

        if (summary.readings > 0) {
            add(pluralStringResource(R.plurals.settings_restore_readings, summary.readings, summary.readings))
        }

        if (summary.badges > 0) {
            add(pluralStringResource(R.plurals.settings_restore_badges, summary.badges, summary.badges))
        }
    }

    val join = stringResource(R.string.settings_restore_join)
    val last = stringResource(R.string.settings_restore_join_last)

    return parts.reduceIndexed { index, sentence, part ->
        val pattern = if (index == parts.lastIndex) last else join

        pattern.format(sentence, part)
    }
}

private fun problemText(problem: BackupProblem): Int = when (problem) {
    BackupProblem.NOT_READABLE -> R.string.settings_restore_not_readable
    BackupProblem.TOO_NEW -> R.string.settings_restore_too_new
    BackupProblem.EMPTY -> R.string.settings_restore_empty
}

@Composable
private fun WipeDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Panel {
            Column(modifier = Modifier.padding(Theme.spacing.medium)) {
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
            onOpenGoal = {},
            onOpenAbout = {},
            onOpenLegal = {},
            onThemeMode = {},
            onLateTolerance = {},
            onOpenAlarmChannel = {},
            onExport = {},
            onImport = {},
            onWipe = {},
        )
    }
}
