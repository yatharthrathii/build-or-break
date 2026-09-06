package com.buildorbreak.app.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buildorbreak.core.common.time.TimeProvider
import com.buildorbreak.core.domain.gateway.AlarmGateway
import com.buildorbreak.core.domain.usecase.CompleteItemUseCase
import com.buildorbreak.core.domain.usecase.ObserveRunUseCase
import com.buildorbreak.core.domain.usecase.ObserveTodayUseCase
import com.buildorbreak.core.domain.usecase.ShiftDayUseCase
import com.buildorbreak.core.domain.usecase.SkipItemUseCase
import com.buildorbreak.core.domain.usecase.SnoozeItemUseCase
import com.buildorbreak.core.model.enums.DeliveryTier
import com.buildorbreak.core.model.enums.OccurrenceState
import com.buildorbreak.core.model.execution.Occurrence
import com.buildorbreak.core.model.plan.Anchor
import com.buildorbreak.core.model.resolved.BudgetWarning
import com.buildorbreak.core.model.resolved.ResolvedDay
import com.buildorbreak.core.model.resolved.ResolvedEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
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
    get() = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

/** How far a snooze from the timeline moves something, matching the notification. */
private val DEFAULT_SNOOZE: Duration = 10.minutes

private const val MILLIS_PER_MINUTE = 60_000L

/**
 * Collects, maps, and calls. No computing.
 *
 * architecture.md hard rule two: a ViewModel never touches Room, DataStore or
 * AlarmManager, and never works anything out. Every number below already exists
 * on `ResolvedDay` or an `Occurrence`; this reads them off and formats them, so
 * that a leaf never needs a locale, a zone or a formatter to draw itself.
 */
@HiltViewModel
class TodayViewModel @Inject constructor(
    observeToday: ObserveTodayUseCase,
    observeRun: ObserveRunUseCase,
    private val complete: CompleteItemUseCase,
    private val snooze: SnoozeItemUseCase,
    private val skip: SkipItemUseCase,
    private val shiftDay: ShiftDayUseCase,
    private val alarms: AlarmGateway,
    private val time: TimeProvider,
) : ViewModel() {

    /**
     * Emits on the minute, so the clock in the header and the "next" row move
     * without a database write. Aligned to the wall clock rather than to when
     * the screen opened, because a clock that says 09:12 until 09:13:40 is a
     * clock that looks broken.
     */
    private val ticks: Flow<LocalDateTime> = flow {
        while (true) {
            emit(time.localNow())
            delay(MILLIS_PER_MINUTE - time.now().toEpochMilli() % MILLIS_PER_MINUTE)
        }
    }

    val state: StateFlow<TodayUiState> = combine(observeToday(), observeRun(), ticks) { day, run, now ->
        day?.let { toUiState(it, run, now) } ?: TodayUiState.Empty
    }.stateIn(
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

    /** Moves the whole day. Zero puts it back. */
    fun onShiftDay(minutes: Int) = viewModelScope.launch { shiftDay(minutes.minutes) }

    // Mapping -----------------------------------------------------------------

    private fun toUiState(day: ResolvedDay, run: Int, now: LocalDateTime): TodayUiState {
        val titles = day.entries.associate { it.item.id to it.item.title }
        val next = day.next(now)

        return TodayUiState(
            header = DayHeader(
                dateLine = day.date.format(DATE),
                templateName = day.template.name,
                clock = now.format(CLOCK),
                doneCount = day.doneCount,
                total = day.total,
            ),
            runDays = run,
            shiftMinutes = day.dayShift.inWholeMinutes.toInt(),
            movedCount = day.entries.count { !it.item.pinned && it.occurrence?.isSettled != true },
            next = next?.let { toNextUp(it, titles) },
            entries = day.entries.map { toEntry(it, titles) }.toImmutableList(),
            nowIndex = next?.let { day.entries.indexOf(it) } ?: -1,
            budget = day.budgetWarning?.let(::toBudgetNotice),
            degradedTier = degradedTier(),
            hasPlan = true,
        )
    }

    private fun toNextUp(entry: ResolvedEntry, titles: Map<Long, String>) = NextUp(
        occurrenceId = entry.occurrence?.id ?: 0,
        itemId = entry.item.id,
        time = entry.at.format(CLOCK),
        title = entry.item.title,
        kind = kindOf(entry, titles),
        note = noteOf(entry),
        durationMinutes = entry.item.duration?.inWholeMinutes?.toInt(),
        hasMinimum = entry.item.hasMinimum,
    )

    private fun toEntry(entry: ResolvedEntry, titles: Map<Long, String>) = TimelineEntry(
        occurrenceId = entry.occurrence?.id ?: 0,
        itemId = entry.item.id,
        time = entry.at.format(CLOCK),
        title = entry.item.title,
        kind = kindOf(entry, titles),
        note = noteOf(entry),
        isDone = entry.occurrence?.isDone == true,
        isMissed = entry.occurrence?.isSettled == true && entry.occurrence?.isDone != true,
    )

    private fun kindOf(entry: ResolvedEntry, titles: Map<Long, String>): EntryKind =
        when (val anchor = entry.item.anchor) {
            is Anchor.Fixed -> EntryKind.Fixed

            is Anchor.Relative -> EntryKind.After(
                parentTitle = titles[anchor.parentItemId].orEmpty(),
                offsetMinutes = anchor.offset.inWholeMinutes.toInt(),
            )

            is Anchor.Window -> EntryKind.Window(from = anchor.from.format(CLOCK), to = anchor.to.format(CLOCK))

            is Anchor.Interval -> EntryKind.Every(anchor.every.inWholeMinutes.toInt())
        }

    /**
     * The fact that matters most, in a fixed order.
     *
     * What happened beats what was planned: a done row says when it was done,
     * not that it was once moved. Below that, a move is more useful than a
     * pin, and a pin more useful than a window's end.
     */
    private fun noteOf(entry: ResolvedEntry): EntryNote? {
        val occurrence = entry.occurrence

        return if (occurrence?.isSettled == true) settledNote(entry, occurrence) else openNote(entry)
    }

    private fun settledNote(entry: ResolvedEntry, occurrence: Occurrence): EntryNote {
        val settledAt = occurrence.settledAt?.let { LocalDateTime.ofInstant(it, time.zone()) }

        return when {
            occurrence.isDone && settledAt != null -> EntryNote.DoneAt(
                time = settledAt.format(CLOCK),
                overMinutes = ChronoUnit.MINUTES.between(entry.at, settledAt).toInt(),
            )

            occurrence.state == OccurrenceState.SKIPPED -> EntryNote.Skipped
            else -> EntryNote.Missed
        }
    }

    private fun openNote(entry: ResolvedEntry): EntryNote? {
        val occurrence = entry.occurrence
        val moved = occurrence?.let { ChronoUnit.MINUTES.between(it.plannedAt, entry.at).toInt() } ?: 0
        val snoozes = occurrence?.snoozeCount ?: 0
        val anchor = entry.item.anchor

        return when {
            entry.degraded -> EntryNote.Degraded
            moved != 0 && !entry.item.pinned -> EntryNote.Moved(moved)
            entry.item.pinned -> EntryNote.Pinned
            snoozes > 0 -> EntryNote.Snoozed(snoozes)
            anchor is Anchor.Window -> EntryNote.Ends(anchor.to.format(CLOCK))
            else -> null
        }
    }

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
