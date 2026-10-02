package com.buildorbreak.app.feature.track

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buildorbreak.app.R
import com.buildorbreak.app.feature.about.BackHeader
import com.buildorbreak.core.designsystem.component.EmptyState
import com.buildorbreak.core.designsystem.component.GhostButton
import com.buildorbreak.core.designsystem.component.HairlineRule
import com.buildorbreak.core.designsystem.component.HeavyRule
import com.buildorbreak.core.designsystem.component.Kicker
import com.buildorbreak.core.designsystem.component.NoticeBar
import com.buildorbreak.core.designsystem.component.OutlineButton
import com.buildorbreak.core.designsystem.component.Panel
import com.buildorbreak.core.designsystem.component.SectionLabel
import com.buildorbreak.core.designsystem.theme.BuildOrBreakTheme
import com.buildorbreak.core.designsystem.theme.Theme
import com.buildorbreak.core.designsystem.theme.TimeStyle
import com.buildorbreak.core.model.enums.TrackUnitState
import kotlinx.collections.immutable.persistentListOf

/** The number column: wide enough for two digits in the time face. */
private val NumberColumn = 32.dp

/**
 * One syllabus: where it stands, and every part in order.
 *
 * The parts are the list and the next one is marked. A tap on any part
 * opens three answers, done, not yet, skip, because a syllabus is never
 * followed exactly: a chapter read on the train still has to be ticked off,
 * and a chapter nobody is going to read has to be got past.
 */
