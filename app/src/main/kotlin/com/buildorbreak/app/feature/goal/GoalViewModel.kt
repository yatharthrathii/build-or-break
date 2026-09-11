package com.buildorbreak.app.feature.goal

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.common.time.TimeProvider
import com.buildorbreak.core.domain.goal.GoalSnapshot
import com.buildorbreak.core.domain.goal.GoalStanding
import com.buildorbreak.core.domain.usecase.ObserveGoalUseCase
import com.buildorbreak.core.domain.usecase.ObservePlanUseCase
import com.buildorbreak.core.domain.usecase.PlanContents
import com.buildorbreak.core.domain.usecase.RetireGoalUseCase
import com.buildorbreak.core.domain.usecase.SaveGoalUseCase
import com.buildorbreak.core.model.enums.GoalKind
import com.buildorbreak.core.model.enums.ValueKind
import com.buildorbreak.core.model.goal.Goal
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val PERCENT = 100
private const val DAYS_PER_WEEK = 7L

/** Eight weeks. Long enough for a habit to prove itself, short enough to finish. */
private const val DEFAULT_WEEKS = 8

/** A step a counting or accumulating goal can be attached to. */
@Immutable
data class GoalItemChoice(val id: Long, val title: String)

/**
 * Everything a goal is, while it is being written.
 *
 * A length in weeks rather than a target date, because that is how people
 * actually decide: "in two months" is a real thought and "by the 14th of
 * March" is arithmetic somebody has to do first. The date the domain wants is
 * derived from it, once, here.
 */
@Immutable
data class GoalDraft(
    val id: Long = 0,
    val title: String = "",
    val kind: GoalKind = GoalKind.COUNT,
    val itemId: Long? = null,
    val valueKind: ValueKind = ValueKind.WEIGHT_KG,
    val startValue: String = "",
    val targetValue: String = "",
    val weeks: Int = DEFAULT_WEEKS,
    /**
     * No default, deliberately.
     *
     * Every goal starts on a real day, and the only clock this app is allowed
     * to read is the injected one. A placeholder here would be a date nobody
     * chose, quietly waiting to be saved by a path that forgot to set it.
     */
    val startDate: LocalDate,
) {
    val start: Double? get() = startValue.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
    val target: Double? get() = targetValue.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }

    /** A measured goal is the only kind whose starting value has to be given. */
    val needsStart: Boolean get() = kind == GoalKind.NUMBER

    /** Only the two kinds that count a step need to know which step. */
    val needsItem: Boolean get() = kind == GoalKind.COUNT || kind == GoalKind.DURATION

    val blocker: GoalBlocker?
        get() = when {
            title.isBlank() -> GoalBlocker.NO_TITLE
            target == null -> GoalBlocker.NO_TARGET
            needsStart && start == null -> GoalBlocker.NO_START
            needsItem && itemId == null -> GoalBlocker.NO_ITEM
            needsStart && start == target -> GoalBlocker.GOES_NOWHERE
            !needsStart && (target ?: 0.0) <= 0.0 -> GoalBlocker.GOES_NOWHERE
            else -> null
        }

    val canSave: Boolean get() = blocker == null
}

/** Why Save is off. Facts; the screen has the words. */
enum class GoalBlocker { NO_TITLE, NO_TARGET, NO_START, NO_ITEM, GOES_NOWHERE }

/**
 * The goal as it stands, or the form for writing one.
 *
 * Both at once rather than two screens, because a goal is one thing and
 * looking at it is when you decide to change it.
 */
@Immutable
data class GoalUiState(
    val loaded: Boolean,
    val goal: GoalCardUi?,
    val draft: GoalDraft?,
    val items: ImmutableList<GoalItemChoice>,
    val saveFailed: Boolean = false,
) {
    val isEditing: Boolean get() = draft != null

    companion object {
        val Empty = GoalUiState(loaded = false, goal = null, draft = null, items = persistentListOf())
    }
}

/** The goal, formatted. Every number here is read off the snapshot. */
@Immutable
data class GoalCardUi(
    val id: Long,
    val title: String,
    val kind: GoalKind,
    val valueKind: ValueKind,
    /** The step a counting or accumulating goal follows. */
    val itemId: Long?,
    val current: Double,
    val target: Double,
    val paceTarget: Double,
    val projected: Double,
    val percent: Int,
    val pacePercent: Int,
    val standing: GoalStanding,
    val daysLeft: Int,
    val daysElapsed: Int,
    val totalDays: Int,
    val willReach: Boolean,
    val hasData: Boolean,
    /** Whether a finished day exists to carry a rate forward from. */
    val hasProjection: Boolean,
    val trail: ImmutableList<Double>,
    /** Where the goal began, kept so an edit does not restart it from today. */
    val startValue: Double = 0.0,
    val startDate: LocalDate? = null,
)

