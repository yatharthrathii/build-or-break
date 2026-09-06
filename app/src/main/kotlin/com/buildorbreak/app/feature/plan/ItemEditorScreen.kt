package com.buildorbreak.app.feature.plan

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buildorbreak.app.R
import com.buildorbreak.core.designsystem.component.QuietCard
import com.buildorbreak.core.designsystem.component.Rule
import com.buildorbreak.core.designsystem.theme.BuildOrBreakTheme
import com.buildorbreak.core.designsystem.theme.Theme
import com.buildorbreak.core.model.enums.AnchorType
import com.buildorbreak.core.model.enums.Salience
import com.buildorbreak.core.model.plan.Weekdays
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.collections.immutable.persistentListOf

private val CLOCK: DateTimeFormatter
    get() = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

/**
 * One step, edited.
 *
 * The anchor kind is the first choice on the screen and everything below it
 * changes to suit, because the four kinds are the one idea this app has that
 * other routine apps do not. Burying them behind an "advanced" section would
 * hide the reason somebody chose this app.
 */
@Composable
fun ItemEditorScreen(
    itemId: Long,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ItemEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(itemId) { viewModel.load(itemId) }

    ItemEditorContent(
        state = state,
        onChange = viewModel::onChange,
        onSave = { viewModel.onSave(onDone) },
        onCancel = onDone,
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemEditorContent(
    state: ItemEditorUiState,
    onChange: (ItemEditorUiState) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
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
            OutlinedTextField(
                value = state.title,
                onValueChange = { onChange(state.copy(title = it)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.editor_title_label)) },
            )

            AnchorSection(state = state, onChange = onChange)

            Rule()

            SalienceSection(state = state, onChange = onChange)

            WeekdaySection(state = state, onChange = onChange)

            Rule()

            MinimumSection(state = state, onChange = onChange)

            PinnedSection(state = state, onChange = onChange)

            Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.small)) {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.editor_cancel)) }

                Button(onClick = onSave, enabled = state.canSave) {
                    Text(stringResource(R.string.editor_save))
                }
            }
        }
    }
}

// Anchor ------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnchorSection(state: ItemEditorUiState, onChange: (ItemEditorUiState) -> Unit) {
    Text(text = stringResource(R.string.editor_when), style = MaterialTheme.typography.titleMedium)

    FlowRow(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.small)) {
        AnchorType.entries.forEach { kind ->
            FilterChip(
                selected = state.anchor.kind == kind,
                onClick = { onChange(state.copy(anchor = state.anchor.copy(kind = kind))) },
                label = { Text(stringResource(anchorLabel(kind))) },
            )
        }
    }

    when (state.anchor.kind) {
        AnchorType.FIXED -> TimeField(
            label = R.string.editor_at,
            time = state.anchor.at,
            onPicked = { onChange(state.copy(anchor = state.anchor.copy(at = it))) },
        )

        AnchorType.RELATIVE -> RelativeFields(state = state, onChange = onChange)

        AnchorType.WINDOW -> WindowFields(state = state, onChange = onChange)

        AnchorType.INTERVAL -> {
            WindowFields(state = state, onChange = onChange)

            NumberField(
                label = R.string.editor_every_minutes,
                value = state.anchor.everyMinutes,
                onValue = { onChange(state.copy(anchor = state.anchor.copy(everyMinutes = it))) },
            )
        }
    }
}

@Composable
private fun RelativeFields(state: ItemEditorUiState, onChange: (ItemEditorUiState) -> Unit) {
    NumberField(
        label = R.string.editor_offset_minutes,
        value = state.anchor.offsetMinutes,
        onValue = { onChange(state.copy(anchor = state.anchor.copy(offsetMinutes = it))) },
    )

    Text(text = stringResource(R.string.editor_after_which), style = MaterialTheme.typography.labelLarge)

    if (state.parents.isEmpty()) {
        // Nothing to hang off. Said plainly rather than shown as an empty list,
        // which would read as a bug.
        Text(
            text = stringResource(R.string.editor_no_parents),
            style = MaterialTheme.typography.bodyMedium,
            color = Theme.colours.warning,
        )
    }

    state.parents.forEach { parent ->
        FilterChip(
            selected = state.anchor.parentItemId == parent.id,
            onClick = { onChange(state.copy(anchor = state.anchor.copy(parentItemId = parent.id))) },
            label = { Text(parent.title) },
        )
    }
}

@Composable
private fun WindowFields(state: ItemEditorUiState, onChange: (ItemEditorUiState) -> Unit) {
    TimeField(
        label = R.string.editor_from,
        time = state.anchor.from,
        onPicked = { onChange(state.copy(anchor = state.anchor.copy(from = it))) },
    )

    TimeField(
        label = R.string.editor_to,
        time = state.anchor.to,
        onPicked = { onChange(state.copy(anchor = state.anchor.copy(to = it))) },
    )

    if (state.anchor.to <= state.anchor.from) {
        Text(
            text = stringResource(R.string.editor_window_backwards),
            style = MaterialTheme.typography.labelMedium,
            color = Theme.colours.warning,
        )
    }
}

