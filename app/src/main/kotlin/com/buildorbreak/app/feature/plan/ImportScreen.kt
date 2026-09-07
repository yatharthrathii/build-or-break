package com.buildorbreak.app.feature.plan

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buildorbreak.app.R
import com.buildorbreak.core.designsystem.component.Badge
import com.buildorbreak.core.designsystem.component.BlockButton
import com.buildorbreak.core.designsystem.component.FillButton
import com.buildorbreak.core.designsystem.component.HairlineRule
import com.buildorbreak.core.designsystem.component.HeavyRule
import com.buildorbreak.core.designsystem.component.Kicker
import com.buildorbreak.core.designsystem.component.Panel
import com.buildorbreak.core.designsystem.theme.BuildOrBreakTheme
import com.buildorbreak.core.designsystem.theme.Theme
import com.buildorbreak.core.model.enums.Salience
import java.util.Locale
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
fun ImportScreen(
    onImported: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.stage) {
        if (state.stage == ImportStage.SAVED) {
            onImported()
            viewModel.onLeave()
        }
    }

    ImportContent(
        state = state,
        prompt = viewModel.promptToCopy,
        onTextChanged = viewModel::onTextChanged,
        onReview = viewModel::onReview,
        onBackToEditing = viewModel::onBackToEditing,
        onConfirm = viewModel::onConfirm,
        onBack = onBack,
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
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .imePadding(),
    ) {
        ImportHeader(
            title = stringResource(
                if (state.stage ==
                    ImportStage.EDITING
                ) {
                    R.string.import_title
                } else {
                    R.string.import_review_title
                },
            ),
            onBack = if (state.stage == ImportStage.EDITING) onBack else onBackToEditing,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (state.stage) {
                ImportStage.EDITING -> Editing(state, prompt, onTextChanged, onReview)
                else -> Reviewing(state, onConfirm)
            }
        }
    }
}

@Composable
private fun ImportHeader(title: String, onBack: () -> Unit) {
    Column {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(role = Role.Button, onClick = onBack),
            )

            Text(
                text = title.uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 14.dp),
            )
        }

        HeavyRule()
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

    TextBox(
        label = stringResource(R.string.import_paste_label),
        value = state.text,
        onValueChange = onTextChanged,
        placeholder = stringResource(R.string.import_paste_placeholder),
        minLines = 6,
    )

    BlockButton(
        text = stringResource(R.string.import_review),
        onClick = onReview,
        enabled = state.canReview,
        icon = Icons.AutoMirrored.Outlined.ArrowForward,
    )
}

/**
 * The primary path. Copying the prompt costs one tap and turns an unbounded
 * parsing problem into a bounded one, because whatever comes back is in the
 * shape the app guarantees it can read.
 */
@Composable
private fun PromptCard(onCopy: () -> Unit) {
    Panel {
        Column(modifier = Modifier.padding(14.dp)) {
            Kicker(text = stringResource(R.string.import_prompt_kicker), color = MaterialTheme.colorScheme.primary)

            Text(
                text = stringResource(R.string.import_prompt_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp),
            )

            Text(
                text = stringResource(R.string.import_prompt_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 5.dp, bottom = 12.dp),
            )

            FillButton(text = stringResource(R.string.import_copy_prompt), onClick = onCopy)
        }
    }
}

@Composable
private fun Reviewing(state: ImportUiState, onConfirm: (String) -> Unit) {
    var templateName by rememberSaveable { mutableStateOf("") }

    ReviewSummary(state)

    Column {
        HeavyRule()
        state.understood.forEach { preview -> UnderstoodRow(preview) }
    }

    NotUnderstood(lines = state.notUnderstood)

    if (state.failed) {
        Text(
            text = stringResource(R.string.import_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }

    TextBox(
        label = stringResource(R.string.import_name_label),
        value = templateName,
        onValueChange = { templateName = it },
        placeholder = stringResource(R.string.import_name_hint),
    )

    BlockButton(
        text = stringResource(R.string.import_confirm),
        onClick = { onConfirm(templateName) },
        enabled = state.understood.isNotEmpty(),
        icon = Icons.AutoMirrored.Outlined.ArrowForward,
    )
}

/** What was read, or the fact that nothing was. */
@Composable
private fun ReviewSummary(state: ImportUiState) {
    val text = if (state.nothingUnderstood) {
        stringResource(R.string.import_nothing_understood)
    } else {
        pluralStringResource(R.plurals.import_understood_count, state.understood.size, state.understood.size)
    }

    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(12.dp),
    ) {
        Kicker(
            text = pluralStringResource(R.plurals.import_not_understood_count, lines.size, lines.size),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )

        lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun UnderstoodRow(preview: ParsedPreview) {
    Column(modifier = Modifier.padding(vertical = 11.dp)) {
        Text(
            text = preview.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Row(
            modifier = Modifier.padding(top = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Badge(text = preview.whenText)
            Badge(text = stringResource(salienceLabel(preview.salience)), accent = preview.salience == Salience.ALARM)

            val notes = buildList {
                if (preview.pinned) add(stringResource(R.string.import_note_pinned))
                if (preview.hasMinimum) add(stringResource(R.string.import_note_minimum))
            }

            if (notes.isNotEmpty()) {
                Text(
                    text = notes.joinToString(stringResource(R.string.import_note_separator)),
                    style = MaterialTheme.typography.bodySmall,
                    color = Theme.colours.faint,
                )
            }
        }
    }

    HairlineRule()
}

// Previews ---------------------------------------------------------------------

@Preview(name = "Import, pasting", showBackground = true)
@Composable
private fun ImportEditingPreview() {
    BuildOrBreakTheme {
        ImportContent(ImportUiState.Empty, "", {}, {}, {}, {}, {})
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
            onBack = {},
        )
    }
}
