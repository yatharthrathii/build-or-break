package com.buildorbreak.app.feature.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.buildorbreak.app.R
import com.buildorbreak.core.designsystem.component.HairlineRule
import com.buildorbreak.core.designsystem.component.Kicker
import com.buildorbreak.core.designsystem.component.Panel
import com.buildorbreak.core.designsystem.component.PickerField
import com.buildorbreak.core.model.enums.ValueKind

/**
 * The group this step belongs to, and what belonging costs it.
 *
 * The line underneath is the important part. A group speaks for its steps: the
 * first one carries the loudness of the group and the rest run silent, which is
 * the whole reason five things at eight in the morning are one interruption
 * rather than five. Somebody who moves a step into a group and then cannot
 * work out why it stopped ringing has been misled by a control that looked
 * independent.
 */
@Composable
internal fun GroupSection(state: ItemEditorUiState, onChange: (ItemEditorUiState) -> Unit) {
    if (state.groups.isEmpty()) return

    var choosing by remember { mutableStateOf(false) }

    Column {
        PickerField(
            label = stringResource(R.string.editor_group),
            value = state.group?.title ?: stringResource(R.string.editor_group_none),
            onClick = { choosing = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp),
            )
        }

        Text(
            text = state.group?.let { stringResource(R.string.editor_group_in, it.title) }
                ?: stringResource(R.string.editor_group_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 7.dp),
        )
    }

    if (choosing) {
        GroupDialog(
            groups = state.groups,
            selected = state.blockId,
            onPick = {
                onChange(state.copy(blockId = it))
                choosing = false
            },
            onDismiss = { choosing = false },
        )
    }
}

@Composable
private fun GroupDialog(
    groups: List<GroupChoice>,
    selected: Long?,
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Panel {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Kicker(
                    text = stringResource(R.string.editor_group_pick),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )

                ChoiceRow(
                    text = stringResource(R.string.editor_group_none),
                    chosen = selected == null,
                    onClick = { onPick(null) },
                )

                groups.forEach { group ->
                    ChoiceRow(text = group.title, chosen = group.id == selected, onClick = { onPick(group.id) })
                }
            }
        }
    }
}

/**
 * What number this step asks for when it is ticked off.
 *
 * "None" is the default and stays the default. Most steps are not measured,
 * and a routine app that asks for a number every time something is completed
 * is a routine app that gets completed less.
 */
@Composable
internal fun MeasureSection(state: ItemEditorUiState, onChange: (ItemEditorUiState) -> Unit) {
    var choosing by remember { mutableStateOf(false) }

    Column {
        PickerField(
            label = stringResource(R.string.editor_measure),
            value = stringResource(valueKindLabel(state.valueKind)),
            onClick = { choosing = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp),
            )
        }

        Text(
            text = stringResource(
                if (state.valueKind == ValueKind.NONE) R.string.editor_measure_body else R.string.editor_measure_on,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 7.dp),
        )
    }

    if (choosing) {
        MeasureDialog(
            selected = state.valueKind,
            onPick = {
                onChange(state.copy(valueKind = it))
                choosing = false
            },
            onDismiss = { choosing = false },
        )
    }
}

@Composable
private fun MeasureDialog(selected: ValueKind, onPick: (ValueKind) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Panel {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Kicker(
                    text = stringResource(R.string.editor_measure_pick),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )

                ValueKind.entries.forEach { kind ->
                    ChoiceRow(
                        text = stringResource(valueKindLabel(kind)),
                        chosen = kind == selected,
                        onClick = { onPick(kind) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(text: String, chosen: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )

        if (chosen) {
            Text(
                text = stringResource(R.string.editor_chosen),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }

    HairlineRule(Modifier.padding(horizontal = 16.dp))
}

internal fun valueKindLabel(kind: ValueKind): Int = when (kind) {
    ValueKind.NONE -> R.string.measure_none
    ValueKind.WEIGHT_KG -> R.string.measure_weight
    ValueKind.REPS -> R.string.measure_reps
    ValueKind.PAGES -> R.string.measure_pages
    ValueKind.MINUTES -> R.string.measure_minutes
    ValueKind.COUNT -> R.string.measure_count
    ValueKind.FREE_NUMBER -> R.string.measure_number
}

/** The short unit, for a number sitting next to a step. */
internal fun valueKindUnit(kind: ValueKind): Int = when (kind) {
    ValueKind.NONE, ValueKind.FREE_NUMBER, ValueKind.COUNT -> R.string.unit_none
    ValueKind.WEIGHT_KG -> R.string.unit_kg
    ValueKind.REPS -> R.string.unit_reps
    ValueKind.PAGES -> R.string.unit_pages
    ValueKind.MINUTES -> R.string.unit_minutes
}
