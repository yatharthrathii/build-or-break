package com.buildorbreak.app.feature.plan

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buildorbreak.app.feature.today.normaliseLink
import com.buildorbreak.app.format.ClockFormat
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.domain.track.TrackTextParser
import com.buildorbreak.core.domain.usecase.ArchiveItemUseCase
import com.buildorbreak.core.domain.usecase.ObservePlanUseCase
import com.buildorbreak.core.domain.usecase.ObserveReadingsUseCase
import com.buildorbreak.core.domain.usecase.ObserveTodayUseCase
import com.buildorbreak.core.domain.usecase.ObserveTracksUseCase
import com.buildorbreak.core.domain.usecase.PlanContents
import com.buildorbreak.core.domain.usecase.SaveItemUseCase
import com.buildorbreak.core.domain.usecase.SaveTrackUseCase
import com.buildorbreak.core.model.enums.AnchorType
import com.buildorbreak.core.model.enums.ItemKind
import com.buildorbreak.core.model.enums.Salience
import com.buildorbreak.core.model.enums.ValueKind
import com.buildorbreak.core.model.plan.Anchor
import com.buildorbreak.core.model.plan.Item
import com.buildorbreak.core.model.plan.MinimumVersion
import com.buildorbreak.core.model.plan.Weekdays
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalTime
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val DEFAULT_INTERVAL_MINUTES = 45
private const val DEFAULT_OFFSET_MINUTES = 15

/** A month of daily numbers. More columns than that and each one is a hair. */
private const val READINGS_SHOWN = 30

/**
 * Where a new step starts before anybody has said otherwise.
 *
 * Eight in the morning rather than midnight, because a picker opening on 00:00
 * is a picker every user has to change, and an hour of window because most
 * things people give themselves a range for take about that long.
 */
private val DEFAULT_TIME: LocalTime = LocalTime.of(8, 0)
private val DEFAULT_WINDOW_END: LocalTime = LocalTime.of(9, 0)

/**
 * The four anchor shapes, flattened for editing.
 *
 * All four sets of fields are held at once rather than swapped out when the kind
 * changes. Somebody trying a window, going back to a fixed time and trying the
 * window again should find their times still there: losing them would make the
 * editor feel punitive about experimenting, which is exactly what somebody
 * shaping a new routine is doing.
 */
@Immutable
data class AnchorDraft(
    val kind: AnchorType = AnchorType.FIXED,
    val at: LocalTime = DEFAULT_TIME,
    val from: LocalTime = DEFAULT_TIME,
    val to: LocalTime = DEFAULT_WINDOW_END,
    val everyMinutes: Int = DEFAULT_INTERVAL_MINUTES,
    val offsetMinutes: Int = DEFAULT_OFFSET_MINUTES,
    val parentItemId: Long? = null,
)

/** A step this one could hang off. Only other items on the template qualify. */
@Immutable
data class ParentChoice(val id: Long, val title: String)

/** A group this step could belong to, and how loud that group is. */
@Immutable
data class GroupChoice(val id: Long, val title: String, val salience: Salience)

/** A syllabus this step could follow, and where it stands. */
@Immutable
data class TrackChoice(val id: Long, val name: String, val position: Int, val total: Int) {
    val isFinished: Boolean get() = position == 0 && total > 0
}

/** Why the save button is off. Facts; the screen has the words. */
enum class SaveBlocker { NO_TITLE, NO_PARENT, WINDOW_BACKWARDS }

