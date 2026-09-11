package com.buildorbreak.app.feature.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buildorbreak.app.R
import com.buildorbreak.core.designsystem.component.FillButton
import com.buildorbreak.core.designsystem.component.GhostButton
import com.buildorbreak.core.designsystem.component.HeavyRule
import com.buildorbreak.core.designsystem.component.Kicker
import com.buildorbreak.core.designsystem.component.SegmentedTabs
import com.buildorbreak.core.designsystem.component.SquareToggle
import com.buildorbreak.core.designsystem.component.Stepper
import com.buildorbreak.core.designsystem.theme.BuildOrBreakTheme
import com.buildorbreak.core.designsystem.theme.Theme
import com.buildorbreak.core.model.enums.AnchorType
import com.buildorbreak.core.model.enums.Salience
import com.buildorbreak.core.model.plan.Weekdays
import java.time.LocalTime
import java.util.Locale
import kotlinx.collections.immutable.persistentListOf

private const val DURATION_STEP = 5
private const val MAX_DURATION = 600

/**
 * One step, edited.
 *
 * The timing kind is the first choice on the screen and everything below it
 * changes to suit, because the four kinds are the one idea this app has that
 * other routine apps do not. Burying them behind an "advanced" section would
 * hide the reason somebody chose this app.
 *
 * Save is in the header, and when it is off the line under the header says
 * why. A greyed button with no explanation was the single most confusing thing
 * about the first version of this screen.
 */
@Composable
fun ItemEditorScreen(
    itemId: Long,
    templateId: Long,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ItemEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(itemId, templateId) { viewModel.load(itemId, templateId) }

    ItemEditorContent(
        state = state,
        onChange = viewModel::onChange,
        onSave = { viewModel.onSave(onDone) },
        onArchive = { viewModel.onArchive(onDone) },
        onCancel = onDone,
        modifier = modifier,
    )
}

@Composable
fun ItemEditorContent(
    state: ItemEditorUiState,
    onChange: (ItemEditorUiState) -> Unit,
    onSave: () -> Unit,
    onArchive: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .imePadding(),
    ) {
        EditorHeader(state = state, onSave = onSave, onCancel = onCancel)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            EditorBody(state = state, onChange = onChange)

            if (!state.isNew) {
                GhostButton(
                    text = stringResource(R.string.editor_archive),
                    onClick = onArchive,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}

@Composable
private fun EditorBody(state: ItemEditorUiState, onChange: (ItemEditorUiState) -> Unit) {
    TextBox(
        label = stringResource(R.string.editor_title_label),
        value = state.title,
        onValueChange = { onChange(state.copy(title = it)) },
        placeholder = stringResource(R.string.editor_title_hint),
    )

    TextBox(
        label = stringResource(R.string.editor_detail_label),
        value = state.detail,
        onValueChange = { onChange(state.copy(detail = it)) },
        placeholder = stringResource(R.string.editor_detail_hint),
        help = stringResource(R.string.editor_detail_body),
        minLines = 2,
    )

    TimingSection(state = state, onChange = onChange)

    GroupSection(state = state, onChange = onChange)

    SalienceSection(state = state, onChange = onChange)

    WeekdaySection(state = state, onChange = onChange)

    DurationSection(state = state, onChange = onChange)

    TextBox(
        label = stringResource(R.string.editor_minimum_title),
        value = state.minimumTitle,
        onValueChange = { onChange(state.copy(minimumTitle = it)) },
        placeholder = stringResource(R.string.editor_minimum_hint),
        help = stringResource(R.string.editor_minimum_body),
    )

    MeasureSection(state = state, onChange = onChange)

    PinnedRow(state = state, onChange = onChange)

    ToggleRow(
        title = stringResource(R.string.editor_catchable_title),
        body = stringResource(R.string.editor_catchable_body),
        checked = state.catchable,
        onCheckedChange = { onChange(state.copy(catchable = it)) },
    )
}

/** Back, the title, and Save. Under it, the reason Save is off, when it is. */
@Composable
private fun EditorHeader(state: ItemEditorUiState, onSave: () -> Unit, onCancel: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(role = Role.Button, onClick = onCancel),
            )

            Text(
                text = stringResource(if (state.isNew) R.string.editor_new_title else R.string.editor_edit_title)
                    .uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
            )

            FillButton(text = stringResource(R.string.editor_save), onClick = onSave, enabled = state.canSave)
        }

        HeavyRule()

        // Why it is off comes first. A screen showing both at once is a screen
        // asking the user to work out which one to believe.
        val blocker = state.saveBlocker

        when {
            blocker != null -> BlockerLine(blocker = blocker)
            state.saveFailed -> FailureLine()
        }
    }
}

