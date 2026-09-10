package com.buildorbreak.app.feature.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buildorbreak.app.R
import com.buildorbreak.core.designsystem.component.BlockButton
import com.buildorbreak.core.designsystem.component.EmptyState
import com.buildorbreak.core.designsystem.component.HairlineRule
import com.buildorbreak.core.designsystem.component.Kicker
import com.buildorbreak.core.designsystem.component.OutlineButton
import com.buildorbreak.core.designsystem.component.ScreenHeader
import com.buildorbreak.core.designsystem.component.SegmentedTabs
import com.buildorbreak.core.designsystem.theme.BuildOrBreakTheme
import com.buildorbreak.core.designsystem.theme.Theme
import com.buildorbreak.core.designsystem.theme.TimeStyle
import com.buildorbreak.core.model.enums.Salience
import com.buildorbreak.core.model.plan.Weekdays
import java.util.Locale
import kotlinx.collections.immutable.persistentListOf

private val TimeColumn = 44.dp
private val HandleSize = 18.dp

/** A dialog id that means "make a new template" rather than edit one. */
private const val NEW_TEMPLATE = -1L

/**
 * The plan, as something to change.
 *
 * Separate from Today on purpose. Today answers "what happens next" and must
 * stay uncluttered; this answers "what did I ask for" and can afford to show
 * every step, including the ones that do not run today, with the kind of time
 * each one keeps written under it.
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
        onSelectTemplate = viewModel::onSelectTemplate,
        onSaveTemplate = viewModel::onSaveTemplate,
        onDeleteTemplate = viewModel::onDeleteTemplate,
        onEditItem = onEditItem,
        onAddItem = onAddItem,
        onImport = onImport,
        modifier = modifier,
    )
}

@Composable
fun PlanContent(
    state: PlanUiState,
    onSelectTemplate: (Long) -> Unit,
    onSaveTemplate: (Long?, String, Weekdays) -> Unit,
    onDeleteTemplate: (Long) -> Unit,
    onEditItem: (Long) -> Unit,
    onAddItem: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Null: closed. NEW_TEMPLATE: a new one. Anything else: editing that id.
    var editingTemplate by rememberSaveable { mutableStateOf<Long?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding(),
    ) {
        ScreenHeader(kicker = kickerText(state), title = stringResource(R.string.plan_title)) {
            HeaderIcons(
                state = state,
                onEditTemplate = { editingTemplate = state.templates.getOrNull(state.selectedIndex)?.id },
                onImport = onImport,
            )
        }

        when {
            !state.hasPlan -> NoPlan(onImport = onImport, onAddItem = onAddItem)
            else -> Steps(
                state = state,
                onSelectTemplate = onSelectTemplate,
                onNewTemplate = { editingTemplate = NEW_TEMPLATE },
                onEditItem = onEditItem,
                onAddItem = onAddItem,
            )
        }
    }

    editingTemplate?.let { id ->
        TemplateDialogHost(
            id = id,
            state = state,
            onSaveTemplate = onSaveTemplate,
            onDeleteTemplate = onDeleteTemplate,
            onClose = { editingTemplate = null },
        )
    }
}

@Composable
private fun TemplateDialogHost(
    id: Long,
    state: PlanUiState,
    onSaveTemplate: (Long?, String, Weekdays) -> Unit,
    onDeleteTemplate: (Long) -> Unit,
    onClose: () -> Unit,
) {
    TemplateDialog(
        existing = state.templates.firstOrNull { it.id == id },
        canDelete = state.templates.size > 1,
        onSave = { name, weekdays ->
            onSaveTemplate(id.takeIf { it != NEW_TEMPLATE }, name, weekdays)
            onClose()
        },
        onDelete = {
            onDeleteTemplate(id)
            onClose()
        },
        onDismiss = onClose,
    )
}

/** Edit the template being shown, and import. Edit only once there is a plan. */
@Composable
private fun HeaderIcons(state: PlanUiState, onEditTemplate: () -> Unit, onImport: () -> Unit) {
    if (state.hasPlan) {
        HeaderIcon(
            icon = Icons.Outlined.Edit,
            description = stringResource(R.string.plan_template_edit),
            onClick = onEditTemplate,
        )
    }

    HeaderIcon(
        icon = Icons.Outlined.FileDownload,
        description = stringResource(R.string.plan_import),
        onClick = onImport,
    )
}

@Composable
private fun HeaderIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .size(24.dp)
            .clickable(role = Role.Button, onClick = onClick),
    )
}

@Composable
private fun kickerText(state: PlanUiState): String = when {
    !state.hasPlan -> stringResource(R.string.plan_kicker_none)
    state.rows.isEmpty() -> stringResource(R.string.plan_kicker_empty)
    else -> pluralStringResource(
        R.plurals.plan_kicker,
        state.rows.size,
        state.rows.size,
        state.firstTime.orEmpty(),
        state.lastTime.orEmpty(),
    )
}