// The rest ----------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SalienceSection(state: ItemEditorUiState, onChange: (ItemEditorUiState) -> Unit) {
    Text(text = stringResource(R.string.editor_how_loud), style = MaterialTheme.typography.titleMedium)

    FlowRow(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.small)) {
        Salience.entries.forEach { salience ->
            FilterChip(
                selected = state.salience == salience,
                onClick = { onChange(state.copy(salience = salience)) },
                label = { Text(stringResource(salienceLabel(salience))) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekdaySection(state: ItemEditorUiState, onChange: (ItemEditorUiState) -> Unit) {
    Text(text = stringResource(R.string.editor_which_days), style = MaterialTheme.typography.titleMedium)

    FlowRow(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.tight)) {
        DayOfWeek.entries.forEach { day ->
            val selected = day in state.weekdays

            FilterChip(
                selected = selected,
                onClick = {
                    val updated = if (selected) state.weekdays - day else state.weekdays + day
                    onChange(state.copy(weekdays = updated))
                },
                label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
            )
        }
    }

    if (state.weekdays.isEmpty) {
        // A step on no days never runs. Saying so is cheaper than letting
        // somebody wonder why it never appeared.
        Text(
            text = stringResource(R.string.editor_no_days),
            style = MaterialTheme.typography.labelMedium,
            color = Theme.colours.warning,
        )
    }
}

@Composable
private fun MinimumSection(state: ItemEditorUiState, onChange: (ItemEditorUiState) -> Unit) {
    QuietCard {
        Column(
            modifier = Modifier.padding(Theme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.small),
        ) {
            Text(text = stringResource(R.string.editor_minimum_title), style = MaterialTheme.typography.titleMedium)

            // Declared in advance because nobody having a bad day is in a state
            // to decide what a fair smaller version would be.
            Text(
                text = stringResource(R.string.editor_minimum_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = state.minimumTitle,
                onValueChange = { onChange(state.copy(minimumTitle = it)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.editor_minimum_label)) },
            )
        }
    }
}

@Composable
private fun PinnedSection(state: ItemEditorUiState, onChange: (ItemEditorUiState) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(R.string.editor_pinned_title), style = MaterialTheme.typography.titleMedium)

            Text(
                text = stringResource(R.string.editor_pinned_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Switch(checked = state.pinned, onCheckedChange = { onChange(state.copy(pinned = it)) })
    }
}

// Fields ------------------------------------------------------------------------

@Composable
private fun TimeField(@StringRes label: Int, time: LocalTime, onPicked: (LocalTime) -> Unit) {
    var picking by remember { mutableStateOf(false) }

    TextButton(onClick = { picking = true }) {
        Text("${stringResource(label)}: ${time.format(CLOCK)}")
    }

    if (picking) {
        TimePickerDialog(
            initial = time,
            onDismiss = { picking = false },
            onPicked = {
                onPicked(it)
                picking = false
            },
        )
    }
}

/**
 * The platform picker, in a plain dialog.
 *
 * `TimePicker` is still marked experimental in Material 3 and has been for
 * several releases. Writing a wheel by hand to avoid one opt in would be worse:
 * this one already handles 24 hour mode, accessibility and the user's own
 * locale, and none of that is worth reimplementing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(initial: LocalTime, onDismiss: () -> Unit, onPicked: (LocalTime) -> Unit) {
    val picker = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true,
    )

    Dialog(onDismissRequest = onDismiss) {
        QuietCard {
            Column(
                modifier = Modifier.padding(Theme.spacing.medium),
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.medium),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TimePicker(state = picker)

                Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.small)) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_cancel)) }

                    Button(onClick = { onPicked(LocalTime.of(picker.hour, picker.minute)) }) {
                        Text(stringResource(R.string.editor_set))
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberField(@StringRes label: Int, value: Int, onValue: (Int) -> Unit) {
    OutlinedTextField(
        value = value.toString(),
        // Anything unparseable becomes zero rather than being rejected, so the
        // field can be cleared and retyped. The save button is what refuses a
        // value that makes no sense.
        onValueChange = { onValue(it.filter(Char::isDigit).toIntOrNull() ?: 0) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(stringResource(label)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@StringRes
private fun anchorLabel(kind: AnchorType): Int = when (kind) {
    AnchorType.FIXED -> R.string.anchor_fixed
    AnchorType.RELATIVE -> R.string.anchor_relative
    AnchorType.WINDOW -> R.string.anchor_window
    AnchorType.INTERVAL -> R.string.anchor_interval
}

@StringRes
private fun salienceLabel(salience: Salience): Int = when (salience) {
    Salience.ALARM -> R.string.salience_alarm
    Salience.NOTIFY -> R.string.salience_notify
    Salience.SILENT -> R.string.salience_silent
    Salience.TIMELINE -> R.string.salience_timeline
}

// Preview -----------------------------------------------------------------------

@Preview(name = "Item editor", showBackground = true)
@Composable
private fun ItemEditorPreview() {
    BuildOrBreakTheme {
        ItemEditorContent(
            state = ItemEditorUiState.Empty.copy(
                title = "Study block",
                anchor = AnchorDraft(kind = AnchorType.WINDOW, from = LocalTime.of(7, 30), to = LocalTime.of(9, 30)),
                weekdays = Weekdays.MonToFri,
                minimumTitle = "Fifteen minutes",
                parents = persistentListOf(ParentChoice(1, "Wake up")),
            ),
            onChange = {},
            onSave = {},
            onCancel = {},
        )
    }
}
