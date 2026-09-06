package com.buildorbreak.app.feature.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buildorbreak.app.R
import com.buildorbreak.core.designsystem.component.EmptyState
import com.buildorbreak.core.designsystem.component.Rule
import com.buildorbreak.core.designsystem.theme.BuildOrBreakTheme
import com.buildorbreak.core.designsystem.theme.Theme
import com.buildorbreak.core.model.enums.Salience
import kotlinx.collections.immutable.persistentListOf

/**
 * The plan, as something to change.
 *
 * Separate from Today on purpose. Today answers "what happens next" and must
 * stay uncluttered; this answers "what did I ask for" and can afford to show
 * every step, including the ones that do not run today.
 */
@Composable
fun PlanScreen(
    onEditItem: (Long) -> Unit,
    onAddItem: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    PlanContent(
        state = state,
        onEditItem = onEditItem,
        onAddItem = onAddItem,
        onImport = onImport,
        onArchive = viewModel::onArchive,
        modifier = modifier,
    )
}

@Composable
fun PlanContent(
    state: PlanUiState,
    onEditItem: (Long) -> Unit,
    onAddItem: () -> Unit,
    onImport: () -> Unit,
    onArchive: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            if (state.hasPlan) {
                ExtendedFloatingActionButton(
                    onClick = onAddItem,
                    text = { Text(stringResource(R.string.plan_add_step)) },
                    icon = {},
                )
            }
        },
    ) { insets ->
        when {
            !state.hasPlan -> EmptyState(
                title = stringResource(R.string.plan_none_title),
                body = stringResource(R.string.plan_none_body),
                actionLabel = stringResource(R.string.plan_import),
                onAction = onImport,
                modifier = Modifier.padding(insets),
            )

            state.isEmpty -> EmptyState(
                title = stringResource(R.string.plan_empty_title),
                body = stringResource(R.string.plan_empty_body),
                actionLabel = stringResource(R.string.plan_add_step),
                onAction = onAddItem,
                modifier = Modifier.padding(insets),
            )

            else -> Steps(
                state = state,
                onEditItem = onEditItem,
                onArchive = onArchive,
                modifier = Modifier.padding(insets),
            )
        }
    }
}

@Composable
private fun Steps(
    state: PlanUiState,
    onEditItem: (Long) -> Unit,
    onArchive: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            Column(
                modifier = Modifier.padding(
                    start = Theme.spacing.medium,
                    end = Theme.spacing.medium,
                    top = Theme.spacing.large,
                    bottom = Theme.spacing.small,
                ),
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.tight),
            ) {
                Text(text = state.templateName, style = MaterialTheme.typography.headlineMedium)

                Text(
                    text = state.planName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { Rule(Modifier.padding(vertical = Theme.spacing.small)) }

        items(items = state.items, key = { it.id }) { row ->
            StepRow(row = row, onEdit = onEditItem, onArchive = onArchive)
        }
    }
}

@Composable
private fun StepRow(row: PlanItemRow, onEdit: (Long) -> Unit, onArchive: (Long) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit(row.id) }
            .padding(horizontal = Theme.spacing.medium, vertical = Theme.spacing.small),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.tight),
    ) {
        Text(text = row.title, style = MaterialTheme.typography.titleMedium)

        Text(
            text = row.whenText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val notes = buildList {
            if (row.weekdaysText.isNotEmpty()) add(row.weekdaysText)
            if (row.pinned) add(stringResource(R.string.plan_note_pinned))
            if (row.hasMinimum) add(stringResource(R.string.plan_note_minimum))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = notes.joinToString(stringResource(R.string.plan_note_separator)),
                style = MaterialTheme.typography.labelMedium,
                color = Theme.colours.faint,
            )

            // Archive rather than delete, and worded that way. Nothing here
            // removes history, and a button that said "delete" would be lying
            // about what it does.
            TextButton(onClick = { onArchive(row.id) }) {
                Text(stringResource(R.string.plan_remove))
            }
        }
    }
}

// Previews ---------------------------------------------------------------------

@Preview(name = "Plan", showBackground = true)
@Composable
private fun PlanPreview() {
    BuildOrBreakTheme {
        PlanContent(
            state = PlanUiState(
                planName = "Weekday routine",
                templateName = "Weekday",
                items = persistentListOf(
                    PlanItemRow(1, "Wake up", "06:30", "", Salience.ALARM, false, false),
                    PlanItemRow(2, "Drink water", "+10m", "", Salience.SILENT, false, false),
                    PlanItemRow(3, "Study block", "07:30 to 09:30", "", Salience.NOTIFY, false, true),
                    PlanItemRow(4, "Gym class", "18:00", "Mon Wed Fri", Salience.ALARM, true, false),
                ),
                hasPlan = true,
            ),
            onEditItem = {},
            onAddItem = {},
            onImport = {},
            onArchive = {},
        )
    }
}
