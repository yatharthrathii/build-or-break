package com.buildorbreak.app.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.common.time.TimeProvider
import com.buildorbreak.core.domain.gateway.AlarmGateway
import com.buildorbreak.core.domain.goal.Prices
import com.buildorbreak.core.domain.usecase.ObservePlanUseCase
import com.buildorbreak.core.domain.usecase.ObserveTodayUseCase
import com.buildorbreak.core.domain.usecase.ObserveUnexplainedSkipsUseCase
import com.buildorbreak.core.domain.usecase.PlanContents
import com.buildorbreak.core.model.enums.DayMode
import com.buildorbreak.core.model.enums.DeliveryTier
import com.buildorbreak.core.model.enums.OccurrenceState
import com.buildorbreak.core.model.enums.SkipChip
import com.buildorbreak.core.model.execution.SkipReason
import com.buildorbreak.core.model.goal.MilestoneAward
import com.buildorbreak.core.model.resolved.ResolvedDay
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How far a snooze from the timeline moves something, matching the notification. */
private val DEFAULT_SNOOZE: Duration = 10.minutes

private const val MILLIS_PER_MINUTE = 60_000L

/**
 * Collects, maps, and calls. No computing.
 *
 * architecture.md hard rule two: a ViewModel never touches Room, DataStore or
 * AlarmManager, and never works anything out. The mapping is `TodayMapper`'s;
 * the actions are use cases; this holds the flows together.
 */
/**
 * The function count rule is suppressed rather than worked around. Today is
 * the screen the whole app exists for, and every function here is one thing
 * a thumb can do to a step: done, smaller, snooze, skip, undo, explain, move,
 * log a number. Splitting them across two ViewModels to satisfy a counter
 * would put half the day's actions somewhere nobody looks for them, and both
 * halves would still need the same state.
 */