@Immutable
data class ItemEditorUiState(
    val itemId: Long,
    val title: String,
    val anchor: AnchorDraft,
    val durationMinutes: Int?,
    val salience: Salience,
    val weekdays: Weekdays,
    val pinned: Boolean,
    val minimumTitle: String,
    /** Whether a missed step is worth doing later in the day. */
    val catchable: Boolean,
    /**
     * A line of context carried with the step: which shelf, which page, which
     * platform. Shown on the card at the moment the step comes round, which is
     * the only moment it is worth anything.
     */
    val detail: String,
    /** What number this step asks for when it is completed, if any. */
    val valueKind: ValueKind,
    /** The group this step belongs to. Null for the ordinary case. */
    val blockId: Long?,
    val groups: ImmutableList<GroupChoice>,
    val parents: ImmutableList<ParentChoice>,
    val isNew: Boolean,
    /** Where this step lands today, when it is on today's timeline. */
    val landsAtToday: String?,
    /** How many relative steps hang off this one. Worth knowing before a move. */
    val childCount: Int,
    /**
     * The last write did not land.
     *
     * Save used to navigate on success and do nothing at all on failure, so a
     * write that failed looked exactly like a button that had not registered
     * the tap. People press it again, then harder, then decide the app is
     * broken, and they are not wrong.
     */
    val saveFailed: Boolean = false,
    /**
     * The numbers this step has collected, oldest first.
     *
     * Asking for a number and never showing it back is a form, not a
     * tracker. Empty for a new step and for one that asks for nothing.
     */
    val readings: ImmutableList<Double> = persistentListOf(),
    /** A link or a file to open when the step comes round. Empty for none. */
    val link: String = "",
    /** The syllabus this step works through, if it works through one. */
    val trackId: Long? = null,
    val tracks: ImmutableList<TrackChoice> = persistentListOf(),
    /** The plan the step is on, for a syllabus made from inside the editor. */
    val planId: Long = 0,
) {
    /** The group this step is in, when it is in one. */
    val group: GroupChoice? get() = groups.firstOrNull { it.id == blockId }

    val track: TrackChoice? get() = tracks.firstOrNull { it.id == trackId }

    /**
     * Whether the group, rather than this step, decides how loud it is.
     *
     * Only for the steps after the first one in the group. Leaving the
     * salience control live while a group silently overrides it would be a
     * setting that does nothing, which is the thing this app refuses to ship.
     */
    val salienceIsGroups: Boolean get() = group != null

    /**
     * A window that ends before it starts is the one input the editor refuses.
     *
     * Everything else degrades sensibly in the resolver, but a backwards window
     * is a typo rather than a preference, and letting it save would produce a
     * step that silently runs once at the wrong time.
     */
    val saveBlocker: SaveBlocker?
        get() = when {
            title.isBlank() -> SaveBlocker.NO_TITLE
            anchor.kind == AnchorType.RELATIVE && anchor.parentItemId == null -> SaveBlocker.NO_PARENT
            anchor.kind in setOf(AnchorType.WINDOW, AnchorType.INTERVAL) && anchor.to <= anchor.from ->
                SaveBlocker.WINDOW_BACKWARDS

            else -> null
        }

    val canSave: Boolean get() = saveBlocker == null

    companion object {
        val Empty = ItemEditorUiState(
            itemId = 0,
            title = "",
            anchor = AnchorDraft(),
            durationMinutes = null,
            salience = Salience.NOTIFY,
            weekdays = Weekdays.EveryDay,
            pinned = false,
            minimumTitle = "",
            catchable = true,
            detail = "",
            valueKind = ValueKind.NONE,
            blockId = null,
            groups = persistentListOf(),
            parents = persistentListOf(),
            isNew = true,
            landsAtToday = null,
            childCount = 0,
        )
    }
}

/**
 * One step, edited.
 *
 * A single `onChange` rather than a method per field. A form with twelve
 * setters is twelve places to forget a validation rule, and the screen already
 * holds the whole state anyway. Everything that is genuinely a decision, what
 * can be saved and how a draft becomes an `Item`, lives here.
 */
