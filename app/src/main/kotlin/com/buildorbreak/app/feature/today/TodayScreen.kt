package com.buildorbreak.app.feature.today

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buildorbreak.app.R
import com.buildorbreak.core.designsystem.component.EmptyState
import com.buildorbreak.core.designsystem.component.InlineNotice
import com.buildorbreak.core.designsystem.component.Rule
import com.buildorbreak.core.designsystem.component.TimelineRow
import com.buildorbreak.core.designsystem.theme.BuildOrBreakTheme
import com.buildorbreak.core.designsystem.theme.Theme
import com.buildorbreak.core.model.enums.DeliveryTier
import com.buildorbreak.core.model.enums.Salience
import kotlinx.collections.immutable.toImmutableList

/**
 * The day, as it stands.
 *
 * The screen is a list and a header, and that restraint is the design. Anything
 * added here competes with the one thing somebody opened the app to see, which
 * is what happens next.
 */
@Composable
fun TodayScreen(
    onOpenReliability: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    TodayContent(
        state = state,
        onDone = viewModel::onDone,
        onSnooze = viewModel::onSnooze,
        onSkip = viewModel::onSkip,
        onOpenReliability = onOpenReliability,
        modifier = modifier,
    )
}

/**
 * The screen without the ViewModel, so it can be previewed and screenshot
 * tested against a fixed state rather than a live database.
 */
@Composable
fun TodayContent(
    state: TodayUiState,
    onDone: (Long) -> Unit,
    onSnooze: (Long) -> Unit,
    onSkip: (Long) -> Unit,
    onOpenReliability: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { insets ->
        when {
            !state.hasPlan -> NoPlan(Modifier.padding(insets))

            state.isEmptyDay -> EmptyState(
                title = stringResource(R.string.today_empty_title),
                body = stringResource(R.string.today_empty_body),
                modifier = Modifier.padding(insets),
            )

            else -> Timeline(
                state = state,
                onDone = onDone,
                onSnooze = onSnooze,
                onSkip = onSkip,
                onOpenReliability = onOpenReliability,
                modifier = Modifier.padding(insets),
            )
        }
    }
}

@Composable
private fun Timeline(
    state: TodayUiState,
    onDone: (Long) -> Unit,
    onSnooze: (Long) -> Unit,
    onSkip: (Long) -> Unit,
    onOpenReliability: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.tight),
    ) {
        item { Header(state.header) }

        state.degradedTier?.let { tier ->
            item {
                InlineNotice(
                    text = stringResource(tierNoticeText(tier)),
                    actionLabel = stringResource(R.string.today_notice_fix),
                    onAction = onOpenReliability,
                    modifier = Modifier.padding(horizontal = Theme.spacing.medium),
                )
            }
        }

        state.budget?.let { notice ->
            item {
                InlineNotice(
                    text = budgetText(notice),
                    modifier = Modifier.padding(horizontal = Theme.spacing.medium),
                )
            }
        }

        item { Rule(Modifier.padding(vertical = Theme.spacing.small)) }

        // Keyed by item and repeat rather than by index, so an interval item
        // gaining a repeat does not make every row below it recompose.
        items(
            items = state.entries,
            key = { "${it.itemId}:${it.occurrenceId}:${it.time}" },
        ) { entry ->
            EntryRow(
                entry = entry,
                isNext = state.entries.indexOf(entry) == state.nowIndex,
                onDone = onDone,
                onSnooze = onSnooze,
                onSkip = onSkip,
            )
        }
    }
}

/**
 * One row and the actions that belong to it.
 *
 * The entry is passed rather than the whole state. architecture.md section 9:
 * handing a leaf the entire `UiState` makes every row recompose when anything
 * anywhere on the screen changes.
 */