@Suppress("TooManyFunctions")
@HiltViewModel
class TodayViewModel @Inject constructor(
    observeToday: ObserveTodayUseCase,
    observePlan: ObservePlanUseCase,
    observeUnexplainedSkips: ObserveUnexplainedSkipsUseCase,
    private val watch: DayWatch,
    private val actions: DayActions,
    private val mapper: TodayMapper,
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), time.localNow())

    /**
     * Which day the screen is showing, which is not decided once.
     *
     * The date used to be read when the ViewModel was built. A phone left on
     * the bedside table through midnight then spent the next morning showing
     * yesterday, with every step already settled and nothing to do, and the
     * only cure was killing the app. Nobody reports that as a bug; they
     * conclude the alarms are unreliable and uninstall.
     */
    private val date: Flow<LocalDate> = ticks.map { it.toLocalDate() }.distinctUntilChanged()

    /**
     * What the user has just done, before the database has said so.
     *
     * A tap on Done settles the row, cancels the alarm, reschedules the rest
     * of the day and refreshes the widget before the day flow re emits. On a
     * mid range phone that is long enough to wonder whether the tap landed.
     * So the tapped occurrence is treated as settled from the moment of the
     * tap, and dropped from here once the database agrees.
     */
    private val pending = MutableStateFlow<Map<Long, OccurrenceState>>(emptyMap())

    /**
     * Everything on this screen that no other screen shares.
     *
     * Held here rather than in the resolved day because none of it is a fact
     * about the routine: a second phone looking at the same database would be
     * right to show none of it.
     */
    private val local = MutableStateFlow(Local())
    private var undoTimer: Job? = null
    private var failureTimer: Job? = null

    /**
     * What is true of the whole day rather than of any one step.
     *
     * Keyed on the date, like the day itself. The run and the thirty day
     * figure used to be read for the date the screen opened on, so a phone
     * left through midnight showed yesterday's numbers all morning.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val facts: Flow<DayFacts> = date.flatMapLatest { on ->
        combine(
            watch.run(on),
            watch.consistency(on),
            watch.goals(),
            watch.points(on),
            watch.wallet(),
        ) { run, score, goals, points, wallet ->
            DayFacts(
                runDays = run,
                consistency = Consistency(score.goodDays, score.consideredDays),
                degradedTier = degradedTier(),
                goals = goals,
                points = points,
                balance = wallet.balance,
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val day: Flow<TodayUiState> =
        combine(
            date.flatMapLatest { observeToday(it) },
            facts,
            ticks,
            pending,
            observePlan(),
        ) { day, dayFacts, now, ahead, plan ->
            day?.let {
                forgetCaughtUp(it, ahead)
                mapper.toUiState(it, now, ahead, plan as? PlanContents.Loaded, dayFacts)
            } ?: TodayUiState.Empty
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val extras: Flow<Extras> =
        combine(local, date.flatMapLatest { observeUnexplainedSkips(it) }, watch.milestone()) { state, skips, earned ->
            // One question at a time, oldest first. Three stacked on the day
            // somebody had a bad morning is an interrogation, not a prompt.
            val ask = skips.firstOrNull { it.occurrenceId !in state.waved }

            Extras(state, ask?.let { SkipAsk(it.occurrenceId, it.title) }, earned)
        }

    init {
        // A new day needs its rows written before anything can be ticked off,
        // and the daily job does not run until five past midnight. Anyone
        // still awake at that hour would otherwise find a day they can look at
        // and cannot touch.
        viewModelScope.launch { date.drop(1).collect { actions.rollOver() } }
    }

    val state: StateFlow<TodayUiState> =
        combine(day, extras) { state, extra ->
            state.copy(
                undo = extra.local.undo,
                undoAsk = extra.local.undoAsk,
                askAbout = extra.ask,
                actionFailed = extra.local.failed,
                askNumber = extra.local.measure,
                milestone = extra.earned?.let { MilestoneNotice(it.milestone) },
            )
        }
            .stateIn(
                scope = viewModelScope,
                // Kept for five seconds so a rotation does not throw the day away
                // and re resolve it, and dropped after that so a backgrounded app
                // is not holding a database subscription open.
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = TodayUiState.Loading,
            )

    /**
     * Completes a step. [minimum] records the smaller version.
     *
     * The smaller version counts, and on a sick day it is what the day asked
     * for, so a plain Done on a reduced step is recorded as one. Scaling down
     * is not failing, and the history has to say so or every figure built on
     * it punishes somebody for having been ill.
     */
    fun onDone(occurrenceId: Long, minimum: Boolean = false) = viewModelScope.launch {
        val reduced = minimum ||
            state.value.entries.firstOrNull { it.occurrenceId == occurrenceId }?.isReduced == true

        expect(occurrenceId, if (reduced) OccurrenceState.DONE_MINIMUM else OccurrenceState.DONE)
        offerUndo(occurrenceId, if (reduced) SettleKind.MINIMUM else SettleKind.DONE)
        askForNumber(occurrenceId)
        report(occurrenceId, actions.complete(occurrenceId, minimum = reduced))
    }

    fun onSnooze(occurrenceId: Long) = viewModelScope.launch {
        report(occurrenceId, actions.snooze(occurrenceId, DEFAULT_SNOOZE))
    }

    /**
     * Moves a missed step to the slot the catch up plan found for it.
     *
     * The same machinery as a snooze, because it is one: the step keeps its
     * identity and its history and simply happens later. Nothing is invented
     * and nothing is quietly written off.
     */
    fun onMoveToSlot(occurrenceId: Long, minutes: Int) = viewModelScope.launch {
        // No row yet means nothing to move. The panel can be drawn from a day
        // whose rows the rescheduling pass has not written, and the next
        // resolve fills them in.
        if (minutes <= 0 || occurrenceId <= 0) return@launch

        report(occurrenceId, actions.snooze(occurrenceId, minutes.minutes))
    }

    /**
     * [chip] is why, when the user chose to say. Always optional.
     *
     * Asking is worth it because the weekly review has nothing to say without
     * it: "you skipped the walk four times" is a fact anybody can see, and "you
     * skipped it four times because work came up" is the one that suggests
     * moving it. Requiring it would be worse than not asking, so the sheet
     * always offers a way past.
     */
    fun onSkip(occurrenceId: Long, chip: SkipChip? = null) = viewModelScope.launch {
        // Answered here and now, so the deferred question must never be put.
        // The flow that finds unexplained skips watches the occurrence table,
        // and it re emitted the moment the row was settled, a beat before the
        // reason had been written: the bar for skips made from a notification
        // came up for a skip made from the sheet, and it stayed up, because
        // writing the reason touches nothing that flow watches.
        local.update { it.copy(waved = it.waved + occurrenceId) }
        expect(occurrenceId, OccurrenceState.SKIPPED)
        offerUndo(occurrenceId, SettleKind.SKIPPED)

        // Written even when nothing was chosen. An empty row is the difference
        // between "declined to say" and "never asked", and the app uses that
        // difference to decide whether to raise it again later.
        val reason = SkipReason(id = 0, occurrenceId = occurrenceId, chip = chip, text = null, createdAt = time.now())

        report(occurrenceId, actions.skip(occurrenceId, reason))
    }

    /**
     * Puts back whatever was last settled.
     *
     * The optimistic entry goes first, so the row returns on the same frame as
     * the tap rather than when the database has finished agreeing.
     */
    /**
     * Offers to put a done step back, for points.
     *
     * Nothing is spent here. The offer says the price and the balance, and
     * the confirm is a second, deliberate tap, because this rewrites a
     * record the day, the run and the goal are all built on.
     */
    fun onAskUndo(occurrenceId: Long) = viewModelScope.launch {
        val entry = state.value.entries.firstOrNull { it.occurrenceId == occurrenceId } ?: return@launch
        if (!entry.isDone) return@launch

        local.update {
            it.copy(
                undoAsk = UndoAsk(
                    occurrenceId = occurrenceId,
                    title = entry.title,
                    cost = Prices.UNDO_STEP,
                    balance = watch.wallet().first().balance,
                ),
            )
        }
    }

    fun onDismissUndoAsk() {
        local.update { it.copy(undoAsk = null) }
    }

    /** Takes the points, then puts the step back. A failed spend undoes nothing. */
    fun onConfirmUndoForPoints() = viewModelScope.launch {
        val ask = local.value.undoAsk ?: return@launch

        local.update { it.copy(undoAsk = null) }
        report(ask.occurrenceId, actions.undoForPoints(ask.occurrenceId))
    }

    fun onUndo() = viewModelScope.launch {
        val offer = local.value.undo ?: return@launch

        // A number owed for a settle that is being taken back is not owed.
        undoTimer?.cancel()
        local.update { it.copy(undo = null, measure = null) }
        pending.update { it - offer.occurrenceId }
        report(offer.occurrenceId, actions.undo(offer.occurrenceId))
    }

    /** The number a completed step asked for. Optional, and closed either way. */
    fun onLogNumber(value: Double) = viewModelScope.launch {
        val prompt = local.value.measure ?: return@launch

        local.update { it.copy(measure = null) }
        watch.logMeasurement(prompt.itemId, prompt.occurrenceId, value)
    }

    /** No number this time. The step stays done; nothing is written. */
    fun onDismissNumber() {
        local.update { it.copy(measure = null) }
    }

    /** Seen. A milestone never comes back, whatever else happens. */
    fun onMilestoneSeen() = viewModelScope.launch {
        state.value.milestone?.let { watch.markMilestoneSeen(it.milestone) }
    }

    /**
     * An answer to the deferred question about a skip made from a notification.
     *
     * The prompt is closed here rather than left to the database. Writing a
     * reason touches the reason table and nothing else, so the flow that
     * watches occurrences has no reason to re emit, and the question sat there
     * having already been answered. Being asked again about something you just
     * answered is how a prompt turns into a nuisance.
     */
    fun onExplainSkip(occurrenceId: Long, chip: SkipChip?) = viewModelScope.launch {
        local.update { it.copy(waved = it.waved + occurrenceId) }
        actions.explainSkip(occurrenceId, chip)
    }

    /** Not now. Asked again on a later day, never again about this one. */
    fun onWaveAway(occurrenceId: Long) {
        local.update { it.copy(waved = it.waved + occurrenceId) }
    }

    /** Moves the whole day. Zero puts it back. */
    fun onShiftDay(minutes: Int) = viewModelScope.launch { actions.shiftDay(minutes.minutes) }

    /** Runs another of the plan's templates today. Resets any shift, as the use case says. */
    fun onRunTemplate(templateId: Long) = viewModelScope.launch {
        actions.switchTemplate(state.value.planId, templateId, DayMode.NORMAL)
    }

    /** A sick day: every step with a smaller version runs as that version. */
    fun onReducedDay(reduced: Boolean) = viewModelScope.launch {
        actions.switchTemplate(
            state.value.planId,
            state.value.currentTemplateId,
            if (reduced) DayMode.REDUCED else DayMode.NORMAL,
        )
    }

    private fun expect(occurrenceId: Long, state: OccurrenceState) {
        if (occurrenceId > 0) pending.update { it + (occurrenceId to state) }
    }

    /**
     * Raises the number question, after the settle rather than before it.
     *
     * Only for the step on the card, which is the only one the screen has the
     * facts for. A Done from the notification shade settles the step exactly
     * as well and simply does not ask, which is the right trade: the settle is
     * the thing that must never be blocked.
     */
    private fun askForNumber(occurrenceId: Long) {
        val card = state.value.next?.takeIf { it.occurrenceId == occurrenceId } ?: return
        val kind = card.measure ?: return

        local.update { it.copy(measure = MeasurePrompt(occurrenceId, card.itemId, card.title, kind)) }
    }

    /**
     * Opens the window in which the last tap can be taken back.
     *
     * Long enough to notice the mistake and short enough not to sit over the
     * timeline. A second settle replaces the first rather than queueing: two
     * undo bars would be two answers to "what does this button take back".
     */
    private fun offerUndo(occurrenceId: Long, kind: SettleKind) {
        val title = state.value.entries.firstOrNull { it.occurrenceId == occurrenceId }?.title ?: return

        undoTimer?.cancel()
        local.update { it.copy(undo = UndoOffer(occurrenceId, title, kind)) }
        undoTimer = viewModelScope.launch {
            delay(UNDO_WINDOW_MILLIS)
            local.update { it.copy(undo = null) }
        }
    }

    /**
     * What to do when the database refuses.
     *
     * The optimistic row is withdrawn first. Leaving it would show a step as
     * done that the app has no record of, and the next reschedule would ring
     * for something the screen says is finished.
     */
    private fun report(occurrenceId: Long, outcome: Outcome<*, *>) {
        if (outcome is Outcome.Success) return

        pending.update { it - occurrenceId }
        undoTimer?.cancel()

        local.update { it.copy(undo = null, failed = true, measure = null) }
        failureTimer?.cancel()
        failureTimer = viewModelScope.launch {
            delay(FAILURE_MILLIS)
            local.update { it.copy(failed = false) }
        }
    }

    /** Everything this screen holds that the database has no opinion about. */
    private data class Local(
        val undo: UndoOffer? = null,
        /** The long press offer, waiting on a second, deliberate tap. */
        val undoAsk: UndoAsk? = null,
        /**
         * Skips this session is finished asking about, whether answered or
         * waved away. Deliberately not persisted: an answer is already in the
         * database, and a "not now" should be allowed to come back tomorrow.
         */
        val waved: Set<Long> = emptySet(),
        val failed: Boolean = false,
        val measure: MeasurePrompt? = null,
    )

    /** The three things the screen shows that the resolved day knows nothing about. */
    private data class Extras(val local: Local, val ask: SkipAsk?, val earned: MilestoneAward?)

    /** Once the database shows a tapped row as settled, the tap has nothing to add. */
    private fun forgetCaughtUp(day: ResolvedDay, ahead: Map<Long, OccurrenceState>) {
        if (ahead.isEmpty()) return

        val caughtUp = day.entries.mapNotNull { it.occurrence }.filter { it.isSettled }.map { it.id }
        if (caughtUp.any { it in ahead }) pending.update { it - caughtUp.toSet() }
    }

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

        /** How long an undo stays on offer. Six seconds is one glance and one reach. */
        const val UNDO_WINDOW_MILLIS = 6_000L

        /** Long enough to be read, short enough not to become furniture. */
        const val FAILURE_MILLIS = 5_000L
    }
}