@HiltViewModel
class ItemEditorViewModel @Inject constructor(
    private val observePlan: ObservePlanUseCase,
    private val observeToday: ObserveTodayUseCase,
    private val observeReadings: ObserveReadingsUseCase,
    private val observeTracks: ObserveTracksUseCase,
    private val saveItem: SaveItemUseCase,
    private val saveTrack: SaveTrackUseCase,
    private val archiveItem: ArchiveItemUseCase,
    private val parser: TrackTextParser,
    private val clock: ClockFormat,
) : ViewModel() {

    private val _state = MutableStateFlow(ItemEditorUiState.Empty)
    val state: StateFlow<ItemEditorUiState> = _state.asStateFlow()

    private var templateId: Long = 0
    private var sortOrder: Int = 0

    /** [itemId] of zero means a new step. */
    /**
     * [chosenTemplateId] is the day a new step is being added to. An existing
     * step is edited on its own day, whatever was passed: the editor used to
     * read the default day for both, and a step opened from the Weekend tab
     * was saved back onto the weekday routine and vanished from where it was.
     */
    fun load(itemId: Long, chosenTemplateId: Long = 0) = viewModelScope.launch {
        // The ViewModel outlives the screen. Blank first, so the step edited
        // last time is never on screen for a frame while this one loads.
        _state.value = ItemEditorUiState.Empty

        val existing = if (itemId > 0) observePlan.itemById(itemId) else null
        val wanted = existing?.templateId ?: chosenTemplateId.takeIf { it > 0 }

        val loaded = observePlan(wanted).first() as? PlanContents.Loaded
        val siblings = loaded?.items.orEmpty()

        templateId = existing?.templateId ?: loaded?.template?.id ?: observePlan.defaultTemplateId() ?: 0
        sortOrder = existing?.sortOrder ?: ((siblings.maxOfOrNull { it.sortOrder } ?: -1) + 1)

        val parents = siblings
            // A step cannot hang off itself, and a chain that pointed backwards
            // would be a cycle the graph would then have to cut.
            .filter { it.id != itemId }
            .map { ParentChoice(it.id, it.title) }

        val landsAt = observeToday().first()?.entryFor(itemId)?.at?.let(clock::format)
        val children = siblings.count { (it.anchor as? Anchor.Relative)?.parentItemId == itemId }
        val groups = loaded?.blocks.orEmpty().map { GroupChoice(it.id, it.title, it.salience) }

        val context = EditorContext(
            parents = parents.toImmutableList(),
            groups = groups.toImmutableList(),
            landsAt = landsAt,
            children = children,
            tracks = trackChoices(),
            planId = loaded?.planId ?: 0,
        )

        _state.value = existing?.let { toState(it, context).copy(readings = readingsOf(it)) } ?: newState(context)
    }

    /** Read once. The editor is open for a minute, and nothing is logged from inside it. */
    private suspend fun readingsOf(item: Item): ImmutableList<Double> = if (item.valueKind == ValueKind.NONE) {
        persistentListOf()
    } else {
        observeReadings(item.id).first().map { it.value }.takeLast(READINGS_SHOWN).toImmutableList()
    }

    private suspend fun trackChoices(): ImmutableList<TrackChoice> =
        observeTracks().first().map { TrackChoice(it.track.id, it.track.name, it.position, it.total) }.toImmutableList()

    /** What the editor knows about the rest of the plan, kept out of the row it is editing. */
    private data class EditorContext(
        val parents: ImmutableList<ParentChoice>,
        val groups: ImmutableList<GroupChoice>,
        val landsAt: String?,
        val children: Int,
        val tracks: ImmutableList<TrackChoice>,
        val planId: Long,
    )

    /** How many parts a pasted syllabus would become, for the line under the box. */
    fun partsIn(text: String): Int = parser.parse(text).size

    /**
     * A syllabus made without leaving the step.
     *
     * Written at once rather than held in the draft, because a syllabus is
     * its own thing on the plan: the step merely follows it. Once saved it
     * is picked for this step, which is what somebody who just pasted thirty
     * lines into the editor meant.
     */
    fun onCreateTrack(name: String, text: String) = viewModelScope.launch {
        val current = _state.value

        when (
            val written = saveTrack(
                trackId = null,
                name = name,
                text = text,
                planId = current.planId.takeIf {
                    it > 0
                },
            )
        ) {
            is Outcome.Success -> _state.value = current.copy(trackId = written.value, tracks = trackChoices())
            is Outcome.Failure -> _state.value = current.copy(saveFailed = true)
        }
    }

    /** Any edit clears the failure. The next Save is a fresh attempt, not the old one. */
    fun onChange(state: ItemEditorUiState) {
        _state.value = state.copy(saveFailed = false)
    }

    fun onSave(onDone: () -> Unit) = viewModelScope.launch {
        val current = _state.value
        if (!current.canSave) return@launch

        if (saveItem(toItem(current)) is Outcome.Success) {
            onDone()
        } else {
            _state.value = current.copy(saveFailed = true)
        }
    }

    /** Archived rather than deleted, so past occurrences keep their meaning. */
    fun onArchive(onDone: () -> Unit) = viewModelScope.launch {
        val id = _state.value.itemId
        if (id <= 0) return@launch

        if (archiveItem(id) is Outcome.Success) {
            onDone()
        } else {
            _state.value = _state.value.copy(saveFailed = true)
        }
    }

    // Mapping ------------------------------------------------------------------

    private fun newState(context: EditorContext) = ItemEditorUiState.Empty.copy(
        parents = context.parents,
        groups = context.groups,
        isNew = true,
        tracks = context.tracks,
        planId = context.planId,
    )

    private fun toState(item: Item, context: EditorContext) = ItemEditorUiState(
        itemId = item.id,
        title = item.title,
        anchor = toDraft(item.anchor),
        durationMinutes = item.duration?.inWholeMinutes?.toInt(),
        salience = item.salience,
        weekdays = item.weekdays,
        pinned = item.pinned,
        minimumTitle = item.minimum?.title.orEmpty(),
        catchable = item.catchable,
        detail = item.detail.orEmpty(),
        valueKind = item.valueKind,
        blockId = item.blockId,
        groups = context.groups,
        parents = context.parents,
        isNew = false,
        landsAtToday = context.landsAt,
        childCount = context.children,
        link = item.bundleUri.orEmpty(),
        trackId = item.trackId,
        tracks = context.tracks,
        planId = context.planId,
    )

    /**
     * Reads an anchor into the draft, leaving the other three shapes at their
     * defaults so switching kind lands somewhere sensible rather than on
     * midnight.
     */
    private fun toDraft(anchor: Anchor): AnchorDraft = when (anchor) {
        is Anchor.Fixed -> AnchorDraft(kind = AnchorType.FIXED, at = anchor.at)

        is Anchor.Relative -> AnchorDraft(
            kind = AnchorType.RELATIVE,
            offsetMinutes = anchor.offset.inWholeMinutes.toInt(),
            parentItemId = anchor.parentItemId,
        )

        is Anchor.Window -> AnchorDraft(kind = AnchorType.WINDOW, from = anchor.from, to = anchor.to)

        is Anchor.Interval -> AnchorDraft(
            kind = AnchorType.INTERVAL,
            from = anchor.from,
            to = anchor.to,
            everyMinutes = anchor.every.inWholeMinutes.toInt(),
        )
    }

    private fun toAnchor(draft: AnchorDraft): Anchor = when (draft.kind) {
        AnchorType.FIXED -> Anchor.Fixed(draft.at)

        AnchorType.RELATIVE -> Anchor.Relative(
            parentItemId = draft.parentItemId ?: 0,
            offset = draft.offsetMinutes.minutes,
        )

        AnchorType.WINDOW -> Anchor.Window(from = draft.from, to = draft.to)

        AnchorType.INTERVAL -> Anchor.Interval(
            every = draft.everyMinutes.minutes,
            from = draft.from,
            to = draft.to,
        )
    }

    private fun toItem(state: ItemEditorUiState): Item = toItem(state, state.track?.id)

    private fun toItem(state: ItemEditorUiState, track: Long?) = Item(
        id = state.itemId,
        templateId = templateId,
        // Only a group that still exists. A stale id would put the step in a
        // group nothing answers to, and SQLite is free to hand that row id to
        // the next group created.
        blockId = state.blockId?.takeIf { id -> state.groups.any { it.id == id } },
        kind = if (track == null) ItemKind.DO else ItemKind.TRACK_SESSION,
        title = state.title.trim(),
        detail = state.detail.trim().takeIf { it.isNotEmpty() },
        anchor = toAnchor(state.anchor),
        duration = state.durationMinutes?.takeIf { it > 0 }?.minutes,
        salience = state.salience,
        weekdays = state.weekdays,
        pinned = state.pinned,
        // A blank minimum is no minimum, not a minimum with no name. An empty
        // title would show as a blank button on the notification.
        minimum = state.minimumTitle.trim().takeIf { it.isNotEmpty() }?.let { MinimumVersion(title = it) },
        valueKind = state.valueKind,
        bundleUri = normaliseLink(state.link),
        trackId = track,
        sortOrder = sortOrder,
        archivedAt = null,
        catchable = state.catchable,
    )
}
