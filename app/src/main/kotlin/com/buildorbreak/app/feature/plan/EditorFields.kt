package com.buildorbreak.app.feature.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.buildorbreak.app.R
import com.buildorbreak.app.format.rememberClockFormat
import com.buildorbreak.core.designsystem.component.HairlineRule
import com.buildorbreak.core.designsystem.component.Kicker
import com.buildorbreak.core.designsystem.component.Panel
import com.buildorbreak.core.designsystem.component.PickerField
import com.buildorbreak.core.designsystem.component.SegmentedTabs
import com.buildorbreak.core.designsystem.component.Stepper
import com.buildorbreak.core.designsystem.theme.Theme
import com.buildorbreak.core.model.enums.AnchorType
import com.buildorbreak.core.model.plan.Weekdays
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

private const val OFFSET_STEP = 5
private const val MAX_OFFSET = 720
private const val MIN_INTERVAL = 5
private const val MAX_INTERVAL = 240

/**
 * The timing kind, and the fields that kind needs.
 *
 * Four tabs across the top, then one or two rows underneath that change with
 * the tab. Nothing is hidden behind a disclosure: the whole point of the app
 * is on this section and it should look like it.
 */
@Composable
internal fun TimingSection(state: ItemEditorUiState, onChange: (ItemEditorUiState) -> Unit) {
    Column {
        Kicker(text = stringResource(R.string.editor_timing))

        SegmentedTabs(
            options = AnchorType.entries.map { stringResource(anchorLabel(it)) },
            selectedIndex = AnchorType.entries.indexOf(state.anchor.kind),
            onSelect = { onChange(state.copy(anchor = state.anchor.copy(kind = AnchorType.entries[it]))) },
            accent = true,
            stretch = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )

        Box(modifier = Modifier.padding(top = 14.dp)) {
            when (state.anchor.kind) {
                AnchorType.FIXED -> TimeField(
                    label = stringResource(R.string.editor_at),
                    time = state.anchor.at,
                    onPicked = { onChange(state.copy(anchor = state.anchor.copy(at = it))) },
                )

                AnchorType.RELATIVE -> RelativeFields(state = state, onChange = onChange)
                AnchorType.WINDOW -> WindowFields(state = state, onChange = onChange)
                AnchorType.INTERVAL -> IntervalFields(state = state, onChange = onChange)
            }
        }

        LandsAt(state = state)
    }
}

@Composable
private fun RelativeFields(state: ItemEditorUiState, onChange: (ItemEditorUiState) -> Unit) {
    val draft = state.anchor
    var choosing by remember { mutableStateOf(false) }
    val parent = state.parents.firstOrNull { it.id == draft.parentItemId }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OffsetField(
            minutes = draft.offsetMinutes,
            onMinutes = { onChange(state.copy(anchor = draft.copy(offsetMinutes = it))) },
            modifier = Modifier.weight(1f),
        )

        PickerField(
            label = stringResource(R.string.editor_after),
            value = parent?.title ?: stringResource(R.string.editor_pick_parent),
            onClick = { choosing = true },
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp),
            )
        }
    }

    if (choosing) {
        ParentDialog(
            parents = state.parents,
            onPick = {
                onChange(state.copy(anchor = draft.copy(parentItemId = it)))
                choosing = false
            },
            onDismiss = { choosing = false },
        )
    }
}

