package com.buildorbreak.app.feature.today

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buildorbreak.app.R
import com.buildorbreak.app.feature.settings.tierShortText
import com.buildorbreak.app.format.rememberClockFormat
import com.buildorbreak.core.designsystem.component.AccentMark
import com.buildorbreak.core.designsystem.component.BlockButton
import com.buildorbreak.core.designsystem.component.EmptyState
import com.buildorbreak.core.designsystem.component.NoticeBar
import com.buildorbreak.core.designsystem.component.OutlineButton
import com.buildorbreak.core.designsystem.component.RowState
import com.buildorbreak.core.designsystem.component.ScreenHeader
import com.buildorbreak.core.designsystem.component.SectionLabel
import com.buildorbreak.core.designsystem.component.TimelineRow
import com.buildorbreak.core.designsystem.theme.BuildOrBreakTheme
import com.buildorbreak.core.designsystem.theme.TimeStyle
import com.buildorbreak.core.model.enums.DeliveryTier
import com.buildorbreak.core.model.enums.SkipChip
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay

/**
 * The day, as it stands.
 *
 * The screen is a ring, a card and a list, and that restraint is the design.
 * Anything added here competes with the one thing somebody opened the app to
 * see, which is what happens next.
 */
@Composable
fun TodayScreen(
    onOpenReliability: () -> Unit,
    onOpenPlan: () -> Unit,
    onImport: () -> Unit,
    onAddStep: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    TodayContent(
        state = state,
        actions = TodayActions(
            onDone = viewModel::onDone,
            onDoneMinimum = viewModel::onDoneMinimum,
            onSnooze = viewModel::onSnooze,
            onSkip = viewModel::onSkip,
            onUndo = viewModel::onUndo,
            onExplainSkip = viewModel::onExplainSkip,
            onWaveAwayAsk = viewModel::onWaveAway,
            onShiftDay = viewModel::onShiftDay,
            onRunTemplate = viewModel::onRunTemplate,
            onSickDay = viewModel::onSickDay,
            onNormalDay = viewModel::onNormalDay,
            onOpenReliability = onOpenReliability,
            onOpenPlan = onOpenPlan,
            onImport = onImport,
            onAddStep = onAddStep,
        ),
        modifier = modifier,
    )
}

/** Everything the screen can do, in one bag, so the leaves take one parameter. */
data class TodayActions(
    val onDone: (Long) -> Unit,
    val onDoneMinimum: (Long) -> Unit,
    val onSnooze: (Long) -> Unit,
    val onSkip: (Long, SkipChip?) -> Unit,
    val onUndo: () -> Unit,
    val onExplainSkip: (Long, SkipChip?) -> Unit,
    val onWaveAwayAsk: (Long) -> Unit,
    val onShiftDay: (Int) -> Unit,
    val onRunTemplate: (Long) -> Unit,
    val onSickDay: () -> Unit,
    val onNormalDay: () -> Unit,
    val onOpenReliability: () -> Unit,
    val onOpenPlan: () -> Unit,
    val onImport: () -> Unit,
    val onAddStep: () -> Unit,
) {
    companion object {
        val None = TodayActions(
            onDone = {},
            onDoneMinimum = {},
            onSnooze = {},
            onSkip = { _, _ -> },
            onUndo = {},
            onExplainSkip = { _, _ -> },
            onWaveAwayAsk = {},
            onShiftDay = {},
            onRunTemplate = {},
            onSickDay = {},
            onNormalDay = {},
            onOpenReliability = {},
            onOpenPlan = {},
            onImport = {},
            onAddStep = {},
        )
    }
}

/**
 * The screen without the ViewModel, so it can be previewed and screenshot
 * tested against a fixed state rather than a live database.
 */
