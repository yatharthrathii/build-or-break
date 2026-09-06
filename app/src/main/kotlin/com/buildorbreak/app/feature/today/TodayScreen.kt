package com.buildorbreak.app.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buildorbreak.app.R
import com.buildorbreak.app.feature.settings.tierShortText
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
import kotlinx.collections.immutable.toImmutableList

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
            onShiftDay = viewModel::onShiftDay,
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
    val onSkip: (Long) -> Unit,
    val onShiftDay: (Int) -> Unit,
    val onOpenReliability: () -> Unit,
    val onOpenPlan: () -> Unit,
    val onImport: () -> Unit,
    val onAddStep: () -> Unit,
) {
    companion object {
        val None = TodayActions({}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}

/**
 * The screen without the ViewModel, so it can be previewed and screenshot
 * tested against a fixed state rather than a live database.
 */
@Composable
fun TodayContent(state: TodayUiState, actions: TodayActions, modifier: Modifier = Modifier) {
    var choosingShift by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding(),
    ) {
        ScreenHeader(
            kicker = stringResource(R.string.today_kicker, state.header.dateLine, state.header.templateName),
            title = stringResource(R.string.today_title),
        ) {
            Text(text = state.header.clock, style = TimeStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AccentMark()
        }

        when {
            !state.hasPlan -> NoPlan(actions = actions)
            state.isEmptyDay -> EmptyDay(onAddStep = actions.onAddStep)
            else -> Day(state = state, actions = actions, onRunningLate = { choosingShift = true })
        }
    }

    if (choosingShift) {
        RunningLateSheet(
            onPick = { minutes ->
                choosingShift = false
                actions.onShiftDay(minutes)
            },
            onDismiss = { choosingShift = false },
        )
    }
}

@Composable
private fun Day(state: TodayUiState, actions: TodayActions, onRunningLate: () -> Unit) {
    val haptics = LocalHapticFeedback.current

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { RingRow(header = state.header, runDays = state.runDays) }

        item { ShiftBar(state = state, onRunningLate = onRunningLate, onReset = { actions.onShiftDay(0) }) }

        notices(state = state, onOpenReliability = actions.onOpenReliability)

        item {
            NextUpCard(
                next = state.next,
                allDone = state.isAllDone,
                onDone = {
                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    actions.onDone(it)
                },
                onDoneMinimum = actions.onDoneMinimum,
                onSnooze = actions.onSnooze,
                onSkip = actions.onSkip,
            )
        }

        item { SectionLabel(text = stringResource(R.string.today_the_day)) }

        // Keyed by item and occurrence rather than by index, so an interval
        // item gaining a repeat does not make every row below it recompose.
        itemsIndexed(items = state.entries, key = { _, entry ->
            "${entry.itemId}:${entry.occurrenceId}:${entry.time}"
        }) { index, entry ->
            TimelineRow(
                time = entry.time,
                title = entry.title,
                state = rowState(entry, isNext = index == state.nowIndex),
                badge = kindText(entry.kind),
                note = entry.note?.let { noteText(it) },
                noteAccent = entry.note is EntryNote.Moved,
                last = index == state.entries.lastIndex,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** The one or two lines about delivery, only when there is something to say. */
private fun LazyListScope.notices(state: TodayUiState, onOpenReliability: () -> Unit) {
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
private fun EmptyDay(onAddStep: () -> Unit) {
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
