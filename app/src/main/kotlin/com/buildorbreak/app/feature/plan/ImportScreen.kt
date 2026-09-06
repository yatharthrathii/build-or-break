package com.buildorbreak.app.feature.plan

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buildorbreak.app.R
import com.buildorbreak.core.designsystem.component.InlineNotice
import com.buildorbreak.core.designsystem.component.QuietCard
import com.buildorbreak.core.designsystem.component.Rule
import com.buildorbreak.core.designsystem.theme.BuildOrBreakTheme
import com.buildorbreak.core.designsystem.theme.Theme
import com.buildorbreak.core.model.enums.Salience
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch

/**
 * Bringing in a routine that already exists.
 *
 * The app does not write plans. This is the front door, and the design follows
 * from one decision made in `PlanFormat`: rather than trying to parse every
 * shape of prose, the app hands the user a prompt for whichever AI tool wrote
 * their routine, and reads what comes back. The tolerant parser is the fallback
 * for the person who pastes something else anyway.
 *
 * Nothing is written until the review step has been seen. That is what makes a
 * best effort parser safe: a misread line is caught here by the person who wrote
 * the routine, not at six in the morning by the person relying on it.
 */
@Composable
fun ImportScreen(onImported: () -> Unit, modifier: Modifier = Modifier, viewModel: ImportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.stage) {
        if (state.stage == ImportStage.SAVED) onImported()
    }

    ImportContent(
        state = state,
        prompt = viewModel.promptToCopy,
        onTextChanged = viewModel::onTextChanged,
        onReview = viewModel::onReview,
        onBackToEditing = viewModel::onBackToEditing,
        onConfirm = viewModel::onConfirm,
        modifier = modifier,
    )
}

@Composable
fun ImportContent(
    state: ImportUiState,
    prompt: String,
    onTextChanged: (String) -> Unit,
    onReview: () -> Unit,
    onBackToEditing: () -> Unit,
    onConfirm: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { insets ->
        Column(
            modifier = Modifier
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(Theme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.medium),
        ) {
            when (state.stage) {
                ImportStage.EDITING -> Editing(
                    state = state,
                    prompt = prompt,
                    onTextChanged = onTextChanged,
                    onReview = onReview,
                )

                else -> Reviewing(
                    state = state,
                    onBackToEditing = onBackToEditing,
                    onConfirm = onConfirm,
                )
            }
        }
    }
}

@Composable
private fun Editing(
    state: ImportUiState,
    prompt: String,
    onTextChanged: (String) -> Unit,
    onReview: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val clipLabel = stringResource(R.string.import_clip_label)

    Text(text = stringResource(R.string.import_title), style = MaterialTheme.typography.headlineMedium)

    Text(
        text = stringResource(R.string.import_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    PromptCard(
        onCopy = {
            // The suspend clipboard API rather than the deprecated one. Copying
            // is the primary path into this screen, so it is worth using the
            // call that will still exist next year.
            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(clipLabel, prompt))) }
        },
    )

    Rule()

    OutlinedTextField(
        value = state.text,
        onValueChange = onTextChanged,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PasteFieldMinHeight),
        label = { Text(stringResource(R.string.import_paste_label)) },
        placeholder = { Text(stringResource(R.string.import_paste_placeholder)) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
    )

    Button(
        onClick = onReview,
        enabled = state.canReview,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.import_review))
    }
}

/**
 * The primary path. Copying the prompt costs one tap and turns an unbounded
 * parsing problem into a bounded one, because whatever comes back is in the
 * shape the app guarantees it can read.
 */