@Composable
private fun OffsetField(minutes: Int, onMinutes: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Kicker(text = stringResource(R.string.editor_offset))

        Stepper(
            value = stringResource(R.string.editor_minutes_value, minutes),
            onDecrement = { onMinutes((minutes - OFFSET_STEP).coerceAtLeast(0)) },
            onIncrement = { onMinutes((minutes + OFFSET_STEP).coerceAtMost(MAX_OFFSET)) },
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun WindowFields(state: ItemEditorUiState, onChange: (ItemEditorUiState) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TimeField(
            label = stringResource(R.string.editor_from),
            time = state.anchor.from,
            onPicked = { onChange(state.copy(anchor = state.anchor.copy(from = it))) },
            modifier = Modifier.weight(1f),
        )

        TimeField(
            label = stringResource(R.string.editor_to),
            time = state.anchor.to,
            onPicked = { onChange(state.copy(anchor = state.anchor.copy(to = it))) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun IntervalFields(state: ItemEditorUiState, onChange: (ItemEditorUiState) -> Unit) {
    val draft = state.anchor

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WindowFields(state = state, onChange = onChange)

        Column {
            Kicker(text = stringResource(R.string.editor_every))

            Stepper(
                value = stringResource(R.string.editor_minutes_value, draft.everyMinutes),
                onDecrement = {
                    onChange(
                        state.copy(
                            anchor = draft.copy(
                                everyMinutes = (draft.everyMinutes - OFFSET_STEP).coerceAtLeast(MIN_INTERVAL),
                            ),
                        ),
                    )
                },
                onIncrement = {
                    onChange(
                        state.copy(
                            anchor = draft.copy(
                                everyMinutes = (draft.everyMinutes + OFFSET_STEP).coerceAtMost(MAX_INTERVAL),
                            ),
                        ),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            )
        }
    }
}

/** "Today this lands at 14:00", on a tint, when the step is on today's timeline. */
@Composable
private fun LandsAt(state: ItemEditorUiState) {
    val landsAt = state.landsAtToday ?: return

    Text(
        text = if (state.childCount > 0) {
            pluralStringResource(R.plurals.editor_lands_at_anchor, state.childCount, landsAt, state.childCount)
        } else {
            stringResource(R.string.editor_lands_at, landsAt)
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = Theme.spacing.inset, vertical = 10.dp),
    )
}

/** A time, in a bordered box, that opens the picker when tapped. */
@Composable
internal fun TimeField(
    label: String,
    time: LocalTime,
    onPicked: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    var picking by remember { mutableStateOf(false) }

    PickerField(label = label, value = rememberClockFormat().format(time), onClick = {
        picking = true
    }, modifier = modifier)

    if (picking) {
        TimeWheelDialog(
            initial = time,
            onDismiss = { picking = false },
            onPicked = {
                onPicked(it)
                picking = false
            },
        )
    }
}

/** Which step this one hangs off. A list, because there are rarely more than ten. */
@Composable
private fun ParentDialog(parents: List<ParentChoice>, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Panel {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Kicker(
                    text = stringResource(R.string.editor_pick_parent_title),
                    modifier = Modifier.padding(horizontal = Theme.spacing.medium, vertical = 10.dp),
                )

                if (parents.isEmpty()) {
                    Text(
                        text = stringResource(R.string.editor_no_parents),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Theme.spacing.medium, vertical = 10.dp),
                    )
                }

                parents.forEach { parent ->
                    Text(
                        text = parent.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { onPick(parent.id) }
                            .padding(horizontal = Theme.spacing.medium, vertical = Theme.spacing.inset),
                    )
                    HairlineRule(Modifier.padding(horizontal = Theme.spacing.medium))
                }
            }
        }
    }
}

/** Seven square cells, Monday first. Filled when the step runs that day. */
@Composable
internal fun WeekdayRow(weekdays: Weekdays, onChange: (Weekdays) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DayOfWeek.entries.forEach { day ->
            val selected = day in weekdays

            Text(
                text = day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface,
                    )
                    .border(
                        Theme.spacing.rule,
                        if (selected) MaterialTheme.colorScheme.onSurface else Theme.colours.faint,
                    )
                    .clickable(role = Role.Checkbox) { onChange(if (selected) weekdays - day else weekdays + day) }
                    .padding(vertical = Theme.spacing.inset),
            )
        }
    }
}

/**
 * A text field drawn the way everything else here is drawn.
 *
 * `BasicTextField` in a bordered box rather than Material's outlined field,
 * which has a rounded corner and a floating label that would be the only two
 * soft things on the screen.
 */
@Composable
internal fun TextBox(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    help: String? = null,
    minLines: Int = 1,
) {
    Column(modifier = modifier) {
        Kicker(text = label)

        help?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.titleSmall.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            minLines = minLines,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .border(Theme.spacing.rule, MaterialTheme.colorScheme.onSurface)
                .background(Theme.colours.raised)
                .heightIn(min = 44.dp)
                .padding(horizontal = Theme.spacing.inset, vertical = 12.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.titleSmall,
                            color = Theme.colours.faint,
                        )
                    }
                    inner()
                }
            },
        )
    }
}

internal fun anchorLabel(kind: AnchorType): Int = when (kind) {
    AnchorType.FIXED -> R.string.anchor_fixed
    AnchorType.RELATIVE -> R.string.anchor_relative
    AnchorType.WINDOW -> R.string.anchor_window
    AnchorType.INTERVAL -> R.string.anchor_interval
}