@Composable
fun TodayContent(state: TodayUiState, actions: TodayActions, modifier: Modifier = Modifier) {
    var choosingShift by rememberSaveable { mutableStateOf(false) }
    var skipping by rememberSaveable { mutableStateOf(NO_OCCURRENCE) }
    var explaining by rememberSaveable { mutableStateOf(NO_OCCURRENCE) }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Header(state)

            when {
                // Nothing, rather than "no plan yet". One is a fact and the other
                // is a guess made before the database has answered.
                state.isLoading -> Unit
                !state.hasPlan -> NoPlan(actions = actions)
                state.isEmptyDay -> EmptyDay(startsTomorrow = state.startsTomorrow, onAddStep = actions.onAddStep)
                else -> Day(
                    state = state,
                    actions = actions,
                    onRunningLate = { choosingShift = true },
                    onSkip = { skipping = it },
                    onAnswerAsk = { explaining = it },
                )
            }
        }

        // Over the list rather than above it. A bar that pushed the timeline
        // down on every completion would move the next row out from under the
        // finger already on its way to tap it.
        UndoBar(offer = state.undo, onUndo = actions.onUndo, modifier = Modifier.align(Alignment.BottomCenter))
    }

    Sheets(
        state = state,
        actions = actions,
        choosingShift = choosingShift,
        skipping = skipping,
        explaining = explaining,
        onClose = {
            choosingShift = false
            skipping = NO_OCCURRENCE
            explaining = NO_OCCURRENCE
        },
    )
}

/** Whichever sheet is open, if any is. Never two at once. */
@Composable
private fun Sheets(
    state: TodayUiState,
    actions: TodayActions,
    choosingShift: Boolean,
    skipping: Long,
    explaining: Long,
    onClose: () -> Unit,
) {
    if (choosingShift) {
        DifferentDaySheet(state = state, actions = actions, onClose = onClose)
    }

    if (skipping != NO_OCCURRENCE) {
        SkipSheet(
            title = state.entries.firstOrNull { it.occurrenceId == skipping }?.title.orEmpty(),
            mode = if (state.next?.occurrenceId == skipping && state.next?.hasArrived == false) {
                SkipAskMode.AHEAD
            } else {
                SkipAskMode.HAPPENED
            },
            onSkip = { chip ->
                onClose()
                actions.onSkip(skipping, chip)
            },
            onDismiss = onClose,
        )
    }

    // The step is already settled here. Only the reason is still open, and
    // dismissing the sheet leaves the question to be asked another time.
    if (explaining != NO_OCCURRENCE) {
        SkipSheet(
            title = state.askAbout?.title.orEmpty(),
            mode = SkipAskMode.AFTER_THE_FACT,
            onSkip = { chip ->
                onClose()
                actions.onExplainSkip(explaining, chip)
            },
            onDismiss = onClose,
        )
    }
}

/**
 * The date, the template and the clock.
 *
 * The kicker is blank rather than half filled while the day is being read: it
 * joins a date and a template name with a dot, and drawing it before either
 * exists leaves a lone dot on screen, which reads as broken rather than as
 * loading.
 */
@Composable
private fun Header(state: TodayUiState) {
    ScreenHeader(
        kicker = if (state.isLoading) {
            ""
        } else {
            stringResource(R.string.today_kicker, state.header.dateLine, state.header.templateName)
        },
        title = stringResource(R.string.today_title),
    ) {
        Text(text = state.header.clock, style = TimeStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AccentMark()
    }
}

/** The sheet, with every pick closing it before it acts. */
@Composable
private fun DifferentDaySheet(state: TodayUiState, actions: TodayActions, onClose: () -> Unit) {
    RunningLateSheet(
        state = state,
        onPick = { minutes ->
            onClose()
            actions.onShiftDay(minutes)
        },
        onRunTemplate = {
            onClose()
            actions.onRunTemplate(it)
        },
        onSickDay = {
            onClose()
            actions.onSickDay()
        },
        onDismiss = onClose,
    )
}

/** No skip sheet open. */
private const val NO_OCCURRENCE = 0L

/** How long a timeline row takes to fade as it arrives or leaves. */
private const val ROW_FADE_MILLIS = 220

/** How far apart the rows arrive, and how far they rise on the way in. */
private const val STAGGER_STEP_MILLIS = 45L
private const val STAGGER_LIMIT = 8
private val ROW_RISE = 20.dp

@Composable
private fun Day(
    state: TodayUiState,
    actions: TodayActions,
    onRunningLate: () -> Unit,
    onSkip: (Long) -> Unit,
    onAnswerAsk: (Long) -> Unit,
) {
    val haptics = LocalHapticFeedback.current

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { RingRow(header = state.header, runDays = state.runDays) }

        item {
            ShiftBar(
                state = state,
                onRunningLate = onRunningLate,
                onReset = { actions.onShiftDay(0) },
                onNormalDay = actions.onNormalDay,
            )
        }

        notices(state = state, onOpenReliability = actions.onOpenReliability)

        state.askAbout?.let { ask ->
            item(key = "ask:${ask.occurrenceId}") {
                SkipAskBar(
                    ask = ask,
                    onAnswer = { onAnswerAsk(ask.occurrenceId) },
                    onWaveAway = { actions.onWaveAwayAsk(ask.occurrenceId) },
                )
            }
        }

        nextUp(state = state, actions = actions, onSkip = onSkip, onDone = {
            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            actions.onDone(it)
        })

        item { SectionLabel(text = stringResource(R.string.today_the_day)) }

        timeline(state)

        // Room for the undo bar, so the last row of the day can still be read
        // while it is on screen.
        item { Spacer(Modifier.height(96.dp)) }
    }
}

