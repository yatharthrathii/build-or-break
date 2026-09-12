package com.buildorbreak.app.feature.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.buildorbreak.app.R
import com.buildorbreak.core.designsystem.component.FillButton
import com.buildorbreak.core.designsystem.component.GhostButton
import com.buildorbreak.core.designsystem.component.HairlineRule
import com.buildorbreak.core.designsystem.component.Kicker
import com.buildorbreak.core.designsystem.component.Panel
import com.buildorbreak.core.designsystem.component.SegmentedTabs
import com.buildorbreak.core.designsystem.theme.Theme
import com.buildorbreak.core.designsystem.theme.TimeStyle
import com.buildorbreak.core.model.enums.Salience
import java.time.LocalTime
import java.util.Locale

/** Groups only make sense for the three that are delivered. */
private val GROUP_SALIENCES = listOf(Salience.ALARM, Salience.NOTIFY, Salience.SILENT)

/** A dialog id meaning "make a new group" rather than edit one. */
internal const val NEW_GROUP = -1L

private val DEFAULT_GROUP_TIME: LocalTime = LocalTime.of(8, 0)

/**
 * A group: a name, when it starts, and how loudly it announces itself.
 *
 * Three fields, because that is all a group is. It is not a step and cannot
 * be completed; it is a heading with a voice, and the voice is the point.
 * Five things between eight and half past arrive as one interruption instead
 * of five, which is what stops a busy morning teaching somebody to mute the
 * app inside a week.
 */
@Composable
internal fun GroupDialog(
    existing: PlanGroupRow?,
    onSave: (String, LocalTime, Salience) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(existing?.title.orEmpty()) }
    var minuteOfDay by rememberSaveable {
        mutableIntStateOf((existing?.at ?: DEFAULT_GROUP_TIME).toSecondOfDay() / SECONDS_PER_MINUTE)
    }
    var salience by rememberSaveable { mutableStateOf(existing?.salience ?: Salience.NOTIFY) }

    Dialog(onDismissRequest = onDismiss) {
        Panel {
            Column(
                modifier = Modifier.padding(Theme.spacing.medium),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                DialogTitle(isNew = existing == null)

                GroupFields(
                    name = name,
                    onName = { name = it },
                    time = LocalTime.ofSecondOfDay(minuteOfDay.toLong() * SECONDS_PER_MINUTE),
                    onTime = { minuteOfDay = it.toSecondOfDay() / SECONDS_PER_MINUTE },
                    salience = salience,
                    onSalience = { salience = it },
                    count = existing?.count ?: 0,
                )

                GroupActions(
                    canSave = name.isNotBlank(),
                    canDelete = existing != null,
                    onSave = {
                        onSave(name, LocalTime.ofSecondOfDay(minuteOfDay.toLong() * SECONDS_PER_MINUTE), salience)
                    },
                    onDelete = onDelete,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun DialogTitle(isNew: Boolean) {
    Text(
        text = stringResource(if (isNew) R.string.plan_group_new_title else R.string.plan_group_edit_title)
            .uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun GroupFields(
    name: String,
    onName: (String) -> Unit,
    time: LocalTime,
    onTime: (LocalTime) -> Unit,
    salience: Salience,
    onSalience: (Salience) -> Unit,
    count: Int,
) {
    TextBox(
        label = stringResource(R.string.plan_group_name),
        value = name,
        onValueChange = onName,
        placeholder = stringResource(R.string.plan_group_name_hint),
    )

    TimeField(label = stringResource(R.string.plan_group_starts), time = time, onPicked = onTime)

    Column {
        Kicker(text = stringResource(R.string.plan_group_how_loud))

        SegmentedTabs(
            options = GROUP_SALIENCES.map { stringResource(salienceLabel(it)) },
            selectedIndex = GROUP_SALIENCES.indexOf(salience).coerceAtLeast(0),
            onSelect = { onSalience(GROUP_SALIENCES[it]) },
            stretch = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )

        Text(
            text = stringResource(R.string.plan_group_how_loud_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Theme.spacing.small),
        )
    }

    if (count > 0) {
        // What a delete would and would not take with it, before it is offered.
        Text(
            text = pluralStringResource(R.plurals.plan_group_delete_body, count, count),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GroupActions(
    canSave: Boolean,
    canDelete: Boolean,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (canDelete) {
            GhostButton(
                text = stringResource(R.string.plan_group_delete),
                onClick = onDelete,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.weight(1f))

        GhostButton(text = stringResource(R.string.editor_cancel), onClick = onDismiss)
        FillButton(text = stringResource(R.string.editor_save), onClick = onSave, enabled = canSave)
    }
}

/**
 * The heading a group draws above its steps.
 *
 * Drawn as a bar rather than as a row, so it reads as a container and not as
 * another thing to tick off. Tapping it edits the group.
 */
@Composable
internal fun GroupHeader(group: PlanGroupRow, onEdit: (Long) -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Theme.colours.raised)
                .clickable(role = Role.Button) { onEdit(group.id) }
                .padding(horizontal = Theme.spacing.medium, vertical = Theme.spacing.inset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = group.time,
                style = TimeStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = group.title.uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Theme.spacing.inset),
            )

            Text(
                text = groupLine(group),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HairlineRule()
    }
}

/** "5 STEPS · ONE ALARM", or the honest empty case. */
@Composable
private fun groupLine(group: PlanGroupRow): String {
    if (group.count == 0) return stringResource(R.string.plan_group_empty).uppercase(Locale.getDefault())

    val steps = pluralStringResource(R.plurals.plan_group_steps, group.count, group.count)
    val voice = stringResource(salienceLabel(group.salience))

    return "$steps ${stringResource(R.string.plan_line_separator)} $voice".uppercase(Locale.getDefault())
}

private const val SECONDS_PER_MINUTE = 60
