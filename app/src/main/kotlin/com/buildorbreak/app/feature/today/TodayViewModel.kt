package com.buildorbreak.app.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buildorbreak.core.common.time.TimeProvider
import com.buildorbreak.core.domain.gateway.AlarmGateway
import com.buildorbreak.core.domain.usecase.CompleteItemUseCase
import com.buildorbreak.core.domain.usecase.ObserveTodayUseCase
import com.buildorbreak.core.domain.usecase.ShiftDayUseCase
import com.buildorbreak.core.domain.usecase.SkipItemUseCase
import com.buildorbreak.core.domain.usecase.SnoozeItemUseCase
import com.buildorbreak.core.model.enums.DeliveryTier
import com.buildorbreak.core.model.resolved.BudgetWarning
import com.buildorbreak.core.model.resolved.ResolvedDay
import com.buildorbreak.core.model.resolved.ResolvedEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Built per read rather than held in a constant.
 *
 * A formatter that captured the locale once would keep formatting in English
 * after somebody switched the phone to Hindi, and would keep doing it until the
 * process was killed. The allocation is nothing next to resolving a whole day.
 */
private val CLOCK: DateTimeFormatter
    get() = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

private val DATE: DateTimeFormatter
    get() = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())

/** How far a snooze from the timeline moves something, matching the notification. */
private val DEFAULT_SNOOZE: Duration = 10.minutes

/**
 * Collects, maps, and calls. No computing.
 *
 * architecture.md hard rule two: a ViewModel never touches Room, DataStore or
 * AlarmManager, and never works anything out. Every number below already exists
 * on `ResolvedDay`, which is the whole reason the resolver is a pure function in
 * a module this class cannot see the inside of.
 *
 * Formatting is the one thing that does happen here, and it happens here rather
 * than in the composables so that a leaf never needs a locale, a zone or a
 * formatter to draw itself.
 */
@HiltViewModel
class TodayViewModel @Inject constructor(
    observeToday: ObserveTodayUseCase,
    private val complete: CompleteItemUseCase,
    private val snooze: SnoozeItemUseCase,
    private val skip: SkipItemUseCase,
    private val shiftDay: ShiftDayUseCase,
    private val alarms: AlarmGateway,
    private val time: TimeProvider,
) : ViewModel() {

    val state: StateFlow<TodayUiState> = observeToday()
        .map { day -> day?.let(::toUiState) ?: TodayUiState.Empty }
        .stateIn(
            scope = viewModelScope,
            // Kept for five seconds so a rotation does not throw the day away and
            // re resolve it, and dropped after that so a backgrounded app is not
            // holding a database subscription open.
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = TodayUiState.Empty,
        )

    fun onDone(occurrenceId: Long) = viewModelScope.launch { complete(occurrenceId) }

    /** The smaller version counts. Scaling down is not failing. */
    fun onDoneMinimum(occurrenceId: Long) = viewModelScope.launch { complete(occurrenceId, minimum = true) }

    fun onSnooze(occurrenceId: Long) = viewModelScope.launch { snooze(occurrenceId, DEFAULT_SNOOZE) }

    fun onSkip(occurrenceId: Long) = viewModelScope.launch { skip(occurrenceId) }

    fun onShiftDay(by: Duration) = viewModelScope.launch { shiftDay(by) }

    // Mapping -----------------------------------------------------------------

    private fun toUiState(day: ResolvedDay): TodayUiState {
        val entries = day.entries.map(::toEntry)
        val next = day.next(time.localNow())

        return TodayUiState(
            header = headerFor(day),
            entries = entries.toImmutableList(),
            nowIndex = next?.let { day.entries.indexOf(it) } ?: -1,
            budget = day.budgetWarning?.let(::toBudgetNotice),
            degradedTier = degradedTier(),
            hasPlan = true,
        )
    }

    private fun headerFor(day: ResolvedDay) = DayHeader(
        date = day.date.format(DATE),
        subtitle = day.template.name,
        doneCount = day.doneCount,
        total = day.total,
    )

    private fun toEntry(entry: ResolvedEntry) = TimelineEntry(
        occurrenceId = entry.occurrence?.id ?: 0,
        itemId = entry.item.id,
        time = entry.at.format(CLOCK),
        title = entry.item.title,
        detail = entry.item.detail,
        salience = entry.salience,
        isDone = entry.occurrence?.isDone == true,
        isMissed = entry.occurrence?.isSettled == true && entry.occurrence?.isDone != true,
        isPinned = entry.item.pinned,
        isDegraded = entry.degraded,
        hasMinimum = entry.item.hasMinimum,
    )

    private fun toBudgetNotice(warning: BudgetWarning) = BudgetNotice(
        alarms = warning.alarmCount,
        notifications = warning.notifyCount,
        alarmsOverBudget = warning.alarmCount > BudgetWarning.MAX_ALARMS,
    )

    /**
     * The tier, only when it is worth mentioning.
     *
     * Null at the top tier, because a banner that is always on screen is a
     * banner nobody reads. The wording lives in a string resource; this returns
     * the fact and lets the screen say it.
     */
    private fun degradedTier(): DeliveryTier? = alarms.currentTier().takeIf { it != DeliveryTier.FULL_SCREEN_ALARM }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
