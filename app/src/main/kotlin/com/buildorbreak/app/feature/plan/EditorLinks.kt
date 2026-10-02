package com.buildorbreak.app.feature.plan

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.buildorbreak.app.R
import com.buildorbreak.app.feature.today.isFileLink
import com.buildorbreak.app.feature.today.rememberLinkLabel
import com.buildorbreak.app.feature.track.TrackEditor
import com.buildorbreak.core.designsystem.component.GhostButton
import com.buildorbreak.core.designsystem.component.Kicker
import com.buildorbreak.core.designsystem.component.OutlineButton
import com.buildorbreak.core.designsystem.component.Panel
import com.buildorbreak.core.designsystem.component.PickerField
import com.buildorbreak.core.designsystem.theme.Theme

/**
 * Something to open when the step comes round.
 *
 * A link typed in, or a file picked from the phone. Either way it sits on
 * the card as one button, because the moment a step arrives is the moment
 * somebody wants the video, the PDF or the playlist, and not a moment to go
 * looking for it.
 */
@Composable
internal fun LinkSection(state: ItemEditorUiState, onChange: (ItemEditorUiState) -> Unit, onPickFile: () -> Unit) {
    Column {
        if (state.link.isFileLink()) {
            PickerField(
                label = stringResource(R.string.editor_link),
                value = rememberLinkLabel(state.link),
                onClick = onPickFile,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            TextBox(
                label = stringResource(R.string.editor_link),
                value = state.link,
                onValueChange = { onChange(state.copy(link = it)) },
                placeholder = stringResource(R.string.editor_link_hint),
            )
        }

        Text(
            text = stringResource(R.string.editor_link_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Theme.spacing.small),
        )

        LinkButtons(state = state, onChange = onChange, onPickFile = onPickFile)
    }
}

@Composable
private fun LinkButtons(state: ItemEditorUiState, onChange: (ItemEditorUiState) -> Unit, onPickFile: () -> Unit) {
    Row(
        modifier = Modifier.padding(top = Theme.spacing.small),
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.small),
    ) {
        OutlineButton(text = stringResource(R.string.editor_link_pick), onClick = onPickFile)

        if (state.link.isNotBlank()) {
            GhostButton(
                text = stringResource(R.string.editor_link_remove),
                onClick = { onChange(state.copy(link = "")) },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The syllabus this step works through, if it works through one.
 *
 * "None" is the default and stays the default. Most steps repeat; this is
 * for the ones that go somewhere, and each time one of those is done it
 * moves a part along the list.
 */
@Composable
internal fun TrackSection(
    state: ItemEditorUiState,
    onChange: (ItemEditorUiState) -> Unit,
    countParts: (String) -> Int,
    onCreateTrack: (String, String) -> Unit,
) {
    var choosing by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }

    TrackField(state = state, onClick = { choosing = true })

    if (choosing) {
        TrackDialog(
            tracks = state.tracks,
            selected = state.trackId,
            onPick = {
                onChange(state.copy(trackId = it))
                choosing = false
            },
            onNew = {
                choosing = false
                creating = true
            },
            onDismiss = { choosing = false },
        )
    }

    if (creating) {
        TrackEditor(
            isNew = true,
            initialName = "",
            initialText = "",
            countParts = countParts,
            onSave = { name, text ->
                onCreateTrack(name, text)
                creating = false
            },
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun TrackField(state: ItemEditorUiState, onClick: () -> Unit) {
    Column {
        PickerField(
            label = stringResource(R.string.editor_track),
            value = state.track?.let { trackLine(it) } ?: stringResource(R.string.editor_track_none),
            onClick = onClick,
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
            text = stringResource(if (state.track == null) R.string.editor_track_body else R.string.editor_track_on),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Theme.spacing.small),
        )
    }
}

/** "Backend course · part 3 of 30". */
@Composable
internal fun trackLine(track: TrackChoice): String = if (track.isFinished) {
    stringResource(R.string.editor_track_finished, track.name, track.total)
} else {
    stringResource(R.string.editor_track_line, track.name, track.position, track.total)
}

@Composable
private fun TrackDialog(
    tracks: List<TrackChoice>,
    selected: Long?,
    onPick: (Long?) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Panel {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Kicker(
                    text = stringResource(R.string.editor_track_pick),
                    modifier = Modifier.padding(horizontal = Theme.spacing.medium, vertical = 10.dp),
                )

                ChoiceRow(
                    text = stringResource(R.string.editor_track_none),
                    chosen = selected == null,
                    onClick = { onPick(null) },
                )

                tracks.forEach { track ->
                    ChoiceRow(text = trackLine(track), chosen = track.id == selected, onClick = { onPick(track.id) })
                }

                ChoiceRow(text = stringResource(R.string.editor_track_new), chosen = false, onClick = onNew)
            }
        }
    }
}