@Composable
private fun Steps(
    state: PlanUiState,
    onSelectTemplate: (Long) -> Unit,
    onNewTemplate: () -> Unit,
    onEditItem: (Long) -> Unit,
    onAddItem: () -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            // The templates, and a last cell that makes a new one. Weekday and
            // weekend are the common pair; travel and rest days are the others.
            SegmentedTabs(
                options = state.templates.map { it.name } + stringResource(R.string.plan_template_new),
                selectedIndex = state.selectedIndex,
                onSelect = { index ->
                    if (index == state.templates.size) onNewTemplate() else onSelectTemplate(state.templates[index].id)
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }

        items(items = state.rows, key = { it.id }) { row ->
            StepRow(row = row, onEdit = onEditItem)
        }

        item { AddStep(onAddItem = onAddItem) }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun StepRow(row: PlanItemRow, onEdit: (Long) -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { onEdit(row.id) }
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.DragHandle,
                contentDescription = null,
                tint = Theme.colours.faint,
                modifier = Modifier.size(HandleSize),
            )

            StepTime(row = row)

            StepBody(row = row, modifier = Modifier.weight(1f))

            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = Theme.colours.faint,
                modifier = Modifier.size(HandleSize),
            )
        }

        HairlineRule()
    }
}

@Composable
private fun StepTime(row: PlanItemRow) {
    Text(
        text = timeText(row.kind),
        style = TimeStyle,
        color = if (row.kind is PlanKind.Fixed) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        textAlign = TextAlign.Start,
        modifier = Modifier
            .padding(start = 11.dp)
            .width(TimeColumn),
    )
}

@Composable
private fun StepBody(row: PlanItemRow, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(start = 11.dp)) {
        Text(
            text = row.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Kicker(
            text = kindLine(row),
            color = if (row.childCount > 0) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

/** The dashed frame at the bottom of the list. Tap to add. */
@Composable
private fun AddStep(onAddItem: () -> Unit) {
    val stroke = Theme.colours.faint
    val width = Theme.spacing.rule

    Row(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
            .height(52.dp)
            .drawBehind {
                val px = width.toPx()
                drawRect(
                    color = stroke,
                    topLeft = Offset(px / 2, px / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - px, size.height - px),
                    style = Stroke(
                        width = px,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                    ),
                )
            }
            .clickable(role = Role.Button, onClick = onAddItem)
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.plan_add_step).uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(HandleSize),
        )
    }
}

@Composable
private fun NoPlan(onImport: () -> Unit, onAddItem: () -> Unit) {
    EmptyState(title = stringResource(R.string.plan_none_title), body = stringResource(R.string.plan_none_body)) {
        BlockButton(
            text = stringResource(R.string.plan_import),
            onClick = onImport,
            icon = Icons.AutoMirrored.Outlined.ArrowForward,
        )
        OutlineButton(text = stringResource(R.string.plan_write), onClick = onAddItem)
    }
}

// Copy lookups -----------------------------------------------------------------

/** The time column: a clock, two clocks, an offset, or the word "every". */
@Composable
private fun timeText(kind: PlanKind): String = when (kind) {
    is PlanKind.Fixed -> kind.at
    is PlanKind.After -> stringResource(R.string.plan_time_relative, kind.offsetMinutes)
    is PlanKind.Window -> "${kind.from}\n${kind.to}"
    is PlanKind.Every -> stringResource(R.string.plan_time_every)
}

/** "FIXED · ALARM ON", "WINDOW · 40 MIN WIDE", "AFTER GYM · ANCHOR FOR 2 STEPS". */
@Composable
private fun kindLine(row: PlanItemRow): String {
    val parts = buildList {
        add(
            when (val kind = row.kind) {
                is PlanKind.Fixed -> stringResource(R.string.plan_line_fixed)
                is PlanKind.After -> stringResource(R.string.plan_line_after, kind.parentTitle)
                is PlanKind.Window -> stringResource(R.string.plan_line_window, kind.minutesWide)
                is PlanKind.Every -> stringResource(R.string.plan_line_every, kind.minutes)
            },
        )
        if (row.childCount > 0) add(pluralStringResource(R.plurals.plan_line_anchor, row.childCount, row.childCount))
        if (row.salience == Salience.ALARM) add(stringResource(R.string.plan_line_alarm))
        if (row.pinned) add(stringResource(R.string.plan_line_pinned))
        if (row.weekdaysText.isNotEmpty()) add(row.weekdaysText)
    }

    return parts.joinToString(stringResource(R.string.plan_line_separator))
}

// Previews ---------------------------------------------------------------------

@Preview(name = "Plan", showBackground = true)
@Composable
private fun PlanPreview() {
    BuildOrBreakTheme {
        PlanContent(
            state = PlanUiState(
                planId = 1,
                planName = "My routine",
                templates = persistentListOf(
                    TemplateTab(1, "Weekday", Weekdays.MonToFri, true),
                    TemplateTab(2, "Weekend", Weekdays.Weekend, false),
                ),
                selectedIndex = 0,
                rows = persistentListOf(
                    PlanItemRow(1, "Wake + water", PlanKind.Fixed("06:40"), Salience.ALARM, false, 0, ""),
                    PlanItemRow(2, "Journal", PlanKind.Window("06:50", "07:30", 40), Salience.NOTIFY, false, 0, ""),
                    PlanItemRow(3, "Gym", PlanKind.Fixed("07:30"), Salience.ALARM, true, 2, ""),
                    PlanItemRow(4, "Protein + shower", PlanKind.After("Gym", 15), Salience.SILENT, false, 0, ""),
                    PlanItemRow(
                        5,
                        "Stand up",
                        PlanKind.Every(45, "11:00", "15:00"),
                        Salience.SILENT,
                        false,
                        0,
                        "Mon Wed Fri",
                    ),
                ),
                firstTime = "06:40",
                lastTime = "22:15",
                hasPlan = true,
            ),
            onSelectTemplate = {},
            onSaveTemplate = { _, _, _ -> },
            onDeleteTemplate = {},
            onEditItem = {},
            onAddItem = {},
            onImport = {},
        )
    }
}