/**
 * The day itself.
 *
 * Keyed by item and repeat, never by time: a step whose time moves because the
 * one above it was done must keep its row rather than be torn down and rebuilt
 * halfway through the animation that is meant to show it moving.
 */
private fun LazyListScope.timeline(state: TodayUiState) {
    itemsIndexed(items = state.entries, key = { _, entry -> "${entry.itemId}:${entry.sequence}" }) { index, entry ->
        Arriving(index = index) {
            TimelineRow(
                time = entry.time,
                title = entry.title,
                state = rowState(entry, isNext = index == state.nowIndex),
                badge = kindText(entry.kind),
                note = entry.note?.let { noteText(it) },
                noteAccent = entry.note is EntryNote.Moved,
                last = index == state.entries.lastIndex,
                wideTime = !rememberClockFormat().is24Hour,
                alarm = entry.isAlarm,
                // A step being done re times everything below it, which moves
                // rows up the list. Animating that move is what makes it read
                // as the day adjusting rather than as the screen redrawing.
                modifier = Modifier
                    .animateItem(
                        fadeInSpec = tween(ROW_FADE_MILLIS),
                        placementSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        fadeOutSpec = tween(ROW_FADE_MILLIS),
                    )
                    .padding(horizontal = 16.dp),
            )
        }
    }
}

/**
 * The day dealing itself out, one row after another.
 *
 * Each row comes up from below a beat after the one above it. The stagger is
 * the point: a list that appears all at once is a list that was already there,
 * and a routine that assembles itself reads as a day being laid out rather
 * than a screen being drawn.
 *
 * Once only, and remembered across a rotation, so re timing the day later
 * animates through `animateItem` rather than replaying this.
 */
@Composable
private fun Arriving(index: Int, content: @Composable () -> Unit) {
    var shown by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(index.coerceAtMost(STAGGER_LIMIT) * STAGGER_STEP_MILLIS)
        shown = true
    }

    val lift by animateDpAsState(
        targetValue = if (shown) 0.dp else ROW_RISE,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "lift",
    )
    val fade by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(ROW_FADE_MILLIS),
        label = "fade",
    )

    // The lambda overload, so a value that changes every frame relayouts
    // rather than recomposing the subtree behind it.
    Column(modifier = Modifier.offset { IntOffset(x = 0, y = lift.roundToPx()) }.alpha(fade)) { content() }
}

private fun LazyListScope.nextUp(
    state: TodayUiState,
    actions: TodayActions,
    onSkip: (Long) -> Unit,
    onDone: (Long) -> Unit,
) {
    item {
        NextUpCard(
            next = state.next,
            allDone = state.isAllDone,
            keptNothing = state.keptNothing,
            onDone = onDone,
            onDoneMinimum = actions.onDoneMinimum,
            onSnooze = actions.onSnooze,
            onSkip = onSkip,
        )
    }
}

/** The one or two lines about delivery, only when there is something to say. */
private fun LazyListScope.notices(state: TodayUiState, onOpenReliability: () -> Unit) {
    if (state.actionFailed) {
        item(key = "failed") { NoticeBar(text = stringResource(R.string.today_action_failed)) }
    }

    state.degradedTier?.let { tier ->
        item {
            NoticeBar(text = stringResource(tierShortText(tier)), tinted = false) {
                OutlineButton(text = stringResource(R.string.today_notice_fix), onClick = onOpenReliability)
            }
        }
    }

    state.budget?.let { notice -> item { NoticeBar(text = budgetText(notice), tinted = false) } }
}

