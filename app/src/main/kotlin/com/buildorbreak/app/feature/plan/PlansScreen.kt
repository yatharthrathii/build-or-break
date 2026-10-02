package com.buildorbreak.app.feature.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
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
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buildorbreak.app.R
import com.buildorbreak.app.feature.about.BackHeader
import com.buildorbreak.core.designsystem.component.Badge
import com.buildorbreak.core.designsystem.component.FillButton
import com.buildorbreak.core.designsystem.component.GhostButton
import com.buildorbreak.core.designsystem.component.HairlineRule
import com.buildorbreak.core.designsystem.component.NoticeBar
import com.buildorbreak.core.designsystem.component.OutlineButton
import com.buildorbreak.core.designsystem.component.Panel
import com.buildorbreak.core.designsystem.theme.BuildOrBreakTheme
import com.buildorbreak.core.designsystem.theme.Theme
import java.util.Locale
import kotlinx.collections.immutable.persistentListOf

/** A dialog id that means "make a new plan" rather than edit one. */
private const val NEW_PLAN = -1L

/** How far the dialog stands in from the edges of the window. */
private val DialogMargin = 24.dp

/**
 * Every plan, and the one Today runs.
 *
 * A plan is a whole routine: its days, its steps, its goals. Two of them
 * are for two different lives, exam season and the rest of the year, and
 * the switch is one tap because the morning it is needed is not a morning
 * for settings.
 */
@Composable
fun PlansScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: PlansViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    PlansContent(
        state = state,
        onUse = viewModel::onUse,
        onAdd = viewModel::onAdd,
        onRename = viewModel::onRename,
        onDelete = viewModel::onDelete,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun PlansContent(
    state: PlansUiState,
    onUse: (Long) -> Unit,
    onAdd: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Null: closed. NEW_PLAN: a new one. Anything else: editing that id.
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding(),
    ) {
        BackHeader(
            kicker = pluralStringResource(R.plurals.plans_kicker, state.plans.size, state.plans.size),
            title = stringResource(R.string.plans_title),
            onBack = onBack,
        )

        if (state.failed) NoticeBar(text = stringResource(R.string.today_action_failed))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { Intro() }

            items(items = state.plans, key = { it.id }) { plan ->
                PlanRow(plan = plan, onUse = { onUse(plan.id) }, onEdit = { editing = plan.id })
            }

            item { AddPlan(onClick = { editing = NEW_PLAN }) }
        }
    }

    editing?.let { id ->
        PlanDialog(
            existing = state.plans.firstOrNull { it.id == id },
            canDelete = state.canDelete,
            onSave = { name ->
                if (id == NEW_PLAN) onAdd(name) else onRename(id, name)
                editing = null
            },
            onDelete = {
                onDelete(id)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun Intro() {
    Text(
        text = stringResource(R.string.plans_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Theme.spacing.medium, vertical = 14.dp),
    )

    HairlineRule(Modifier.padding(horizontal = Theme.spacing.medium))
}

@Composable
private fun PlanRow(plan: PlanRowUi, onUse: () -> Unit, onEdit: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = Theme.spacing.medium)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.inset),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = plan.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    text = stringResource(R.string.plans_since, plan.since),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Theme.spacing.tight),
                )
            }

            if (plan.isRunning) {
                Badge(text = stringResource(R.string.plans_running), accent = true)
            } else {
                OutlineButton(text = stringResource(R.string.plans_use), onClick = onUse)
            }

            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = stringResource(R.string.plans_edit, plan.name),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(22.dp)
                    .clickable(role = Role.Button, onClick = onEdit),
            )
        }

        HairlineRule()
    }
}

@Composable
private fun AddPlan(onClick: () -> Unit) {
    OutlineButton(
        text = stringResource(R.string.plans_add),
        onClick = onClick,
        modifier = Modifier.padding(Theme.spacing.medium),
    )
}

/** A plan is a name. Delete is offered only when another plan is left, and it says what goes. */
@Composable
private fun PlanDialog(
    existing: PlanRowUi?,
    canDelete: Boolean,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }

    // Full width with its own margin, like the syllabus editor. A text box
    // inside a dialog left to size itself never settles under Robolectric,
    // and a dialog that cannot be tested is a dialog nobody checks.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Panel(modifier = Modifier.padding(horizontal = DialogMargin)) {
            Column(
                modifier = Modifier.padding(Theme.spacing.medium),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = stringResource(if (existing == null) R.string.plans_new_title else R.string.plans_edit_title)
                        .uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                TextBox(
                    label = stringResource(R.string.plan_template_name),
                    value = name,
                    onValueChange = { name = it },
                    placeholder = stringResource(R.string.plans_name_hint),
                )

                DialogNote(isNew = existing == null, canDelete = canDelete)

                DialogActions(
                    canSave = name.isNotBlank(),
                    canDelete = existing != null && canDelete,
                    onSave = { onSave(name) },
                    onDelete = onDelete,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

/** What a new plan starts as, or what deleting this one takes with it. */
@Composable
private fun DialogNote(isNew: Boolean, canDelete: Boolean) {
    if (!isNew && !canDelete) return

    Text(
        text = stringResource(if (isNew) R.string.plans_new_body else R.string.plans_delete_body),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DialogActions(
    canSave: Boolean,
    canDelete: Boolean,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
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
        FillButton(text = stringResource(R.string.editor_save), onClick = onSave, enabled = canSave)
    }
}

@Preview(name = "Plans", showBackground = true)
@Composable
private fun PlansPreview() {
    BuildOrBreakTheme {
        PlansContent(
            state = PlansUiState(
                plans = persistentListOf(
                    PlanRowUi(1, "Normal days", isRunning = true, since = "Sep 2026"),
                    PlanRowUi(2, "Exam season", isRunning = false, since = "Oct 2026"),
                ),
            ),
            onUse = {},
            onAdd = {},
            onRename = { _, _ -> },
            onDelete = {},
            onBack = {},
        )
    }
}
