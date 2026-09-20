package com.buildorbreak.app.feature.goal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buildorbreak.app.R
import com.buildorbreak.app.feature.about.BackHeader
import com.buildorbreak.core.designsystem.component.EmptyState
import com.buildorbreak.core.designsystem.component.FillButton
import com.buildorbreak.core.designsystem.component.GhostButton
import com.buildorbreak.core.designsystem.component.HairlineRule
import com.buildorbreak.core.designsystem.component.Kicker
import com.buildorbreak.core.designsystem.component.OutlineButton
import com.buildorbreak.core.designsystem.component.Panel
import com.buildorbreak.core.designsystem.component.Stepper
import com.buildorbreak.core.designsystem.theme.BuildOrBreakTheme
import com.buildorbreak.core.designsystem.theme.Theme
import com.buildorbreak.core.designsystem.theme.TimeStyle
import com.buildorbreak.core.model.enums.GoalKind
import com.buildorbreak.core.model.enums.ValueKind
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.collections.immutable.persistentListOf

/**
 * "Fri 12 Sep". Long enough to place the day, short enough for a list.
 *
 * A function rather than a value, because a value is read once when the
 * class loads and would keep the locale the app started in. Somebody who
 * switches the phone to Hindi and comes back would find the dates still in
 * English until the process was killed.
 */
private fun rowDate(): DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

/**
 * Every number behind the goal, and a way to fix one.
 *
 * The goal card shows a seven day average, which is the honest number and
 * also an invisible one: somebody who weighed 50.5 and reads 49.8 has to
 * take the app's word for it. This is the working. It is also the only place
 * in the app where a number already written down can be changed, which is
 * why the row says the date first: the question being answered is always
 * "which day did I get wrong", never "which value".
 */
@Composable
fun ReadingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Opened from the goal's "add a reading", which means the editor, not the list. */
    startAdding: Boolean = false,
    viewModel: ReadingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Once the series has arrived, so the box opens with whatever the day
    // already holds rather than empty and then changing under the thumb.
    LaunchedEffect(startAdding, state.loaded) {
        if (startAdding && state.loaded) viewModel.onAdd()
    }

    ReadingsContent(
        state = state,
        onAdd = viewModel::onAdd,
        onEdit = viewModel::onEdit,
        onShiftDay = viewModel::onShiftDay,
        onTyped = viewModel::onTyped,
        onSave = viewModel::onSave,
        onDelete = viewModel::onDelete,
        onCancel = viewModel::onCancel,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun ReadingsContent(
    state: ReadingsUiState,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onShiftDay: (Long) -> Unit,
    onTyped: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .imePadding(),
    ) {
        BackHeader(
            kicker = pluralStringResource(R.plurals.readings_count, state.rows.size, state.rows.size),
            title = stringResource(R.string.readings_title),
            onBack = onBack,
        )

        when {
            state.rows.isNotEmpty() -> ReadingList(state = state, onAdd = onAdd, onEdit = onEdit)
            state.loaded -> NoReadings(canAdd = state.canAdd, onAdd = onAdd)
            // Nothing, rather than an empty state, before the first read.
            else -> Unit
        }
    }

    state.editing?.let { draft ->
        EditDialog(
            draft = draft,
            unit = state.valueKind,
            failed = state.failed,
            onShiftDay = onShiftDay,
            onTyped = onTyped,
            onSave = onSave,
            onDelete = onDelete,
            onCancel = onCancel,
        )
    }
}

@Composable
private fun ReadingList(state: ReadingsUiState, onAdd: () -> Unit, onEdit: (Long) -> Unit) {
    val unit = stringResource(goalUnit(state.valueKind, GoalKind.NUMBER))

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                text = stringResource(R.string.readings_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = Theme.spacing.medium,
                    end = Theme.spacing.medium,
                    top = Theme.spacing.medium,
                    bottom = Theme.spacing.small,
                ),
            )

            if (state.canAdd) {
                OutlineButton(
                    text = stringResource(R.string.readings_add),
                    onClick = onAdd,
                    modifier = Modifier.padding(
                        start = Theme.spacing.medium,
                        bottom = Theme.spacing.small,
                    ),
                )
            }
        }

        items(items = state.rows, key = { it.id }) { row ->
            ReadingRowView(row = row, unit = unit, onEdit = onEdit)
        }
    }
}

@Composable
private fun ReadingRowView(row: ReadingRow, unit: String, onEdit: (Long) -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { onEdit(row.id) }
                .padding(horizontal = Theme.spacing.medium, vertical = Theme.spacing.inset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.date.format(rowDate()),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                if (row.isToday) {
                    Kicker(
                        text = stringResource(R.string.readings_today),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = Theme.spacing.tight),
                    )
                }
            }

            Text(
                text = format(row.value) + " " + unit,
                style = TimeStyle,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        HairlineRule()
    }
}

/**
 * One number, changed or removed.
 *
 * A dialog rather than a strip, against the rule the rest of the app keeps,
 * because this one is not something the app noticed: it is a thing the user
 * asked to do to a specific row, and it has to end in a decision.
 */
