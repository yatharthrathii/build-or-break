package com.buildorbreak.app.feature.plan

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buildorbreak.app.format.ClockFormat
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.common.result.getOrNull
import com.buildorbreak.core.domain.usecase.DeleteTemplateUseCase
import com.buildorbreak.core.domain.usecase.ObservePlanUseCase
import com.buildorbreak.core.domain.usecase.PlanContents
import com.buildorbreak.core.domain.usecase.SaveTemplateUseCase
import com.buildorbreak.core.model.enums.DayMode
import com.buildorbreak.core.model.enums.Salience
import com.buildorbreak.core.model.plan.Anchor
import com.buildorbreak.core.model.plan.DayTemplate
import com.buildorbreak.core.model.plan.Item
import com.buildorbreak.core.model.plan.Weekdays
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class TemplateTab(val id: Long, val name: String, val weekdays: Weekdays, val isDefault: Boolean)

@Immutable
data class PlanUiState(
    val planId: Long,
    val planName: String,
    val templates: ImmutableList<TemplateTab>,
    val selectedIndex: Int,
    val rows: ImmutableList<PlanItemRow>,
    /** The earliest and latest clock times on the template, for the kicker. */
    val firstTime: String?,
    val lastTime: String?,
    val hasPlan: Boolean,
) {
    val isEmpty: Boolean get() = hasPlan && rows.isEmpty()

    companion object {
        val Empty = PlanUiState(
            planId = 0,
            planName = "",
            templates = persistentListOf(),
            selectedIndex = 0,
            rows = persistentListOf(),
            firstTime = null,
            lastTime = null,
            hasPlan = false,
        )
    }
}

/** What kind of time a step keeps, with the facts the row needs to say so. */
@Immutable
sealed interface PlanKind {
    data class Fixed(val at: String) : PlanKind

    data class After(val parentTitle: String, val offsetMinutes: Int) : PlanKind

    data class Window(val from: String, val to: String, val minutesWide: Int) : PlanKind

    data class Every(val minutes: Int, val from: String, val to: String) : PlanKind
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
    val kind: PlanKind,
    val salience: Salience,
    val pinned: Boolean,
    /** How many relative steps hang off this one. */
    val childCount: Int,
    val weekdaysText: String,
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
    private val saveTemplate: SaveTemplateUseCase,
    private val deleteTemplate: DeleteTemplateUseCase,
    private val clock: ClockFormat,
) : ViewModel() {

    private val selectedTemplate = MutableStateFlow<Long?>(null)

    /** The last thing observed, so a save can copy the fields the dialog does not edit. */
    private var latest: PlanContents.Loaded? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<PlanUiState> = selectedTemplate
        .flatMapLatest { observePlan(it) }
        .map(::toUiState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = PlanUiState.Empty,
        )

    fun onSelectTemplate(id: Long) {
        selectedTemplate.value = id
    }

    /**
     * Writes a template. [id] null makes a new one and selects it.
     *
     * A new template is never the default; the plan already has one and two
     * defaults would be a coin toss every morning. Its order is last.
     */
    fun onSaveTemplate(id: Long?, name: String, weekdays: Weekdays) = viewModelScope.launch {
        val loaded = latest ?: return@launch
        val existing = loaded.templates.firstOrNull { it.id == id }

        val template = existing?.copy(name = name.trim(), weekdays = weekdays) ?: DayTemplate(
            id = 0,
            planId = loaded.planId,
            name = name.trim(),
            weekdays = weekdays,
            isDefault = loaded.templates.isEmpty(),
            mode = DayMode.NORMAL,
            sortOrder = loaded.templates.size,
        )

        saveTemplate(template).getOrNull()?.let { selectedTemplate.value = it }
    }

    /** Refused by the use case when it is the last one. The screen never offers that. */
    fun onDeleteTemplate(id: Long) = viewModelScope.launch {
        val loaded = latest ?: return@launch
        if (deleteTemplate(loaded.planId, id) is Outcome.Success) selectedTemplate.value = null
    }

    private fun toUiState(contents: PlanContents): PlanUiState = when (contents) {
        PlanContents.None -> PlanUiState.Empty

        is PlanContents.Loaded -> {
            latest = contents
            val titles = contents.items.associate { it.id to it.title }
            val starts = contents.items.mapNotNull { startOf(it.anchor) }

            PlanUiState(
                planId = contents.planId,
                planName = contents.planName,
                templates = contents.templates
                    .map { TemplateTab(it.id, it.name, it.weekdays, it.isDefault) }
                    .toImmutableList(),
                selectedIndex = contents.templates.indexOfFirst { it.id == contents.template.id }.coerceAtLeast(0),
                rows = contents.items.map { toRow(it, titles, contents.items) }.toImmutableList(),
                firstTime = starts.minOrNull()?.let(clock::format),
                lastTime = starts.maxOrNull()?.let(clock::format),
                hasPlan = true,
            )
        }
    }

    private fun toRow(item: Item, titles: Map<Long, String>, all: List<Item>) = PlanItemRow(
        id = item.id,
        title = item.title,
        kind = kindOf(item.anchor, titles),
        salience = item.salience,
        pinned = item.pinned,
        childCount = all.count { (it.anchor as? Anchor.Relative)?.parentItemId == item.id },
        weekdaysText = describe(item.weekdays),
    )

    private fun kindOf(anchor: Anchor, titles: Map<Long, String>): PlanKind = when (anchor) {
        is Anchor.Fixed -> PlanKind.Fixed(clock.format(anchor.at))

        is Anchor.Relative -> PlanKind.After(
            parentTitle = titles[anchor.parentItemId].orEmpty(),
            offsetMinutes = anchor.offset.inWholeMinutes.toInt(),
        )

        is Anchor.Window -> PlanKind.Window(
            from = clock.format(anchor.from),
            to = clock.format(anchor.to),
            minutesWide = java.time.Duration.between(anchor.from, anchor.to).toMinutes().toInt(),
        )

        is Anchor.Interval -> PlanKind.Every(
            minutes = anchor.every.inWholeMinutes.toInt(),
            from = clock.format(anchor.from),
            to = clock.format(anchor.to),
        )
    }

    /** Where a step starts on the clock. Relative steps have no clock of their own. */
    private fun startOf(anchor: Anchor): LocalTime? = when (anchor) {
        is Anchor.Fixed -> anchor.at
        is Anchor.Window -> anchor.from
        is Anchor.Interval -> anchor.from
        is Anchor.Relative -> null
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