@Composable
private fun PromptCard(onCopy: () -> Unit) {
    QuietCard {
        Column(
            modifier = Modifier.padding(Theme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.small),
        ) {
            Text(text = stringResource(R.string.import_prompt_title), style = MaterialTheme.typography.titleMedium)

            Text(
                text = stringResource(R.string.import_prompt_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(onClick = onCopy) { Text(stringResource(R.string.import_copy_prompt)) }
        }
    }
}

@Composable
private fun Reviewing(state: ImportUiState, onBackToEditing: () -> Unit, onConfirm: (String) -> Unit) {
    var templateName by rememberSaveable { mutableStateOf("") }

    Text(text = stringResource(R.string.import_review_title), style = MaterialTheme.typography.headlineMedium)

    ReviewSummary(state)

    state.understood.forEach { preview -> UnderstoodRow(preview) }

    NotUnderstood(lines = state.notUnderstood)

    if (state.failed) {
        InlineNotice(text = stringResource(R.string.import_failed))
    }

    Rule()

    OutlinedTextField(
        value = templateName,
        onValueChange = { templateName = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(stringResource(R.string.import_name_label)) },
    )

    Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.small)) {
        TextButton(onClick = onBackToEditing) { Text(stringResource(R.string.import_back)) }

        Button(
            onClick = { onConfirm(templateName) },
            enabled = state.understood.isNotEmpty(),
        ) {
            Text(stringResource(R.string.import_confirm))
        }
    }
}

/** What was read, or the fact that nothing was. */
@Composable
private fun ReviewSummary(state: ImportUiState) {
    val text = if (state.nothingUnderstood) {
        stringResource(R.string.import_nothing_understood)
    } else {
        pluralStringResource(R.plurals.import_understood_count, state.understood.size, state.understood.size)
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Never dropped silently.
 *
 * A parser that quietly discards what it cannot read produces a plan that looks
 * complete and is missing the two steps that mattered.
 */
@Composable
private fun NotUnderstood(lines: kotlinx.collections.immutable.ImmutableList<String>) {
    if (lines.isEmpty()) return

    Rule()

    Text(
        text = pluralStringResource(R.plurals.import_not_understood_count, lines.size, lines.size),
        style = MaterialTheme.typography.titleMedium,
    )

    lines.forEach { line ->
        Text(text = line, style = MaterialTheme.typography.bodyMedium, color = Theme.colours.warning)
    }
}

@Composable
private fun UnderstoodRow(preview: ParsedPreview) {
    QuietCard {
        Column(
            modifier = Modifier.padding(Theme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.tight),
        ) {
            Text(text = preview.title, style = MaterialTheme.typography.titleMedium)

            Text(
                text = preview.whenText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val notes = buildList {
                if (preview.pinned) add(stringResource(R.string.import_note_pinned))
                if (preview.hasMinimum) add(stringResource(R.string.import_note_minimum))
            }

            if (notes.isNotEmpty()) {
                Text(
                    text = notes.joinToString(stringResource(R.string.import_note_separator)),
                    style = MaterialTheme.typography.labelMedium,
                    color = Theme.colours.faint,
                )
            }
        }
    }
}

private val PasteFieldMinHeight = 160.dp

// Previews ---------------------------------------------------------------------

@Preview(name = "Import, pasting", showBackground = true)
@Composable
private fun ImportEditingPreview() {
    BuildOrBreakTheme {
        ImportContent(
            state = ImportUiState.Empty,
            prompt = "",
            onTextChanged = {},
            onReview = {},
            onBackToEditing = {},
            onConfirm = {},
        )
    }
}

@Preview(name = "Import, reviewing", showBackground = true)
@Composable
private fun ImportReviewPreview() {
    BuildOrBreakTheme {
        ImportContent(
            state = ImportUiState.Empty.copy(
                stage = ImportStage.REVIEWING,
                understood = persistentListOf(
                    ParsedPreview("Wake up", "06:30", Salience.ALARM, hasMinimum = false, pinned = false),
                    ParsedPreview("Drink water", "+10m", Salience.SILENT, hasMinimum = false, pinned = false),
                    ParsedPreview("Study block", "07:30 to 09:30", Salience.NOTIFY, hasMinimum = true, pinned = false),
                ),
                notUnderstood = persistentListOf("remember to buy milk"),
            ),
            prompt = "",
            onTextChanged = {},
            onReview = {},
            onBackToEditing = {},
            onConfirm = {},
        )
    }
}