private fun rowState(entry: TimelineEntry, isNext: Boolean): RowState = when {
    entry.isDone -> RowState.DONE
    entry.isMissed -> RowState.MISSED
    isNext -> RowState.NEXT
    else -> RowState.UPCOMING
}

@Composable
private fun NoPlan(actions: TodayActions) {
    EmptyState(
        title = stringResource(R.string.today_no_plan_title),
        body = stringResource(R.string.today_no_plan_body),
    ) {
        BlockButton(text = stringResource(R.string.today_paste), onClick = actions.onImport)
        OutlineButton(text = stringResource(R.string.today_write), onClick = actions.onAddStep)
    }
}

@Composable
private fun EmptyDay(startsTomorrow: Boolean, onAddStep: () -> Unit) {
    // Two different empty days. A plan written this evening has not lost the
    // morning it never saw, and saying it has no steps on this weekday when it
    // has a full one reads as the app having thrown the routine away.
    if (startsTomorrow) {
        EmptyState(
            title = stringResource(R.string.today_starts_tomorrow_title),
            body = stringResource(R.string.today_starts_tomorrow_body),
        )

        return
    }

    EmptyState(
        title = stringResource(R.string.today_empty_title),
        body = stringResource(R.string.today_empty_body),
    ) {
        OutlineButton(text = stringResource(R.string.plan_add_step), onClick = onAddStep)
    }
}

/** Whichever limit was actually broken. Naming both would say neither clearly. */
@Composable
private fun budgetText(notice: BudgetNotice): String = if (notice.alarmsOverBudget) {
    pluralStringResource(R.plurals.budget_too_many_alarms, notice.alarms, notice.alarms)
} else {
    pluralStringResource(R.plurals.budget_too_many_notifications, notice.notifications, notice.notifications)
}

// Previews ---------------------------------------------------------------------

@Preview(name = "Today", showBackground = true)
@Composable
private fun TodayPreview() {
    BuildOrBreakTheme { TodayContent(state = previewState(), actions = TodayActions.None) }
}

@Preview(name = "Today dark", showBackground = true)
@Composable
private fun TodayDarkPreview() {
    BuildOrBreakTheme(darkTheme = true) { TodayContent(state = previewState(), actions = TodayActions.None) }
}

@Preview(name = "Today, no plan", showBackground = true)
@Composable
private fun TodayNoPlanPreview() {
    BuildOrBreakTheme { TodayContent(state = TodayUiState.Empty, actions = TodayActions.None) }
}

// Fixture data for previews and screen tests. Literal on purpose: a preview
// built from named constants is a preview nobody can read at a glance.
@Suppress("MagicNumber", "LongMethod")
internal fun previewState(): TodayUiState {
    val entries = listOf(
        TimelineEntry(
            1,
            1,
            "07:30",
            "Gym",
            EntryKind.Fixed,
            EntryNote.DoneAt("09:05", 20),
            isDone = true,
            isMissed = false,
        ),
        TimelineEntry(
            2,
            2,
            "09:20",
            "Protein + shower",
            EntryKind.After("Gym", 15),
            EntryNote.DoneAt("09:20", 0),
            true,
            false,
        ),
        TimelineEntry(
            3,
            3,
            "09:50",
            "Deep work block 1",
            EntryKind.Window("09:30", "10:20"),
            EntryNote.Moved(20),
            false,
            false,
        ),
        TimelineEntry(4, 4, "12:30", "Lunch + walk", EntryKind.Fixed, EntryNote.Pinned, false, false),
        TimelineEntry(
            5,
            5,
            "18:00",
            "Language drill",
            EntryKind.Window("18:00", "20:00"),
            EntryNote.Ends("20:00"),
            false,
            false,
        ),
    )

    return TodayUiState(
        header = DayHeader(dateLine = "Mon 7 Sep", templateName = "Weekday", clock = "09:12", doneCount = 4, total = 9),
        runDays = 3,
        shiftMinutes = 20,
        movedCount = 5,
        next = NextUp(
            3,
            3,
            "09:50",
            "Deep work block 1",
            EntryKind.Window("09:30", "10:20"),
            EntryNote.Moved(20),
            50,
            true,
        ),
        entries = entries.toImmutableList(),
        nowIndex = 2,
        budget = null,
        degradedTier = DeliveryTier.EXACT_HEADS_UP,
        hasPlan = true,
    )
}