/**
 * One goal, watched and edited.
 *
 * A goal is optional and the routine works without one, so the empty state is
 * an offer rather than a nag. Nothing on this screen computes: the pace, the
 * projection and the standing all arrive worked out, so the number here and
 * the number on Insights cannot disagree.
 */
@HiltViewModel
class GoalViewModel @Inject constructor(
    observeGoal: ObserveGoalUseCase,
    observePlan: ObservePlanUseCase,
    private val saveGoal: SaveGoalUseCase,
    private val retireGoal: RetireGoalUseCase,
    private val time: TimeProvider,
) : ViewModel() {

    private val draft = MutableStateFlow<GoalDraft?>(null)
    private val failed = MutableStateFlow(false)

    val state: StateFlow<GoalUiState> =
        combine(observeGoal(), observePlan(), draft, failed) { snapshot, plan, editing, failure ->
            GoalUiState(
                loaded = true,
                goal = snapshot?.let(::toCard),
                draft = editing,
                items = (plan as? PlanContents.Loaded)?.items.orEmpty()
                    .map { GoalItemChoice(it.id, it.title) }
                    .toImmutableList(),
                saveFailed = failure,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = GoalUiState.Empty,
        )

    /** Opens the form on a blank goal, starting today. */
    fun onNew() {
        draft.value = GoalDraft(startDate = time.today())
        failed.value = false
    }

    /** Opens the form on the goal that exists, keeping the day it started. */
    fun onEdit() {
        val card = state.value.goal ?: return

        draft.value = GoalDraft(
            id = card.id,
            title = card.title,
            kind = card.kind,
            itemId = card.itemId,
            valueKind = card.valueKind,
            // The goal's own start, not where it is now. Seeding the form with
            // the current level turned a title edit into a restart: the start
            // moved to today's weight, the bar fell to zero, and the weeks of
            // progress behind it were gone.
            startValue = card.startValue.takeIf { card.kind == GoalKind.NUMBER }?.toString().orEmpty(),
            targetValue = card.target.toString(),
            weeks = ((card.totalDays + DAYS_PER_WEEK - 1) / DAYS_PER_WEEK).toInt().coerceAtLeast(1),
            startDate = card.startDate ?: time.today().minusDays(card.daysElapsed.toLong()),
        )
        failed.value = false
    }

    fun onChange(next: GoalDraft) {
        draft.value = next
        failed.value = false
    }

    fun onCancel() {
        draft.value = null
    }

    fun onSave() = viewModelScope.launch {
        val current = draft.value ?: return@launch
        if (!current.canSave) return@launch

        val written = saveGoal(toGoal(current)) is Outcome.Success

        if (written) draft.value = null
        failed.value = !written
    }

    /** Its history stays, because it happened. */
    fun onRetire() = viewModelScope.launch {
        state.value.goal?.let { retireGoal(it.id) }
    }

    private fun toGoal(from: GoalDraft) = Goal(
        id = from.id,
        // Filled in by the use case from the active plan when it is zero.
        planId = 0,
        kind = from.kind,
        title = from.title.trim(),
        itemId = from.itemId.takeIf { from.needsItem },
        valueKind = if (from.kind == GoalKind.NUMBER) from.valueKind else ValueKind.NONE,
        startValue = if (from.needsStart) from.start ?: 0.0 else 0.0,
        targetValue = from.target ?: 0.0,
        startDate = from.startDate,
        targetDate = from.startDate.plusDays(from.weeks * DAYS_PER_WEEK),
        isActive = true,
    )

    private fun toCard(snapshot: GoalSnapshot) = GoalCardUi(
        id = snapshot.goal.id,
        title = snapshot.goal.title,
        kind = snapshot.goal.kind,
        valueKind = snapshot.goal.valueKind,
        itemId = snapshot.goal.itemId,
        current = snapshot.current,
        target = snapshot.goal.targetValue,
        paceTarget = snapshot.paceTarget,
        projected = snapshot.projected,
        percent = (snapshot.percent * PERCENT).toInt(),
        pacePercent = (snapshot.paceFraction * PERCENT).toInt(),
        standing = snapshot.standing,
        daysLeft = snapshot.daysLeft,
        daysElapsed = snapshot.daysElapsed,
        totalDays = snapshot.totalDays,
        willReach = snapshot.willReach,
        hasData = snapshot.hasData,
        hasProjection = snapshot.hasProjection,
        trail = snapshot.trail.toImmutableList(),
        startValue = snapshot.goal.startValue,
        startDate = snapshot.goal.startDate,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
