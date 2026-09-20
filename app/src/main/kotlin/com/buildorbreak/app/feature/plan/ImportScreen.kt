package com.buildorbreak.app.feature.plan

import android.content.ClipData
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
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
import com.buildorbreak.core.domain.parse.PlanTextParser
import com.buildorbreak.core.model.enums.Salience
import com.buildorbreak.core.model.plan.Anchor
import java.time.LocalTime
import java.util.Locale
import kotlin.time.Duration.Companion.minutes
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch

/** No row open for editing. */
private const val NONE = -1

/** Where a line added by hand starts until its time is set. */
private val DEFAULT_NEW_TIME: LocalTime = LocalTime.of(8, 0)

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
        actions = ImportActions(
            onTextChanged = viewModel::onTextChanged,
            onReview = viewModel::onReview,
            onBackToEditing = viewModel::onBackToEditing,
            onConfirm = viewModel::onConfirm,
            onBack = onBack,
            onEdit = viewModel::onEditItem,
            onRemove = viewModel::onRemoveItem,
            onAdd = viewModel::onAddLine,
        ),
        modifier = modifier,
    )
}

/** Everything the screen can do, in one handle. */
@Immutable
data class ImportActions(
    val onTextChanged: (String) -> Unit,
    val onReview: () -> Unit,
    val onBackToEditing: () -> Unit,
    val onConfirm: (String) -> Unit,
    val onBack: () -> Unit,
    val onEdit: (Int, String, Anchor) -> Unit,
    val onRemove: (Int) -> Unit,
    val onAdd: (Int, String, Anchor) -> Unit,
) {
    companion object {
        val None = ImportActions({}, {}, {}, {}, {}, { _, _, _ -> }, {}, { _, _, _ -> })
    }
}

@Composable
fun ImportContent(
    state: ImportUiState,
    prompt: String,
    actions: ImportActions,
    modifier: Modifier = Modifier,
) {
    // The phone's back does what the arrow does. On the review stage that
    // is back to the text, not out of the screen: somebody who sees a line
    // was not understood presses back to fix it, and leaving instead threw
    // the paste away and reopened on the same stale preview next time.
    BackHandler(enabled = state.stage != ImportStage.EDITING, onBack = actions.onBackToEditing)

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
            onBack = if (state.stage == ImportStage.EDITING) actions.onBack else actions.onBackToEditing,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Theme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (state.stage) {
                ImportStage.EDITING -> Editing(state, prompt, actions.onTextChanged, actions.onReview)
                else -> Reviewing(state, actions)
            }
        }
    }
}

@Composable
private fun ImportHeader(title: String, onBack: () -> Unit) {
    Column {
        Row(
            modifier = Modifier.padding(horizontal = Theme.spacing.medium, vertical = 12.dp),
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
                modifier = Modifier.padding(start = Theme.spacing.inset),
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
                modifier = Modifier.padding(top = Theme.spacing.tight, bottom = 12.dp),
            )

            FillButton(text = stringResource(R.string.import_copy_prompt), onClick = onCopy)
        }
    }
}

@Composable
private fun Reviewing(state: ImportUiState, actions: ImportActions) {
    var templateName by rememberSaveable { mutableStateOf("") }
    var editing by rememberSaveable { mutableIntStateOf(NONE) }
    var adding by rememberSaveable { mutableIntStateOf(NONE) }

    ReviewSummary(state)

    Column {
        HeavyRule()
        state.understood.forEachIndexed { index, preview -> UnderstoodRow(preview, onClick = { editing = index }) }
    }

    NotUnderstood(lines = state.notUnderstood, onPick = { adding = it })

    ConfirmBlock(
        state = state,
        templateName = templateName,
        onTemplateName = { templateName = it },
        onConfirm = { actions.onConfirm(templateName) },
    )

    ReviewSheets(
        state = state,
        editing = editing,
        adding = adding,
        actions = actions,
        onClose = {
            editing = NONE
            adding = NONE
        },
    )
}

