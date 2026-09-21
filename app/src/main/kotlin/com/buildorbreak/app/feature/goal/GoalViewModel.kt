package com.buildorbreak.app.feature.goal

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.common.time.TimeProvider
import com.buildorbreak.core.domain.goal.GoalSnapshot
import com.buildorbreak.core.domain.goal.GoalStanding
import com.buildorbreak.core.domain.goal.GoalWeek
import com.buildorbreak.core.domain.usecase.ObserveGoalUseCase
import com.buildorbreak.core.domain.usecase.ObservePlanUseCase
import com.buildorbreak.core.domain.usecase.PlanItemChoice
import com.buildorbreak.core.domain.usecase.RetireGoalUseCase
import com.buildorbreak.core.domain.usecase.SaveGoalUseCase
import com.buildorbreak.core.domain.usecase.SetWeekCountedUseCase
import com.buildorbreak.core.model.enums.GoalKind
import com.buildorbreak.core.model.enums.ValueKind
import com.buildorbreak.core.model.goal.Goal
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
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
private const val MAX_WEEKS = 52

/**
 * A step a counting or accumulating goal can be attached to.
 *
 * [templateName] is shown only when the plan has more than one template, and
 * then only because two templates usually carry a step of the same name and
 * they are two different steps.
 */
@Immutable
data class GoalItemChoice(val id: Long, val title: String, val templateName: String = "")

/**
 * Everything a goal is, while it is being written.
 *
 * The end is held as a date and can be set two ways. Weeks come first,
 * because "in two months" is how most goals are thought of. A date is the
 * other way in, for the goals that belong to the calendar rather than to the
 * person: a wedding, a medical check, a trip. Weeks are read off the date,
 * never stored beside it, so the two cannot disagree.
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
    /**
     * No default, deliberately.
     *
     * Every goal starts on a real day, and the only clock this app is allowed
     * to read is the injected one. A placeholder here would be a date nobody
     * chose, quietly waiting to be saved by a path that forgot to set it.
     */
    val startDate: LocalDate,
    val targetDate: LocalDate = startDate.plusDays(DEFAULT_WEEKS * DAYS_PER_WEEK),
    /** Which of the two ways the end is being set. Only changes what the form shows. */
    val byDate: Boolean = false,
    /**
     * The first day the goal may end on: after it starts, and not in the past.
     *
     * Handed in because the form may not read a clock, and because a goal
     * edited into yesterday would finish the moment it was saved.
     */
    val earliestEnd: LocalDate = startDate.plusDays(1),
) {
    /** A year. Past that a goal is a wish, and the pace line is too flat to read. */
    val latestEnd: LocalDate get() = startDate.plusDays(MAX_WEEKS * DAYS_PER_WEEK)

    val days: Int get() = ChronoUnit.DAYS.between(startDate, targetDate).toInt()

    /** Rounded up, so a goal of ten days reads as two weeks rather than one. */
    val weeks: Int get() = ((days + DAYS_PER_WEEK - 1) / DAYS_PER_WEEK).toInt().coerceIn(1, MAX_WEEKS)

    /** The draft, ending this many whole weeks after it starts. Null when that is not allowed. */
    fun endingAfter(weeks: Int): GoalDraft? {
        val end = startDate.plusDays(weeks * DAYS_PER_WEEK)
        return copy(targetDate = end).takeIf { weeks >= 1 && end >= earliestEnd && end <= latestEnd }
    }

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

/**
 * One week of the goal, and whether the goal is reading it.
 *
 * [weeksAgo] is zero for the week today is in. A fact, so the screen can say
 * "this week" in whatever language it is in.
 */
@Immutable
data class GoalWeekUi(val start: LocalDate, val end: LocalDate, val counted: Boolean, val weeksAgo: Int)

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
    /** The target date has passed, or the target was met. Either way it is over. */
    val isFinished: Boolean = false,
    /** True when it was met. Only meaningful once [isFinished]. */
    val reached: Boolean = false,
    /** Whether a finished day exists to carry a rate forward from. */
    val hasProjection: Boolean,
    val trail: ImmutableList<Double>,
    /** Where the goal began, kept so an edit does not restart it from today. */
    val startValue: Double = 0.0,
    val startDate: LocalDate? = null,
    /** The recent weeks that can be left out of the goal, newest first. */
    val weeks: ImmutableList<GoalWeekUi> = persistentListOf(),
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
    private val observePlan: ObservePlanUseCase,
    private val saveGoal: SaveGoalUseCase,
    private val retireGoal: RetireGoalUseCase,
    private val setWeekCounted: SetWeekCountedUseCase,
    private val time: TimeProvider,
) : ViewModel() {

    private val draft = MutableStateFlow<GoalDraft?>(null)
    private val failed = MutableStateFlow(false)

    val state: StateFlow<GoalUiState> =
        combine(observeGoal(), observePlan.allItems(), draft, failed) { snapshot, steps, editing, failure ->
            GoalUiState(
                loaded = true,
                goal = snapshot?.let(::toCard),
                draft = editing,
                items = choicesOf(steps),
                saveFailed = failure,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = GoalUiState.Empty,
        )

    /**
     * Every step on the plan, named so two of them can be told apart.
     *
     * A single template plan is the common case and its steps need no
     * qualifier; the moment there are two, "Gym" on the weekday plan and
     * "Gym" on the weekend plan are two rows that have to read differently.
     */
    private fun choicesOf(steps: List<PlanItemChoice>): ImmutableList<GoalItemChoice> {
        val manyTemplates = steps.map { it.templateName }.distinct().size > 1

        return steps
            .map { GoalItemChoice(it.id, it.title, if (manyTemplates) it.templateName else "") }
            .toImmutableList()
    }

    /** Opens the form on a blank goal, starting today. */
    fun onNew() {
        val today = time.today()
        draft.value = GoalDraft(startDate = today, earliestEnd = today.plusDays(1))
        failed.value = false
    }

    /** Opens the form on the goal that exists, keeping the day it started. */
    fun onEdit() {
        val card = state.value.goal ?: return
        val started = card.startDate ?: time.today().minusDays(card.daysElapsed.toLong())

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
            startDate = started,
            targetDate = started.plusDays(card.totalDays.toLong()),
            // A goal set by date keeps being edited by date. Whole weeks is
            // what the stepper writes, so anything else came from the calendar.
            byDate = card.totalDays % DAYS_PER_WEEK != 0L,
            earliestEnd = maxOf(started, time.today()).plusDays(1),
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

    /** Leaves a week out of the goal, or puts it back. The days themselves are untouched. */
    fun onWeekCounted(weekStart: LocalDate, counted: Boolean) = viewModelScope.launch {
        state.value.goal?.let { setWeekCounted(it.id, weekStart, counted) }
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
        targetDate = from.targetDate,
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
        isFinished = snapshot.isFinished,
        reached = snapshot.standing == GoalStanding.REACHED,
        hasProjection = snapshot.hasProjection,
        trail = snapshot.trail.toImmutableList(),
        startValue = snapshot.goal.startValue,
        startDate = snapshot.goal.startDate,
        weeks = snapshot.weeks.map { toWeek(it, snapshot.on) }.toImmutableList(),
    )

    private fun toWeek(week: GoalWeek, today: LocalDate): GoalWeekUi {
        val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        return GoalWeekUi(
            start = week.start,
            end = week.start.plusDays(DAYS_PER_WEEK - 1),
            counted = week.counted,
            weeksAgo = (ChronoUnit.DAYS.between(week.start, thisMonday) / DAYS_PER_WEEK).toInt(),
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
