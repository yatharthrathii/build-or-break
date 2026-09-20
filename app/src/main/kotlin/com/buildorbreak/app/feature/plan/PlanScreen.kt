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
import androidx.compose.material.icons.outlined.CreateNewFolder
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buildorbreak.app.R
import com.buildorbreak.app.format.rememberClockFormat
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
import java.time.LocalTime
import java.util.Locale
import kotlinx.collections.immutable.persistentListOf

/**
 * The clock column, wide enough for the longest time the phone will print.
 *
 * Two widths rather than one. "06:50" needs forty four; "10:30 AM" needs
 * sixty eight, and at forty four it wrapped onto a second line, which made
 * every row a different height and turned the column into a staircase.
 */
private val TimeColumn = 44.dp
private val WideTimeColumn = 68.dp
private val HandleSize = 18.dp

/** How far a step inside a group sits in from the edge. Enough to read as inside it. */
private val GroupIndent = 30.dp

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
    onAddItem: (templateId: Long) -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    PlanContent(
        state = state,
        actions = PlanActions(
            onSelectTemplate = viewModel::onSelectTemplate,
            onSaveTemplate = viewModel::onSaveTemplate,
            onDeleteTemplate = viewModel::onDeleteTemplate,
            onSaveGroup = viewModel::onSaveGroup,
            onDeleteGroup = viewModel::onDeleteGroup,
            onEditItem = onEditItem,
            onAddItem = { onAddItem(state.templateId) },
            onImport = onImport,
            onReorder = viewModel::onReorder,
        ),
        modifier = modifier,
    )
}

/** Everything the plan screen can do, in one bag, so the leaves take one parameter. */
data class PlanActions(
    val onSelectTemplate: (Long) -> Unit,
    val onSaveTemplate: (Long?, String, Weekdays) -> Unit,
    val onDeleteTemplate: (Long) -> Unit,
    val onSaveGroup: (Long?, String, LocalTime, Salience) -> Unit,
    val onDeleteGroup: (Long) -> Unit,
    val onEditItem: (Long) -> Unit,
    val onAddItem: () -> Unit,
    val onImport: () -> Unit,
    /** A tie of steps at one minute, in the order the user just put them. */
    val onReorder: (List<Long>) -> Unit = {},
) {
    companion object {
        val None = PlanActions(
            onSelectTemplate = {},
            onSaveTemplate = { _, _, _ -> },
            onDeleteTemplate = {},
            onSaveGroup = { _, _, _, _ -> },
            onDeleteGroup = {},
            onEditItem = {},
            onAddItem = {},
            onImport = {},
        )
    }
}

@Composable
fun PlanContent(state: PlanUiState, actions: PlanActions, modifier: Modifier = Modifier) {
    // Null: closed. NEW_TEMPLATE: a new one. Anything else: editing that id.
    var editingTemplate by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingGroup by rememberSaveable { mutableStateOf<Long?>(null) }

    PlanBody(
        state = state,
        actions = actions,
        onEditTemplate = { editingTemplate = it },
        onEditGroup = { editingGroup = it },
        modifier = modifier,
    )

    editingGroup?.let { id ->
        GroupDialogHost(
            id = id,
            state = state,
            onSaveGroup = actions.onSaveGroup,
            onDeleteGroup = actions.onDeleteGroup,
            onClose = { editingGroup = null },
        )
    }

    editingTemplate?.let { id ->
        TemplateDialogHost(
            id = id,
            state = state,
            onSaveTemplate = actions.onSaveTemplate,
            onDeleteTemplate = actions.onDeleteTemplate,
            onClose = { editingTemplate = null },
        )
    }
}

/** The header, the template tabs, and the steps under their headings. */
@Composable
private fun PlanBody(
    state: PlanUiState,
    actions: PlanActions,
    onEditTemplate: (Long?) -> Unit,
    onEditGroup: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding(),
    ) {
        ScreenHeader(kicker = kickerText(state), title = stringResource(R.string.plan_title)) {
            HeaderIcons(
                state = state,
                onEditTemplate = { onEditTemplate(state.templates.getOrNull(state.selectedIndex)?.id) },
                onNewGroup = { onEditGroup(NEW_GROUP) },
                onImport = actions.onImport,
            )
        }

        when {
            !state.hasPlan -> NoPlan(onImport = actions.onImport, onAddItem = actions.onAddItem)
            else -> Steps(
                state = state,
                actions = actions,
                onNewTemplate = { onEditTemplate(NEW_TEMPLATE) },
                onEditGroup = onEditGroup,
            )
        }
    }
}

