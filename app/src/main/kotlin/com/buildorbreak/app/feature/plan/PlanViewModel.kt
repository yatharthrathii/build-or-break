package com.buildorbreak.app.feature.plan

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buildorbreak.app.format.ClockFormat
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.common.result.getOrNull
import com.buildorbreak.core.domain.usecase.DeleteBlockUseCase
import com.buildorbreak.core.domain.usecase.DeleteTemplateUseCase
import com.buildorbreak.core.domain.usecase.ObservePlanUseCase
import com.buildorbreak.core.domain.usecase.PlanContents
import com.buildorbreak.core.domain.usecase.SaveBlockUseCase
import com.buildorbreak.core.domain.usecase.SaveTemplateUseCase
import com.buildorbreak.core.model.enums.DayMode
import com.buildorbreak.core.model.enums.Salience
import com.buildorbreak.core.model.enums.ValueKind
import com.buildorbreak.core.model.plan.Anchor
import com.buildorbreak.core.model.plan.Block
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

/**
 * One group heading on the plan.
 *
 * [count] belongs on the heading because it is the number that explains the
 * behaviour underneath it: five steps in a group is one interruption, and
 * seeing the five is what makes that make sense.
 */
@Immutable
data class PlanGroupRow(
    val id: Long,
    val title: String,
    /** Already formatted, for the heading. */
    val time: String,
    /** The same moment, unformatted, so the dialog can open on it. */
    val at: LocalTime,
    val salience: Salience,
    val count: Int,
)

/**
 * A run of steps under one heading, or a loose step under none.
 *
 * Built here rather than on the screen. Deciding where a group starts and
 * which steps belong to it is a fact about the plan, and a screen that worked
 * it out would be a second place for it to be worked out differently.
 */
@Immutable
data class PlanSection(val group: PlanGroupRow?, val rows: ImmutableList<PlanItemRow>)

@Immutable
data class PlanUiState(
    val planId: Long,
    val planName: String,
    val templateId: Long,
    val templates: ImmutableList<TemplateTab>,
    val selectedIndex: Int,
    val rows: ImmutableList<PlanItemRow>,
    /** The same steps as [rows], grouped, in the order they are drawn. */
    val sections: ImmutableList<PlanSection>,
    val groups: ImmutableList<PlanGroupRow>,
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
            templateId = 0,
            templates = persistentListOf(),
            selectedIndex = 0,
            rows = persistentListOf(),
            sections = persistentListOf(),
            groups = persistentListOf(),
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
    /** The group this step is in, or null. Decides which section it lands in. */
    val groupId: Long? = null,
    /** Whether it carries a note. Shown as a mark, never as the note itself. */
    val hasNote: Boolean = false,
    /** Whether it asks for a number when it is completed. */
    val measured: Boolean = false,
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
    private val saveBlock: SaveBlockUseCase,
    private val deleteBlock: DeleteBlockUseCase,
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

    /** Writes a group. [id] null makes a new one, last in order. */
    fun onSaveGroup(
        id: Long?,
        title: String,
        at: LocalTime,
        salience: Salience,
    ) = viewModelScope.launch {
        val loaded = latest ?: return@launch
        val existing = loaded.blocks.firstOrNull { it.id == id }

        saveBlock(
            existing?.copy(title = title.trim(), anchor = Anchor.Fixed(at), salience = salience) ?: Block(
                id = 0,
                templateId = loaded.template.id,
                title = title.trim(),
                anchor = Anchor.Fixed(at),
                salience = salience,
                sortOrder = loaded.blocks.size,
            ),
        )
    }

    /** The steps inside it stay on the plan. Only the grouping goes. */
    fun onDeleteGroup(id: Long) = viewModelScope.launch {
        val loaded = latest ?: return@launch
        deleteBlock(loaded.template.id, id)
    }

    private fun toUiState(contents: PlanContents): PlanUiState = when (contents) {
        PlanContents.None -> PlanUiState.Empty

        is PlanContents.Loaded -> {
            latest = contents
            val titles = contents.items.associate { it.id to it.title }
            val starts = contents.items.mapNotNull { startOf(it.anchor) }

            val rows = contents.items.map { toRow(it, titles, contents.items) }
            val groups = contents.blocks.map { toGroup(it, rows) }

            PlanUiState(
                planId = contents.planId,
                planName = contents.planName,
                templateId = contents.template.id,
                templates = contents.templates
                    .map { TemplateTab(it.id, it.name, it.weekdays, it.isDefault) }
                    .toImmutableList(),
                selectedIndex = contents.templates.indexOfFirst { it.id == contents.template.id }.coerceAtLeast(0),
                rows = rows.toImmutableList(),
                sections = sectionsOf(rows, groups),
                groups = groups.toImmutableList(),
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
        groupId = item.blockId,
        hasNote = !item.detail.isNullOrBlank(),
        measured = item.valueKind != ValueKind.NONE,
    )

    private fun toGroup(block: Block, rows: List<PlanItemRow>) = PlanGroupRow(
        id = block.id,
        title = block.title,
        time = startOf(block.anchor)?.let(clock::format).orEmpty(),
        at = startOf(block.anchor) ?: LocalTime.MIDNIGHT,
        salience = block.salience,
        count = rows.count { it.groupId == block.id },
    )

    /**
     * The list as it is drawn: every group whole, at the position of its first
     * step, and the loose steps left where they are.
     *
     * Gathering a group rather than following plan order exactly is deliberate.
     * A group whose steps are not adjacent would otherwise print its heading
     * twice, and two headings with the same name reads as a bug rather than as
     * a fact about a sort order.
     */
    private fun sectionsOf(rows: List<PlanItemRow>, groups: List<PlanGroupRow>): ImmutableList<PlanSection> {
        val byId = groups.associateBy { it.id }
        val sections = mutableListOf<PlanSection>()
        val placed = mutableSetOf<Long>()

        rows.forEach { row ->
            val group = row.groupId?.let(byId::get)

            when {
                group == null -> sections += PlanSection(null, persistentListOf(row))

                group.id in placed -> Unit

                else -> {
                    placed += group.id
                    sections += PlanSection(group, rows.filter { it.groupId == group.id }.toImmutableList())
                }
            }
        }

        // A group with nothing in it yet still has to be visible, or making
        // one looks like it did nothing at all.
        groups.filterNot { it.id in placed }.forEach { sections += PlanSection(it, persistentListOf()) }

        return sections.toImmutableList()
    }

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