@Composable
fun TrackScreen(
    trackId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrackViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(trackId) { viewModel.load(trackId) }

    TrackContent(
        state = state,
        countParts = viewModel::partsIn,
        onSave = viewModel::onSave,
        onSetState = viewModel::onSetState,
        onDelete = { viewModel.onDelete(onBack) },
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun TrackContent(
    state: TrackUiState,
    countParts: (String) -> Int,
    onSave: (String, String) -> Unit,
    onSetState: (Long, TrackUnitState) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var marking by rememberSaveable { mutableStateOf<Long?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding(),
    ) {
        BackHeader(kicker = stringResource(R.string.track_kicker), title = state.name, onBack = onBack)

        if (state.failed) NoticeBar(text = stringResource(R.string.today_action_failed))

        if (state.isMissing) {
            EmptyState(
                title = stringResource(R.string.track_gone_title),
                body = stringResource(R.string.track_gone_body),
            )
        } else {
            Parts(state = state, onEdit = { editing = true }, onDelete = onDelete, onMark = { marking = it })
        }
    }

    TrackDialogs(
        state = state,
        editing = editing,
        marking = marking,
        countParts = countParts,
        onSave = { name, text ->
            onSave(name, text)
            editing = false
        },
        onSetState = { id, picked ->
            onSetState(id, picked)
            marking = null
        },
        onClose = {
            editing = false
            marking = null
        },
    )
}

/** Whichever of the two is open: the list editor, or the three answers for one part. */
@Composable
private fun TrackDialogs(
    state: TrackUiState,
    editing: Boolean,
    marking: Long?,
    countParts: (String) -> Int,
    onSave: (String, String) -> Unit,
    onSetState: (Long, TrackUnitState) -> Unit,
    onClose: () -> Unit,
) {
    if (editing) {
        TrackEditor(
            isNew = false,
            initialName = state.name,
            initialText = state.text,
            countParts = countParts,
            onSave = onSave,
            onDismiss = onClose,
        )
    }

    val unit = marking?.let { id -> state.units.firstOrNull { it.id == id } } ?: return

    MarkDialog(unit = unit, onPick = { picked -> onSetState(unit.id, picked) }, onDismiss = onClose)
}

@Composable
private fun Parts(
    state: TrackUiState,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMark: (Long) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { Standing(state = state) }

        item { SectionLabel(text = stringResource(R.string.track_parts)) }

        items(items = state.units, key = { it.id }) { unit ->
            UnitRow(unit = unit, leftOff = state.leftOff.takeIf { unit.isNext }, onClick = { onMark(unit.id) })
        }

        item {
            Row(
                modifier = Modifier.padding(Theme.spacing.medium),
                horizontalArrangement = Arrangement.spacedBy(Theme.spacing.small),
            ) {
                OutlineButton(text = stringResource(R.string.track_edit), onClick = onEdit)
                GhostButton(
                    text = stringResource(R.string.track_delete),
                    onClick = onDelete,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** The big number: which part is next, and how much of the whole is done. */
@Composable
private fun Standing(state: TrackUiState) {
    Column(modifier = Modifier.padding(horizontal = Theme.spacing.medium, vertical = 16.dp)) {
        Text(
            text = if (state.isFinished) {
                stringResource(R.string.track_all_done, state.total)
            } else {
                stringResource(R.string.track_standing, state.position, state.total)
            },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Kicker(
            text = if (state.isFinished) {
                stringResource(R.string.track_minutes_spent, state.minutesSpent)
            } else {
                stringResource(R.string.track_finished_count, state.finished) +
                    stringResource(R.string.plan_line_separator) +
                    stringResource(R.string.track_minutes_spent, state.minutesSpent)
            },
            modifier = Modifier.padding(top = Theme.spacing.small),
        )
    }

    HeavyRule()
}

@Composable
private fun UnitRow(unit: UnitUi, leftOff: String?, onClick: () -> Unit) {
    val done = unit.state == TrackUnitState.DONE
    val ink = when {
        unit.isNext -> MaterialTheme.colorScheme.onSurface
        done || unit.state == TrackUnitState.SKIPPED -> Theme.colours.faint
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (unit.isNext) Modifier.background(Theme.colours.raised) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Theme.spacing.medium, vertical = Theme.spacing.inset),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UnitNumber(unit = unit, done = done, ink = ink)

            Text(
                text = unit.title,
                style = MaterialTheme.typography.titleSmall,
                color = ink,
                modifier = Modifier.weight(1f),
            )

            unit.estimateMinutes?.let {
                Text(
                    text = stringResource(R.string.today_duration_min, it),
                    style = MaterialTheme.typography.labelMedium,
                    color = Theme.colours.faint,
                    modifier = Modifier.padding(start = Theme.spacing.small),
                )
            }
        }

        UnitNote(unit = unit, leftOff = leftOff)
    }

    HairlineRule(Modifier.padding(horizontal = Theme.spacing.medium))
}

/** The number, or a tick once the part is done. */
@Composable
private fun UnitNumber(unit: UnitUi, done: Boolean, ink: androidx.compose.ui.graphics.Color) {
    Box(modifier = Modifier.width(NumberColumn), contentAlignment = Alignment.CenterStart) {
        if (done) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = stringResource(R.string.track_state_done),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        } else {
            Text(text = unit.number.toString(), style = TimeStyle, color = ink)
        }
    }
}

/** The one line under a part worth saying: next, skipped, or where it stopped. */
@Composable
private fun UnitNote(unit: UnitUi, leftOff: String?) {
    val line = when {
        unit.isNext && leftOff != null -> stringResource(R.string.track_left_off, leftOff)
        unit.isNext -> stringResource(R.string.track_up_next)
        unit.state == TrackUnitState.SKIPPED -> stringResource(R.string.track_state_skipped)
        unit.state == TrackUnitState.IN_PROGRESS -> stringResource(R.string.track_state_started)
        else -> return
    }

    Text(
        text = line,
        style = MaterialTheme.typography.bodySmall,
        color = if (unit.isNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = NumberColumn, top = Theme.spacing.tight),
    )
}

/** Done, not yet, skip. Three answers, because a syllabus is never followed exactly. */
@Composable
private fun MarkDialog(unit: UnitUi, onPick: (TrackUnitState) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Panel {
            Column(modifier = Modifier.padding(Theme.spacing.medium)) {
                Kicker(text = stringResource(R.string.track_part_number, unit.number))

                Text(
                    text = unit.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = Theme.spacing.tight, bottom = Theme.spacing.medium),
                )

                Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.small)) {
                    OutlineButton(
                        text = stringResource(R.string.track_mark_done),
                        onClick = { onPick(TrackUnitState.DONE) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlineButton(
                        text = stringResource(R.string.track_mark_waiting),
                        onClick = { onPick(TrackUnitState.PENDING) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlineButton(
                        text = stringResource(R.string.track_mark_skipped),
                        onClick = { onPick(TrackUnitState.SKIPPED) },
                        modifier = Modifier.fillMaxWidth(),
                        muted = true,
                    )
                }

                GhostButton(
                    text = stringResource(R.string.editor_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.padding(top = Theme.spacing.small).align(Alignment.End),
                )
            }
        }
    }
}

@Preview(name = "Syllabus", showBackground = true)
@Composable
private fun TrackPreview() {
    BuildOrBreakTheme {
        TrackContent(
            state = TrackUiState(
                id = 1,
                name = "Backend course",
                text = "",
                units = persistentListOf(
                    UnitUi(1, 1, "HTTP basics", 30, TrackUnitState.DONE, isNext = false),
                    UnitUi(2, 2, "REST and JSON", 45, TrackUnitState.SKIPPED, isNext = false),
                    UnitUi(3, 3, "Authentication", null, TrackUnitState.IN_PROGRESS, isNext = true),
                    UnitUi(4, 4, "Deployment", 60, TrackUnitState.PENDING, isNext = false),
                ),
                position = 3,
                total = 4,
                finished = 1,
                minutesSpent = 75,
                leftOff = "Page 42, the JWT example",
            ),
            countParts = { 0 },
            onSave = { _, _ -> },
            onSetState = { _, _ -> },
            onDelete = {},
            onBack = {},
        )
    }
}