@Composable
private fun GroupDialogHost(
    id: Long,
    state: PlanUiState,
    onSaveGroup: (Long?, String, LocalTime, Salience) -> Unit,
    onDeleteGroup: (Long) -> Unit,
    onClose: () -> Unit,
) {
    GroupDialog(
        existing = state.groups.firstOrNull { it.id == id },
        onSave = { title, at, salience ->
            onSaveGroup(id.takeIf { it != NEW_GROUP }, title, at, salience)
            onClose()
        },
        onDelete = {
            onDeleteGroup(id)
            onClose()
        },
        onDismiss = onClose,
    )
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
        cost = state.routineCost,
        balance = state.balance,
    )
}

/** Edit the template being shown, make a group, and import. */
@Composable
private fun HeaderIcons(
    state: PlanUiState,
    onEditTemplate: () -> Unit,
    onNewGroup: () -> Unit,
    onImport: () -> Unit,
) {
    if (state.hasPlan) {
        HeaderIcon(
            icon = Icons.Outlined.Edit,
            description = stringResource(R.string.plan_template_edit),
            onClick = onEditTemplate,
        )

        HeaderIcon(
            icon = Icons.Outlined.CreateNewFolder,
            description = stringResource(R.string.plan_group_new_title),
            onClick = onNewGroup,
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
    actions: PlanActions,
    onNewTemplate: () -> Unit,
    onEditGroup: (Long) -> Unit,
) {
    val reorder = rememberReorderState()

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { TemplateTabs(state = state, actions = actions, onNewTemplate = onNewTemplate) }

        state.sections.forEach { section ->
            section.group?.let { group ->
                item(key = "group:" + group.id) { GroupHeader(group = group, onEdit = onEditGroup) }
            }

            val rows = reorder.arrange(section.rows)

            items(items = rows, key = { it.id }) { row ->
                StepRow(
                    row = row,
                    indented = section.group != null,
                    onEdit = actions.onEditItem,
                    handle = Modifier.reorderHandle(
                        state = reorder,
                        id = row.id,
                        tie = rows.filter { it.movable && it.slot == row.slot }.map { it.id },
                        onReordered = actions.onReorder,
                    ),
                    modifier = Modifier
                        .animateItem()
                        .reorderRow(reorder, row.id),
                )
            }
        }

        item { AddStep(onAddItem = actions.onAddItem) }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

/**
 * The templates, and a last cell that makes a new one. Weekday and weekend
 * are the common pair; travel and rest days are the others.
 */
@Composable
private fun TemplateTabs(state: PlanUiState, actions: PlanActions, onNewTemplate: () -> Unit) {
    SegmentedTabs(
        options = state.templates.map { it.name } + stringResource(R.string.plan_template_new),
        selectedIndex = state.selectedIndex,
        onSelect = { index ->
            if (index == state.templates.size) {
                onNewTemplate()
            } else {
                actions.onSelectTemplate(state.templates[index].id)
            }
        },
        modifier = Modifier.padding(horizontal = Theme.spacing.medium, vertical = Theme.spacing.inset),
    )
}

@Composable
private fun StepRow(
    row: PlanItemRow,
    indented: Boolean,
    onEdit: (Long) -> Unit,
    modifier: Modifier = Modifier,
    handle: Modifier = Modifier,
) {
    Column(modifier = modifier.background(MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { onEdit(row.id) }
                .padding(start = if (indented) GroupIndent else 16.dp, end = 16.dp)
                .padding(vertical = Theme.spacing.inset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Solid where it does something, faint where it does not. A row
            // alone at its minute has nowhere to go; the clock put it there.
            Icon(
                imageVector = Icons.Outlined.DragHandle,
                contentDescription = if (row.movable) stringResource(R.string.plan_reorder) else null,
                tint = if (row.movable) MaterialTheme.colorScheme.onSurfaceVariant else Theme.colours.faint,
                modifier = handle.size(HandleSize),
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
    val wide = !rememberClockFormat().is24Hour

    Text(
        text = timeText(row.kind),
        style = TimeStyle,
        color = if (row.kind is PlanKind.Fixed) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        textAlign = TextAlign.Start,
        maxLines = 1,
        modifier = Modifier
            .padding(start = Theme.spacing.inset)
            // Sized for digits at the default size, so it grows with them.
            .width((if (wide) WideTimeColumn else TimeColumn) * LocalDensity.current.fontScale),
    )
}

@Composable
private fun StepBody(row: PlanItemRow, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(start = Theme.spacing.inset)) {
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
            maxLines = 2,
            modifier = Modifier.padding(top = Theme.spacing.tight),
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
            .padding(horizontal = Theme.spacing.inset),
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

/**
 * The time column: one clock, or an offset from the step above.
 *
 * A window and an interval both have two times, and both used to print the
 * pair stacked. On a twelve hour phone that came out as four lines in a
 * column built for one, so the row stood twice as tall as its neighbours and
 * the list stopped reading as a list. The column now shows the time the step
 * is first available, which is the one the eye is running down the page
 * looking for, and the far end moves to the line underneath where there is
 * room to say it in words.
 */
@Composable
private fun timeText(kind: PlanKind): String = when (kind) {
    is PlanKind.Fixed -> kind.at
    is PlanKind.After -> stringResource(R.string.plan_time_relative, kind.offsetMinutes)
    is PlanKind.Window -> kind.from
    is PlanKind.Every -> kind.from
}

/** "FIXED · ALARM ON", "WINDOW · 40 MIN WIDE", "AFTER GYM · ANCHOR FOR 2 STEPS". */
@Composable
private fun kindLine(row: PlanItemRow): String {
    val parts = buildList {
        add(
            when (val kind = row.kind) {
                is PlanKind.Fixed -> stringResource(R.string.plan_line_fixed)
                is PlanKind.After -> stringResource(R.string.plan_line_after, kind.parentTitle)
                is PlanKind.Window -> stringResource(R.string.plan_line_window, kind.to)
                is PlanKind.Every -> stringResource(R.string.plan_line_every, kind.minutes, kind.to)
            },
        )
        if (row.childCount > 0) add(pluralStringResource(R.plurals.plan_line_anchor, row.childCount, row.childCount))
        if (row.salience == Salience.ALARM && row.groupId == null) add(stringResource(R.string.plan_line_alarm))
        if (row.pinned) add(stringResource(R.string.plan_line_pinned))
        if (row.measured) add(stringResource(R.string.plan_line_measured))
        if (row.hasNote) add(stringResource(R.string.plan_line_note))
        if (row.weekdaysText.isNotEmpty()) add(row.weekdaysText)
    }

    return parts.joinToString(stringResource(R.string.plan_line_separator))
}

// Previews ---------------------------------------------------------------------

@Preview(name = "Plan", showBackground = true)
@Composable
private fun PlanPreview() {
    BuildOrBreakTheme {
        PlanContent(state = previewPlanState(), actions = PlanActions.None)
    }
}

// Fixture data, literal on purpose so the preview can be read at a glance.
@Suppress("MagicNumber")
private fun previewPlanState(): PlanUiState {
    val morning = PlanGroupRow(1, "Morning routine", "06:40", LocalTime.of(6, 40), Salience.ALARM, 2)

    val rows = persistentListOf(
        PlanItemRow(1, "Wake + water", PlanKind.Fixed("06:40"), Salience.ALARM, false, 0, "", groupId = 1),
        PlanItemRow(2, "Journal", PlanKind.Window("06:50", "07:30"), Salience.NOTIFY, false, 0, "", groupId = 1),
        PlanItemRow(3, "Gym", PlanKind.Fixed("07:30"), Salience.ALARM, true, 2, "", measured = true),
        PlanItemRow(4, "Protein + shower", PlanKind.After("Gym", 15), Salience.SILENT, false, 0, "", hasNote = true),
        PlanItemRow(5, "Stand up", PlanKind.Every(45, "11:00", "15:00"), Salience.SILENT, false, 0, "Mon Wed Fri"),
    )

    return PlanUiState(
        planId = 1,
        planName = "My routine",
        templateId = 1,
        templates = persistentListOf(
            TemplateTab(1, "Weekday", Weekdays.MonToFri, true),
            TemplateTab(2, "Weekend", Weekdays.Weekend, false),
        ),
        selectedIndex = 0,
        rows = rows,
        sections = persistentListOf(
            PlanSection(morning, persistentListOf(rows[0], rows[1])),
            PlanSection(null, persistentListOf(rows[2])),
            PlanSection(null, persistentListOf(rows[3])),
            PlanSection(null, persistentListOf(rows[4])),
        ),
        groups = persistentListOf(morning),
        firstTime = "06:40",
        lastTime = "22:15",
        hasPlan = true,
    )
}
