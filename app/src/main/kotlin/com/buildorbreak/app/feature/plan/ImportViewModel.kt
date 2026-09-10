package com.buildorbreak.app.feature.plan

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buildorbreak.app.format.ClockFormat
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.domain.parse.ParsedItem
import com.buildorbreak.core.domain.parse.PlanFormat
import com.buildorbreak.core.domain.parse.PlanTextParser
import com.buildorbreak.core.domain.usecase.ImportPlanUseCase
import com.buildorbreak.core.model.enums.Salience
import com.buildorbreak.core.model.plan.Anchor
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the import has got to. Three stages, and the middle one is the point. */
enum class ImportStage {
    /** Pasting. Nothing has been read yet. */
    EDITING,

    /**
     * Read, and waiting to be agreed with.
     *
     * The stage that makes a best effort parser safe to ship. Without it a
     * misread line becomes a plan somebody only discovers at six in the morning
     * when an alarm does not fire.
     */
    REVIEWING,

    SAVED,
}

/**
 * One understood line, ready to be shown back.
 *
 * Formatted here rather than in the composable so a leaf never needs a locale or
 * a formatter, and so the review screen shows exactly the times that will be
 * written rather than its own rendering of them.
 */
@Immutable
data class ParsedPreview(
    val title: String,
    val whenText: String,
    val salience: Salience,
    val hasMinimum: Boolean,
    val pinned: Boolean,
    /** What will be written, so the row can be corrected before it is. */
    val anchor: Anchor,
)

@Immutable
data class ImportUiState(
    val text: String,
    val stage: ImportStage,
    val understood: ImmutableList<ParsedPreview>,
    /** Lines the parser could not read. Shown, never dropped. */
    val notUnderstood: ImmutableList<String>,
    val failed: Boolean,
) {
    val canReview: Boolean get() = text.isNotBlank()

    val nothingUnderstood: Boolean get() = stage == ImportStage.REVIEWING && understood.isEmpty()

    companion object {
        val Empty = ImportUiState(
            text = "",
            stage = ImportStage.EDITING,
            understood = persistentListOf(),
            notUnderstood = persistentListOf(),
            failed = false,
        )
    }
}

/**
 * Paste, read, agree, write.
 *
 * The parse happens in memory and nothing is saved until the user has seen what
 * was understood. That confirm step is the whole reason a tolerant parser is
 * acceptable here: a bad read is caught at import time by the person who wrote
 * the routine, rather than at six in the morning by the person relying on it.
 */
@HiltViewModel
class ImportViewModel @Inject constructor(
    private val importPlan: ImportPlanUseCase,
    private val clock: ClockFormat,
) : ViewModel() {

    private val parser = PlanTextParser()
    private var parsed: List<ParsedItem> = emptyList()
    private var unrecognised: List<String> = emptyList()

    private val _state = MutableStateFlow(ImportUiState.Empty)
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    /** The prompt the user hands to whichever tool wrote their routine. */
    val promptToCopy: String = PlanFormat.PROMPT

    fun onTextChanged(text: String) {
        _state.update { it.copy(text = text, stage = ImportStage.EDITING, failed = false) }
    }

    fun onReview() {
        val result = parser.parse(_state.value.text)
        parsed = result.items
        unrecognised = result.unrecognised

        _state.update {
            it.copy(
                stage = ImportStage.REVIEWING,
                understood = result.items.map(::toPreview).toImmutableList(),
                notUnderstood = result.unrecognised.toImmutableList(),
                failed = false,
            )
        }
    }

    /**
     * A row corrected by hand. The title and the time are what a best effort
     * parser most often gets wrong, and both are cheaper to fix here than in
     * the step editor after the plan exists.
     */
    fun onEditItem(index: Int, title: String, anchor: Anchor) {
        val item = parsed.getOrNull(index) ?: return

        replaceParsed(parsed.toMutableList().apply { set(index, item.copy(title = title, anchor = anchor)) })
    }

    fun onRemoveItem(index: Int) {
        if (index !in parsed.indices) return

        replaceParsed(parsed.filterIndexed { i, _ -> i != index })
    }

    /**
     * A line the parser could not read, given a time by the person who wrote
     * it. Slotted in by time so the plan comes out in day order.
     */
    fun onAddLine(index: Int, title: String, anchor: Anchor) {
        val line = unrecognised.getOrNull(index) ?: return
        unrecognised = unrecognised.filterIndexed { i, _ -> i != index }

        val start = startOf(anchor)
        val at = parsed.indexOfFirst { existing -> start != null && startOf(existing.anchor)?.isAfter(start) == true }
        val item = ParsedItem(title = title, anchor = anchor, sourceLine = line)

        replaceParsed(parsed.toMutableList().apply { add(if (at < 0) size else at, item) })
    }

    private fun replaceParsed(items: List<ParsedItem>) {
        parsed = items

        _state.update {
            it.copy(
                understood = items.map(::toPreview).toImmutableList(),
                notUnderstood = unrecognised.toImmutableList(),
            )
        }
    }

    private fun startOf(anchor: Anchor): LocalTime? = when (anchor) {
        is Anchor.Fixed -> anchor.at
        is Anchor.Window -> anchor.from
        is Anchor.Interval -> anchor.from
        is Anchor.Relative -> null
    }

    fun onBackToEditing() {
        _state.update { it.copy(stage = ImportStage.EDITING) }
    }

    /**
     * Writes only what was understood.
     *
     * The lines that were not are left behind rather than guessed at, and the
     * user has already seen them on the review screen. Importing eight of ten
     * steps is a routine somebody can finish by hand; importing a wrong guess is
     * an alarm at the wrong time.
     */
    fun onConfirm(templateName: String) = viewModelScope.launch {
        val outcome = importPlan(parsed, templateName.ifBlank { DEFAULT_TEMPLATE_NAME })

        _state.update {
            when (outcome) {
                is Outcome.Success -> it.copy(stage = ImportStage.SAVED, failed = false)
                is Outcome.Failure -> it.copy(failed = true)
            }
        }
    }

    /**
     * Back to blank once the saved plan has been handed on.
     *
     * The ViewModel outlives the screen, so without this a second visit would
     * open on a stage that immediately navigates away again.
     */
    fun onLeave() {
        parsed = emptyList()
        unrecognised = emptyList()
        _state.value = ImportUiState.Empty
    }

    private fun toPreview(item: ParsedItem) = ParsedPreview(
        title = item.title,
        whenText = describe(item.anchor),
        salience = item.salience ?: Salience.NOTIFY,
        hasMinimum = item.minimumTitle != null,
        pinned = item.pinned,
        anchor = item.anchor,
    )

    /**
     * What the anchor will actually do, in the shape the review screen shows.
     *
     * A relative offset reads as "15m after the step above" rather than naming a
     * parent id, because at this point the parent has no id and the user is
     * looking at a list where "the step above" is literally true.
     */
    private fun describe(anchor: Anchor): String = when (anchor) {
        is Anchor.Fixed -> clock.format(anchor.at)

        is Anchor.Relative -> "+${anchor.offset.inWholeMinutes}m"

        is Anchor.Window -> "${clock.format(anchor.from)} to ${clock.format(anchor.to)}"

        is Anchor.Interval ->
            "every ${anchor.every.inWholeMinutes}m, " +
                "${clock.format(anchor.from)} to ${clock.format(anchor.to)}"
    }

    private companion object {
        const val DEFAULT_TEMPLATE_NAME = "My routine"
    }
}
