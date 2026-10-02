package com.buildorbreak.app.feature.track

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buildorbreak.app.R
import com.buildorbreak.app.feature.about.BackHeader
import com.buildorbreak.core.designsystem.component.EmptyState
import com.buildorbreak.core.designsystem.component.HairlineRule
import com.buildorbreak.core.designsystem.component.Kicker
import com.buildorbreak.core.designsystem.component.NoticeBar
import com.buildorbreak.core.designsystem.component.OutlineButton
import com.buildorbreak.core.designsystem.component.StepBars
import com.buildorbreak.core.designsystem.theme.BuildOrBreakTheme
import com.buildorbreak.core.designsystem.theme.Theme
import kotlinx.collections.immutable.persistentListOf

/**
 * Every syllabus on the plan.
 *
 * A shake is the same every day; a course is not. Each row here is a list
 * of parts that one step works through, a sitting at a time, and the row
 * says which part is up next.
 */
@Composable
fun TracksScreen(
    onOpenTrack: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TracksViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    TracksContent(
        state = state,
        countParts = viewModel::partsIn,
        onCreate = viewModel::onCreate,
        onOpenTrack = onOpenTrack,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun TracksContent(
    state: TracksUiState,
    countParts: (String) -> Int,
    onCreate: (String, String) -> Unit,
    onOpenTrack: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var creating by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding(),
    ) {
        BackHeader(
            kicker = pluralStringResource(R.plurals.tracks_kicker, state.tracks.size, state.tracks.size),
            title = stringResource(R.string.tracks_title),
            onBack = onBack,
        )

        if (state.failed) NoticeBar(text = stringResource(R.string.today_action_failed))

        TracksList(state = state, onOpenTrack = onOpenTrack, onNew = { creating = true })
    }

    if (creating) {
        TrackEditor(
            isNew = true,
            initialName = "",
            initialText = "",
            countParts = countParts,
            onSave = { name, text ->
                onCreate(name, text)
                creating = false
            },
            onDismiss = { creating = false },
        )
    }
}

/** The rows, or the invitation to make the first one. */
@Composable
private fun TracksList(state: TracksUiState, onOpenTrack: (Long) -> Unit, onNew: () -> Unit) {
    if (state.tracks.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.tracks_none_title),
            body = stringResource(R.string.tracks_none_body),
        ) {
            OutlineButton(text = stringResource(R.string.tracks_add), onClick = onNew)
        }

        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items = state.tracks, key = { it.id }) { track ->
            TrackRow(track = track, onOpen = { onOpenTrack(track.id) })
        }

        item {
            OutlineButton(
                text = stringResource(R.string.tracks_add),
                onClick = onNew,
                modifier = Modifier.padding(Theme.spacing.medium),
            )
        }
    }
}

@Composable
private fun TrackRow(track: TrackRowUi, onOpen: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = Theme.spacing.medium)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onOpen)
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackRowBody(track = track, modifier = Modifier.weight(1f))

            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = Theme.colours.faint,
                modifier = Modifier.size(18.dp),
            )
        }

        HairlineRule()
    }
}

@Composable
private fun TrackRowBody(track: TrackRowUi, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = track.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Kicker(text = progressText(track), modifier = Modifier.padding(top = Theme.spacing.tight))

        track.nextTitle?.let {
            Text(
                text = stringResource(R.string.track_next_part, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Theme.spacing.tight),
            )
        }

        StepBars(
            total = track.total,
            completed = track.finished,
            modifier = Modifier.padding(top = Theme.spacing.small),
        )
    }
}

/** "PART 3 OF 30 · 2 DONE", or "ALL 30 DONE". */
@Composable
internal fun progressText(track: TrackRowUi): String = if (track.isFinished) {
    stringResource(R.string.track_all_done, track.total)
} else {
    stringResource(R.string.track_progress, track.position, track.total, track.finished)
}

@Preview(name = "Syllabuses", showBackground = true)
@Composable
private fun TracksPreview() {
    BuildOrBreakTheme {
        TracksContent(
            state = TracksUiState(
                tracks = persistentListOf(
                    TrackRowUi(1, "Backend course", position = 3, total = 30, finished = 2, nextTitle = "HTTP basics"),
                    TrackRowUi(2, "Kafka, the book", position = 0, total = 12, finished = 12, nextTitle = null),
                ),
            ),
            countParts = { 0 },
            onCreate = { _, _ -> },
            onOpenTrack = {},
            onBack = {},
        )
    }
}