/** The name box and the save button, with the failure line above them when there is one. */
@Composable
private fun ConfirmBlock(
    state: ImportUiState,
    templateName: String,
    onTemplateName: (String) -> Unit,
    onConfirm: () -> Unit,
) {
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
        onValueChange = onTemplateName,
        placeholder = stringResource(R.string.import_name_hint),
    )

    BlockButton(
        text = stringResource(R.string.import_confirm),
        onClick = onConfirm,
        enabled = state.understood.isNotEmpty(),
        icon = Icons.AutoMirrored.Outlined.ArrowForward,
    )
}

/**
 * The sheet for the row being corrected, or the line being added. One at a
 * time; both indexes are [NONE] when neither is open.
 */
@Composable
private fun ReviewSheets(
    state: ImportUiState,
    editing: Int,
    adding: Int,
    actions: ImportActions,
    onClose: () -> Unit,
) {
    state.understood.getOrNull(editing)?.let { preview ->
        key(editing) {
            ImportEditSheet(
                kicker = stringResource(R.string.import_edit_kicker),
                title = preview.title,
                anchor = preview.anchor,
                onSave = { title, anchor ->
                    actions.onEdit(editing, title, anchor)
                    onClose()
                },
                onRemove = {
                    actions.onRemove(editing)
                    onClose()
                },
                onDismiss = onClose,
            )
        }
    }

    state.notUnderstood.getOrNull(adding)?.let { line ->
        key(adding) {
            ImportEditSheet(
                kicker = stringResource(R.string.import_add_kicker),
                title = line,
                anchor = Anchor.Fixed(DEFAULT_NEW_TIME),
                onSave = { title, anchor ->
                    actions.onAdd(adding, title, anchor)
                    onClose()
                },
                onRemove = null,
                onDismiss = onClose,
            )
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

    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

    if (!state.nothingUnderstood) {
        Text(
            text = stringResource(R.string.import_tap_to_fix),
            style = MaterialTheme.typography.bodySmall,
            color = Theme.colours.faint,
        )
    }
}

/**
 * Never dropped silently.
 *
 * A parser that quietly discards what it cannot read produces a plan that looks
 * complete and is missing the two steps that mattered.
 */
@Composable
private fun NotUnderstood(lines: kotlinx.collections.immutable.ImmutableList<String>, onPick: (Int) -> Unit) {
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

        Text(
            text = stringResource(R.string.import_tap_to_add),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(top = 4.dp),
        )

        lines.forEachIndexed { index, line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clickable(role = Role.Button) { onPick(index) },
            )
        }
    }
}

@Composable
private fun UnderstoodRow(preview: ParsedPreview, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Theme.spacing.inset),
    ) {
        Text(
            text = preview.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Row(
            modifier = Modifier.padding(top = Theme.spacing.tight),
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
        ImportContent(ImportUiState.Empty, "", ImportActions.None)
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
                    ParsedPreview(
                        title = "Wake up",
                        whenText = "06:30",
                        salience = Salience.ALARM,
                        hasMinimum = false,
                        pinned = false,
                        anchor = Anchor.Fixed(LocalTime.of(6, 30)),
                    ),
                    ParsedPreview(
                        title = "Drink water",
                        whenText = "+10m",
                        salience = Salience.SILENT,
                        hasMinimum = false,
                        pinned = false,
                        anchor = Anchor.Relative(PlanTextParser.PARENT_UNRESOLVED, 10.minutes),
                    ),
                    ParsedPreview(
                        title = "Study block",
                        whenText = "07:30 to 09:30",
                        salience = Salience.NOTIFY,
                        hasMinimum = true,
                        pinned = false,
                        anchor = Anchor.Window(LocalTime.of(7, 30), LocalTime.of(9, 30)),
                    ),
                ),
                notUnderstood = persistentListOf("remember to buy milk"),
            ),
            prompt = "",
            actions = ImportActions.None,
        )
    }
}
