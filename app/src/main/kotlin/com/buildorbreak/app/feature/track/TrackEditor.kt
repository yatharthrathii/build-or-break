package com.buildorbreak.app.feature.track

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.buildorbreak.app.R
import com.buildorbreak.app.feature.plan.TextBox
import com.buildorbreak.core.designsystem.component.FillButton
import com.buildorbreak.core.designsystem.component.GhostButton
import com.buildorbreak.core.designsystem.component.HeavyRule
import com.buildorbreak.core.designsystem.theme.Theme
import java.util.Locale

/** A syllabus is long. Ten lines of text area before it scrolls. */
private const val TEXT_LINES = 10

/**
 * A syllabus, as text.
 *
 * A name and a box to paste into, and that is the whole editor. The app
 * never writes the syllabus: the user pastes one in, one part per line,
 * and the line under the box says how many parts were understood before
 * anything is saved. Full screen rather than a dialog, because a course
 * outline is thirty lines and a dialog is a slot for three.
 */
@Composable
internal fun TrackEditor(
    isNew: Boolean,
    initialName: String,
    initialText: String,
    /** How many parts the text would become. The ViewModel's parser, handed down. */
    countParts: (String) -> Int,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var text by rememberSaveable { mutableStateOf(initialText) }
    val parts = remember(text) { countParts(text) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .statusBarsPadding()
                .imePadding(),
        ) {
            EditorHeader(
                isNew = isNew,
                canSave = name.isNotBlank() && parts > 0,
                onSave = { onSave(name, text) },
                onDismiss = onDismiss,
            )

            EditorFields(
                name = name,
                text = text,
                parts = parts,
                isNew = isNew,
                onName = { name = it },
                onText = { text = it },
            )
        }
    }
}

@Composable
private fun EditorFields(
    name: String,
    text: String,
    parts: Int,
    isNew: Boolean,
    onName: (String) -> Unit,
    onText: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Theme.spacing.medium)
            .padding(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        TextBox(
            label = stringResource(R.string.track_name),
            value = name,
            onValueChange = onName,
            placeholder = stringResource(R.string.track_name_hint),
        )

        TextBox(
            label = stringResource(R.string.track_text),
            value = text,
            onValueChange = onText,
            placeholder = stringResource(R.string.track_text_hint),
            help = stringResource(R.string.track_text_body),
            minLines = TEXT_LINES,
        )

        PartsLine(parts = parts, isNew = isNew)
    }
}

@Composable
private fun EditorHeader(
    isNew: Boolean,
    canSave: Boolean,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Theme.spacing.medium, end = Theme.spacing.medium, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(if (isNew) R.string.track_new_title else R.string.track_edit_title)
                    .uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )

            GhostButton(text = stringResource(R.string.editor_cancel), onClick = onDismiss)
            FillButton(text = stringResource(R.string.editor_save), onClick = onSave, enabled = canSave)
        }

        HeavyRule()
    }
}

/** "12 parts found", or why Save is off. On an edit, what shortening the text costs. */
@Composable
private fun PartsLine(parts: Int, isNew: Boolean) {
    Column {
        Text(
            text = if (parts == 0) {
                stringResource(R.string.track_parts_none)
            } else {
                pluralStringResource(R.plurals.track_parts_found, parts, parts)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (parts ==
                0
            ) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )

        if (!isNew) {
            Text(
                text = stringResource(R.string.track_edit_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Theme.spacing.small),
            )
        }
    }
}