@Composable
private fun EditDialog(
    draft: ReadingDraft,
    unit: ValueKind,
    failed: Boolean,
    onShiftDay: (Long) -> Unit,
    onTyped: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    Dialog(onDismissRequest = onCancel) {
        Panel {
            ReadingEditor(
                draft = draft,
                unit = unit,
                failed = failed,
                onShiftDay = onShiftDay,
                onTyped = onTyped,
                onSave = onSave,
                onDelete = onDelete,
                onCancel = onCancel,
            )
        }
    }
}

/**
 * The day the number is for.
 *
 * Fixed when a row is being corrected, because the question there is which
 * value was wrong rather than which day. Stepped a day at a time when one is
 * being added: the day being looked for is today or the one before it, and a
 * month grid is a lot of screen for a question with two likely answers.
 */
@Composable
private fun WhichDay(draft: ReadingDraft, onShiftDay: (Long) -> Unit) {
    if (!draft.isNew) {
        Kicker(text = draft.date.format(rowDate()))
        return
    }

    Kicker(text = stringResource(R.string.readings_add_day))

    Stepper(
        value = draft.date.format(rowDate()),
        onDecrement = { onShiftDay(-1) },
        onIncrement = { onShiftDay(1) },
        modifier = Modifier.fillMaxWidth().padding(top = Theme.spacing.small),
        decrementLabel = stringResource(R.string.readings_day_earlier),
        incrementLabel = stringResource(R.string.readings_day_later),
    )
}

/** A line under the box: what saving will replace, or why it did not save. */
@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.padding(top = Theme.spacing.small),
    )
}

/**
 * The inside of the editor, without the window around it.
 *
 * Separate so it can be tested. A `Dialog` opens a window of its own, and
 * under Robolectric that window never reaches idle, so a test of the editor
 * through the dialog times out on the frame clock rather than on anything
 * this app does.
 */
@Composable
internal fun ReadingEditor(
    draft: ReadingDraft,
    unit: ValueKind,
    failed: Boolean,
    onShiftDay: (Long) -> Unit,
    onTyped: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(modifier = Modifier.padding(Theme.spacing.medium)) {
        WhichDay(draft = draft, onShiftDay = onShiftDay)

        NumberBox(typed = draft.typed, unit = stringResource(goalUnit(unit, GoalKind.NUMBER)), onTyped = onTyped)

        if (draft.isNew && draft.replaces) Note(text = stringResource(R.string.readings_replaces))

        if (failed) Note(text = stringResource(R.string.readings_failed))

        Text(
            text = stringResource(
                if (draft.isNew) R.string.readings_rebuilds_new else R.string.readings_rebuilds,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = Theme.colours.faint,
            modifier = Modifier.padding(top = Theme.spacing.inset),
        )

        DialogActions(
            canSave = draft.canSave,
            canDelete = draft.canDelete,
            onSave = onSave,
            onDelete = onDelete,
            onCancel = onCancel,
        )
    }
}

@Composable
private fun DialogActions(
    canSave: Boolean,
    canDelete: Boolean,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Theme.spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FillButton(text = stringResource(R.string.readings_save), onClick = onSave, enabled = canSave)
        OutlineButton(text = stringResource(R.string.action_cancel), onClick = onCancel, muted = true)

        Box(modifier = Modifier.weight(1f))

        if (canDelete) {
            GhostButton(
                text = stringResource(R.string.readings_delete),
                onClick = onDelete,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun NumberBox(typed: String, unit: String, onTyped: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Theme.spacing.small)
            .border(Theme.spacing.rule, MaterialTheme.colorScheme.onSurface)
            .background(Theme.colours.raised)
            .padding(horizontal = Theme.spacing.inset, vertical = Theme.spacing.inset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = typed,
            onValueChange = onTyped,
            textStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )

        Text(
            text = unit,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NoReadings(canAdd: Boolean, onAdd: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        EmptyState(
            title = stringResource(R.string.readings_none_title),
            body = stringResource(R.string.readings_none_body),
        )

        if (canAdd) {
            OutlineButton(text = stringResource(R.string.readings_add), onClick = onAdd)
        }
    }
}

@Preview(name = "Readings", showBackground = true)
@Composable
private fun ReadingsPreview() {
    BuildOrBreakTheme {
        ReadingsContent(
            state = ReadingsUiState(
                loaded = true,
                title = "Gain three kilos",
                valueKind = ValueKind.WEIGHT_KG,
                rows = persistentListOf(
                    ReadingRow(3, LocalDate.of(2026, 9, 12), 50.5, isToday = true),
                    ReadingRow(2, LocalDate.of(2026, 9, 11), 49.8, isToday = false),
                    ReadingRow(1, LocalDate.of(2026, 9, 10), 50.1, isToday = false),
                ),
                canAdd = true,
            ),
            onAdd = {},
            onEdit = {},
            onShiftDay = {},
            onTyped = {},
            onSave = {},
            onDelete = {},
            onCancel = {},
            onBack = {},
        )
    }
}
