package com.buildorbreak.app.feature.plan

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.buildorbreak.app.R
import com.buildorbreak.core.designsystem.component.FillButton
import com.buildorbreak.core.designsystem.component.GhostButton
import com.buildorbreak.core.designsystem.component.Kicker
import com.buildorbreak.core.designsystem.component.Panel
import com.buildorbreak.core.designsystem.theme.Theme
import com.buildorbreak.core.model.plan.Weekdays
import java.util.Locale

/**
 * A template: its name and the days it runs on.
 *
 * Two fields, because that is all a template is. Which template runs on a
 * day is decided by these weekday masks, and the plan's default covers any
 * day nothing claims. Delete is offered only when another template is left,
 * and it says what goes with it.
 */
@Composable
internal fun TemplateDialog(
    existing: TemplateTab?,
    canDelete: Boolean,
    onSave: (String, Weekdays) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    /** What a new one costs past the free two. Zero while they are still free. */
    cost: Int = 0,
    balance: Int = 0,
) {
    val charged = existing == null && cost > 0
    val affordable = !charged || balance >= cost
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var weekdays by rememberSaveable { mutableStateOf(existing?.weekdays?.bits ?: Weekdays.EveryDay.bits) }

    Dialog(onDismissRequest = onDismiss) {
        Panel {
            Column(
                modifier = Modifier.padding(Theme.spacing.medium),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = stringResource(
                        if (existing == null) R.string.plan_template_new_title else R.string.plan_template_edit_title,
                    ).uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Fields(
                    name = name,
                    onName = { name = it },
                    weekdays = Weekdays(weekdays),
                    onWeekdays = { weekdays = it.bits },
                    showDeleteNote = existing != null && canDelete,
                )

                // Said before the save, not after it fails. Somebody who
                // cannot afford a third routine should learn that from the
                // dialog rather than from a button that quietly does nothing.
                if (charged) {
                    Text(
                        text = stringResource(R.string.points_routine_body, cost, balance),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (affordable) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }

                Actions(
                    saveLabel = if (charged) stringResource(R.string.points_routine_confirm, cost) else null,
                    canSave = name.isNotBlank() && affordable,
                    canDelete = existing != null && canDelete,
                    onSave = { onSave(name, Weekdays(weekdays)) },
                    onDelete = onDelete,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun Fields(
    name: String,
    onName: (String) -> Unit,
    weekdays: Weekdays,
    onWeekdays: (Weekdays) -> Unit,
    showDeleteNote: Boolean,
) {
    TextBox(
        label = stringResource(R.string.plan_template_name),
        value = name,
        onValueChange = onName,
        placeholder = stringResource(R.string.plan_template_name_hint),
    )

    Column {
        Kicker(text = stringResource(R.string.plan_template_days))
        WeekdayRow(weekdays = weekdays, onChange = onWeekdays, modifier = Modifier.padding(top = 8.dp))
    }

    if (showDeleteNote) {
        Text(
            text = stringResource(R.string.plan_template_delete_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Actions(
    canSave: Boolean,
    canDelete: Boolean,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    /** "Add for 500" when it costs something. Null keeps the plain Save. */
    saveLabel: String? = null,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (canDelete) {
            GhostButton(
                text = stringResource(R.string.plan_template_delete),
                onClick = onDelete,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.weight(1f))

        GhostButton(text = stringResource(R.string.editor_cancel), onClick = onDismiss)
        FillButton(
            text = saveLabel ?: stringResource(R.string.editor_save),
            onClick = onSave,
            enabled = canSave,
        )
    }
}
