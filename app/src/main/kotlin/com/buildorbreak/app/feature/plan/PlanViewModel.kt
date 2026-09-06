package com.buildorbreak.app.feature.plan

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buildorbreak.core.domain.usecase.ArchiveItemUseCase
import com.buildorbreak.core.domain.usecase.ObservePlanUseCase
import com.buildorbreak.core.domain.usecase.PlanContents
import com.buildorbreak.core.model.enums.Salience
import com.buildorbreak.core.model.plan.Anchor
import com.buildorbreak.core.model.plan.Item
import com.buildorbreak.core.model.plan.Weekdays
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val CLOCK: DateTimeFormatter
    get() = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

@Immutable
data class PlanUiState(
    val planName: String,
    val templateName: String,
    val items: ImmutableList<PlanItemRow>,
    val hasPlan: Boolean,
) {
    val isEmpty: Boolean get() = hasPlan && items.isEmpty()

    companion object {
        val Empty = PlanUiState(
            planName = "",
            templateName = "",
            items = persistentListOf(),
            hasPlan = false,
        )
    }
}

/**
 * One row of the editor.
 *
 * [weekdaysText] is empty when the step runs every day, because a label that
 * appears on every row carries no information and only makes the list harder to
 * scan. It appears exactly when it means something.
 */
@Immutable
data class PlanItemRow(
    val id: Long,
    val title: String,
    val whenText: String,
    val weekdaysText: String,
    val salience: Salience,
    val pinned: Boolean,
    val hasMinimum: Boolean,
)

/**
 * The plan as written, not as it resolves today.
 *
 * Every item on the template is listed, including the ones that do not run
 * today. An editor that hid the Saturday steps on a Tuesday would be an editor
 * nobody could use to fix Saturday.
 */
@HiltViewModel
class PlanViewModel @Inject constructor(
    observePlan: ObservePlanUseCase,
    private val archive: ArchiveItemUseCase,
) : ViewModel() {

    val state: StateFlow<PlanUiState> = observePlan()
        .map(::toUiState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = PlanUiState.Empty,
        )

    fun onArchive(itemId: Long) = viewModelScope.launch { archive(itemId) }

    private fun toUiState(contents: PlanContents): PlanUiState = when (contents) {
        PlanContents.None -> PlanUiState.Empty

        is PlanContents.Loaded -> PlanUiState(
            planName = contents.planName,
            templateName = contents.template.name,
            items = contents.items.map(::toRow).toImmutableList(),
            hasPlan = true,
        )
    }

    private fun toRow(item: Item) = PlanItemRow(
        id = item.id,
        title = item.title,
        whenText = describe(item.anchor),
        weekdaysText = describe(item.weekdays),
        salience = item.salience,
        pinned = item.pinned,
        hasMinimum = item.hasMinimum,
    )

    /**
     * What the anchor does, in the fewest words that are still true.
     *
     * A relative offset names its parent by id here rather than by title,
     * because resolving the title would mean a second lookup per row and the
     * editor already shows the parent directly above it in almost every case.
     */
    private fun describe(anchor: Anchor): String = when (anchor) {
        is Anchor.Fixed -> anchor.at.format(CLOCK)
        is Anchor.Relative -> "+${anchor.offset.inWholeMinutes}m"
        is Anchor.Window -> "${anchor.from.format(CLOCK)} to ${anchor.to.format(CLOCK)}"
        is Anchor.Interval ->
            "every ${anchor.every.inWholeMinutes}m, ${anchor.from.format(CLOCK)} to ${anchor.to.format(CLOCK)}"
    }

    /** Empty for every day, so the label only appears when it says something. */
    private fun describe(weekdays: Weekdays): String = if (weekdays.isEveryDay) {
        ""
    } else {
        weekdays.days.joinToString(" ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