@Composable
private fun EntryRow(
    entry: TimelineEntry,
    isNext: Boolean,
    onDone: (Long) -> Unit,
    onSnooze: (Long) -> Unit,
    onSkip: (Long) -> Unit,
) {
    Column {
        TimelineRow(
            time = entry.time,
            title = entry.title,
            salience = entry.salience,
            detail = entry.detail,
            isDone = entry.isDone,
            isMissed = entry.isMissed,
            isNext = isNext,
            isPinned = entry.isPinned,
            isDegraded = entry.isDegraded,
        )

        // Actions appear on the next thing only. A list where every row carries
        // three buttons is a wall of buttons, and the row that matters is the
        // one the day has actually reached.
        if (isNext && entry.isActionable) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Theme.spacing.section, bottom = Theme.spacing.small),
                horizontalArrangement = Arrangement.spacedBy(Theme.spacing.small),
            ) {
                TextButton(onClick = { onDone(entry.occurrenceId) }) {
                    Text(stringResource(R.string.today_action_done))
                }
                TextButton(onClick = { onSnooze(entry.occurrenceId) }) {
                    Text(stringResource(R.string.today_action_snooze))
                }
                TextButton(onClick = { onSkip(entry.occurrenceId) }) {
                    Text(stringResource(R.string.today_action_skip))
                }
            }
        }
    }
}

@Composable
private fun Header(header: DayHeader) {
    Column(
        modifier = Modifier.padding(
            start = Theme.spacing.medium,
            end = Theme.spacing.medium,
            top = Theme.spacing.large,
            bottom = Theme.spacing.small,
        ),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.tight),
    ) {
        Text(text = header.date, style = MaterialTheme.typography.headlineMedium)

        Text(
            // Counts rather than a percentage. "4 of 11" can be checked against
            // the list below it; a percentage has to be taken on trust.
            text = stringResource(
                R.string.today_subtitle,
                header.subtitle,
                pluralStringResource(
                    R.plurals.today_done_count,
                    header.total,
                    header.doneCount,
                    header.total,
                ),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NoPlan(modifier: Modifier = Modifier) {
    EmptyState(
        title = stringResource(R.string.today_no_plan_title),
        body = stringResource(R.string.today_no_plan_body),
        modifier = modifier,
    )
}

/**
 * The one line the timeline shows about a degraded tier.
 *
 * The full explanation lives on the Reliability screen. A banner that tries to
 * explain Doze batching above somebody's morning is a banner in the way.
 */
@StringRes
private fun tierNoticeText(tier: DeliveryTier): Int = when (tier) {
    DeliveryTier.FULL_SCREEN_ALARM -> R.string.tier_full_screen_body
    DeliveryTier.EXACT_HEADS_UP -> R.string.tier_heads_up_short
    DeliveryTier.INEXACT_NOTIFICATION -> R.string.tier_inexact_short
    DeliveryTier.IN_APP_ONLY -> R.string.tier_in_app_short
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
    BuildOrBreakTheme {
        TodayContent(
            state = previewState(),
            onDone = {},
            onSnooze = {},
            onSkip = {},
            onOpenReliability = {},
        )
    }
}

@Preview(name = "Today dark", showBackground = true)
@Composable
private fun TodayDarkPreview() {
    BuildOrBreakTheme(darkTheme = true) {
        TodayContent(
            state = previewState(),
            onDone = {},
            onSnooze = {},
            onSkip = {},
            onOpenReliability = {},
        )
    }
}

private fun previewState(): TodayUiState {
    val sample = listOf(
        PreviewEntry("06:30", "Wake up", Salience.ALARM, done = true),
        PreviewEntry("06:40", "Drink water", Salience.SILENT, done = true),
        PreviewEntry("07:00", "Medicine", Salience.NOTIFY),
        PreviewEntry("07:30", "Study block", Salience.NOTIFY, detail = "the hard one first"),
        PreviewEntry("11:00", "Stand and stretch", Salience.SILENT),
        PreviewEntry("18:00", "Gym class", Salience.ALARM, pinned = true),
    )

    val entries = sample.mapIndexed { index, preview ->
        val id = index + 1L
        TimelineEntry(
            occurrenceId = id,
            itemId = id,
            time = preview.time,
            title = preview.title,
            detail = preview.detail,
            salience = preview.salience,
            isDone = preview.done,
            isMissed = false,
            isPinned = preview.pinned,
            isDegraded = false,
            hasMinimum = false,
        )
    }

    return TodayUiState(
        header = DayHeader(
            date = "Monday 5 January",
            subtitle = "Weekday",
            doneCount = sample.count { it.done },
            total = sample.size,
        ),
        entries = entries.toImmutableList(),
        nowIndex = sample.indexOfFirst { !it.done },
        budget = null,
        degradedTier = null,
        hasPlan = true,
    )
}

private data class PreviewEntry(
    val time: String,
    val title: String,
    val salience: Salience,
    val detail: String? = null,
    val done: Boolean = false,
    val pinned: Boolean = false,
)