/** A write that did not land, said plainly rather than swallowed. */
@Composable
private fun FailureLine() {
    Text(
        text = stringResource(R.string.editor_save_failed),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onError,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.error)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

/** Why Save is off, on a tint, in one sentence. */
@Composable
private fun BlockerLine(blocker: SaveBlocker) {
    Text(
        text = stringResource(blockerText(blocker)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

@Composable
private fun SalienceSection(state: ItemEditorUiState, onChange: (ItemEditorUiState) -> Unit) {
    Column {
        Kicker(text = stringResource(R.string.editor_how_loud))

        SegmentedTabs(
            options = Salience.entries.map { stringResource(salienceLabel(it)) },
            selectedIndex = Salience.entries.indexOf(state.salience),
            onSelect = { onChange(state.copy(salience = Salience.entries[it])) },
            stretch = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )

        // A step in a group does not get to decide this, so the screen says so
        // rather than leaving a control that quietly does nothing.
        Text(
            text = if (state.salienceIsGroups) {
                stringResource(R.string.editor_salience_group, state.group?.title.orEmpty())
            } else {
                stringResource(salienceHint(state.salience))
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (state.salienceIsGroups) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(top = 7.dp),
        )
    }
}

@Composable
private fun WeekdaySection(state: ItemEditorUiState, onChange: (ItemEditorUiState) -> Unit) {
    Column {
        Kicker(text = stringResource(R.string.editor_which_days))

        WeekdayRow(
            weekdays = state.weekdays,
            onChange = { onChange(state.copy(weekdays = it)) },
            modifier = Modifier.padding(top = 8.dp),
        )

        if (state.weekdays.isEmpty) {
            // A step on no days never runs. Saying so is cheaper than letting
            // somebody wonder why it never appeared.
            Text(
                text = stringResource(R.string.editor_no_days),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(top = 7.dp),
            )
        }
    }
}

@Composable
private fun DurationSection(state: ItemEditorUiState, onChange: (ItemEditorUiState) -> Unit) {
    val minutes = state.durationMinutes ?: 0

    Column {
        Kicker(text = stringResource(R.string.editor_duration))

        Stepper(
            value = if (minutes == 0) {
                stringResource(R.string.editor_duration_none)
            } else {
                stringResource(R.string.editor_minutes_value, minutes)
            },
            onDecrement = { onChange(state.copy(durationMinutes = (minutes - DURATION_STEP).coerceAtLeast(0))) },
            onIncrement = {
                onChange(state.copy(durationMinutes = (minutes + DURATION_STEP).coerceAtMost(MAX_DURATION)))
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
    }
}

@Composable
private fun PinnedRow(state: ItemEditorUiState, onChange: (ItemEditorUiState) -> Unit) {
    ToggleRow(
        title = stringResource(R.string.editor_pinned_title),
        body = stringResource(R.string.editor_pinned_body),
        checked = state.pinned,
        onCheckedChange = { onChange(state.copy(pinned = it)) },
    )
}

@Composable
private fun ToggleRow(
    title: String,
    body: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp),
            )
        }

        SquareToggle(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.padding(start = Theme.spacing.medium),
        )
    }
}

// Copy lookups -----------------------------------------------------------------

private fun blockerText(blocker: SaveBlocker): Int = when (blocker) {
    SaveBlocker.NO_TITLE -> R.string.editor_reason_title
    SaveBlocker.NO_PARENT -> R.string.editor_reason_parent
    SaveBlocker.WINDOW_BACKWARDS -> R.string.editor_reason_window
}

internal fun salienceLabel(salience: Salience): Int = when (salience) {
    Salience.ALARM -> R.string.salience_alarm
    Salience.NOTIFY -> R.string.salience_notify
    Salience.SILENT -> R.string.salience_silent
    Salience.TIMELINE -> R.string.salience_timeline
}

private fun salienceHint(salience: Salience): Int = when (salience) {
    Salience.ALARM -> R.string.editor_salience_hint_alarm
    Salience.NOTIFY -> R.string.editor_salience_hint_notify
    Salience.SILENT -> R.string.editor_salience_hint_silent
    Salience.TIMELINE -> R.string.editor_salience_hint_timeline
}

// Preview -----------------------------------------------------------------------

@Preview(name = "Item editor", showBackground = true)
@Composable
private fun ItemEditorPreview() {
    BuildOrBreakTheme {
        ItemEditorContent(
            state = ItemEditorUiState.Empty.copy(
                title = "Deep work block 2",
                anchor = AnchorDraft(kind = AnchorType.RELATIVE, offsetMinutes = 30, parentItemId = 1),
                weekdays = Weekdays.MonToFri,
                minimumTitle = "Fifteen minutes",
                parents = persistentListOf(ParentChoice(1, "Lunch + walk")),
                isNew = false,
                landsAtToday = "14:00",
                durationMinutes = 50,
            ),
            onChange = {},
            onSave = {},
            onArchive = {},
            onCancel = {},
        )
    }
}

@Preview(name = "Item editor, window", showBackground = true)
@Composable
private fun ItemEditorWindowPreview() {
    BuildOrBreakTheme {
        ItemEditorContent(
            state = ItemEditorUiState.Empty.copy(
                anchor = AnchorDraft(kind = AnchorType.WINDOW, from = LocalTime.of(18, 0), to = LocalTime.of(20, 0)),
            ),
            onChange = {},
            onSave = {},
            onArchive = {},
            onCancel = {},
        )
    }
}
