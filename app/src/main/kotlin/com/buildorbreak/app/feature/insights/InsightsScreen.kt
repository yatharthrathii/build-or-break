package com.buildorbreak.app.feature.insights

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buildorbreak.app.R
import com.buildorbreak.core.designsystem.component.EmptyState
import com.buildorbreak.core.designsystem.component.HairlineRule
import com.buildorbreak.core.designsystem.component.HeavyRule
import com.buildorbreak.core.designsystem.component.Kicker
import com.buildorbreak.core.designsystem.component.Label
import com.buildorbreak.core.designsystem.component.ScreenHeader
import com.buildorbreak.core.designsystem.component.SectionLabel
import com.buildorbreak.core.designsystem.component.SegmentedTabs
import com.buildorbreak.core.designsystem.theme.BuildOrBreakTheme
import com.buildorbreak.core.designsystem.theme.HeroNumberStyle
import com.buildorbreak.core.designsystem.theme.Theme
import com.buildorbreak.core.designsystem.theme.TimeStyle
import com.buildorbreak.core.domain.review.InsightsPeriod
import com.buildorbreak.core.model.enums.ReviewStory
import com.buildorbreak.core.model.review.ReviewAnswer
import java.time.LocalDate
import java.util.Locale
import kotlinx.collections.immutable.persistentListOf

private val ChartHeight = 112.dp
private val KeptColumn = 44.dp
private val SlipColumn = 56.dp

/** A bar is never invisible. A day with one miss out of one still has a mark. */
private const val MIN_BAR = 0.02f

/**
 * One period, one number, one change worth making.
 *
 * The big number is the fraction of steps kept, the bars show which days held,
 * and the table says which steps did not. Below all of it, a single
 * suggestion drawn from the weekly review, offered once. A dashboard with
 * eleven charts is a dashboard nobody opens twice.
 */
@Composable
fun InsightsScreen(
    onEditItem: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InsightsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    InsightsContent(
        state = state,
        onPeriod = viewModel::onPeriod,
        onApply = onEditItem,
        onDismiss = viewModel::onDismissSuggestion,
        modifier = modifier,
    )
}

@Composable
fun InsightsContent(
    state: InsightsUiState,
    onPeriod: (InsightsPeriod) -> Unit,
    onApply: (Long) -> Unit,
    onDismiss: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding(),
    ) {
        ScreenHeader(kicker = kickerText(state), title = stringResource(R.string.insights_title)) {
            SegmentedTabs(
                options = listOf(stringResource(R.string.insights_week), stringResource(R.string.insights_month)),
                selectedIndex = InsightsPeriod.entries.indexOf(state.period),
                onSelect = { onPeriod(InsightsPeriod.entries[it]) },
            )
        }

        when {
            !state.hasPlan -> EmptyState(
                title = stringResource(R.string.insights_no_plan_title),
                body = stringResource(R.string.insights_no_plan_body),
            )

            state.isEmpty -> EmptyState(
                title = stringResource(R.string.insights_empty_title),
                body = stringResource(R.string.insights_empty_body),
            )

            else -> Body(state = state, onApply = onApply, onDismiss = onDismiss)
        }
    }
}

@Composable
private fun kickerText(state: InsightsUiState): String = when {
    !state.hasPlan -> ""
    state.period == InsightsPeriod.WEEK -> stringResource(R.string.insights_kicker_week, state.weekNumber, state.range)
    else -> stringResource(R.string.insights_kicker_month, state.range)
}

@Composable
private fun Body(state: InsightsUiState, onApply: (Long) -> Unit, onDismiss: (LocalDate) -> Unit) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        HeroRow(state = state)
        Chart(state = state)

        SectionLabel(text = stringResource(R.string.insights_step_by_step))
        StepTable(steps = state.steps)

        state.suggestion?.let { suggestion ->
            SuggestionPanel(
                suggestion = suggestion,
                onApply = { onApply(suggestion.itemId) },
                onDismiss = { onDismiss(suggestion.weekStart) },
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** 78%, and the three lines that qualify it. */
@Composable
private fun HeroRow(state: InsightsUiState) {
    Column {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 16.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.insights_percent, state.percent),
                    style = HeroNumberStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Label(text = stringResource(R.string.insights_steps_kept), modifier = Modifier.padding(top = 6.dp))
            }

            HeroDetails(state = state)
        }

        HeavyRule()
    }
}

@Composable
private fun HeroDetails(state: InsightsUiState) {
    Column(modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)) {
        Text(
            text = pluralStringResource(R.plurals.insights_kept_of, state.total, state.kept, state.total),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Text(
            text = changeText(state),
            style = MaterialTheme.typography.bodyMedium,
            color = if ((state.changePoints ?: 0) < 0) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            },
            modifier = Modifier.padding(top = 3.dp),
        )

        Text(
            text = state.averageSlipMinutes?.let { stringResource(R.string.insights_avg_slip, it) }
                ?: stringResource(R.string.insights_no_slip),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
private fun changeText(state: InsightsUiState): String {
    val points = state.changePoints ?: return stringResource(R.string.insights_change_none)
    val period = stringResource(
        if (state.period == InsightsPeriod.WEEK) R.string.insights_period_week else R.string.insights_period_month,
    )

    return when {
        points > 0 -> stringResource(R.string.insights_change_up, points, period)
        points < 0 -> stringResource(R.string.insights_change_down, -points, period)
        else -> stringResource(R.string.insights_change_flat, period)
    }
}

/** One bar per day or week. The best one is accent; weekends are faint. */
@Composable
private fun Chart(state: InsightsUiState) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
        Label(
            text = stringResource(
                if (state.period == InsightsPeriod.WEEK) {
                    R.string.insights_kept_per_day
                } else {
                    R.string.insights_kept_per_week
                },
            ),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
                .height(ChartHeight),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            state.bars.forEach { bar -> Bar(bar) }
        }

        Text(
            text = stringResource(storyText(state.story)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
    }

    HairlineRule()
}

@Composable
private fun RowScope.Bar(bar: BarUi) {
    val height by animateFloatAsState(bar.fraction ?: 0f, spring(stiffness = 120f), label = "bar")
    val colour = when {
        bar.isBest -> MaterialTheme.colorScheme.primary
        bar.isWeekend -> Theme.colours.faint
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.BottomCenter) {
            BarFill(empty = bar.fraction == null, height = height, colour = colour)
        }

        Text(
            text = bar.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (bar.isBest) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** The column itself, or a hairline where there is nothing to draw yet. */
@Composable
private fun BarFill(empty: Boolean, height: Float, colour: androidx.compose.ui.graphics.Color) {
    if (empty) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Theme.spacing.rule)
                .background(Theme.colours.rail),
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(height.coerceAtLeast(MIN_BAR))
                .background(colour),
        )
    }
}

@Composable
private fun StepTable(steps: List<StepRowUi>) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(modifier = Modifier.padding(vertical = 9.dp)) {
            Kicker(text = stringResource(R.string.insights_col_step), modifier = Modifier.weight(1f))
            Kicker(
                text = stringResource(R.string.insights_col_kept),
                modifier = Modifier.width(KeptColumn),
            )
            Kicker(
                text = stringResource(R.string.insights_col_slip),
                modifier = Modifier.width(SlipColumn),
            )
        }

        HeavyRule()

        steps.forEachIndexed { index, row ->
            StepRow(row = row)
            if (index < steps.lastIndex) HairlineRule()
        }
    }
}

@Composable
private fun StepRow(row: StepRowUi) {
    val ink = if (row.isProblem) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (row.isProblem) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = row.title, style = MaterialTheme.typography.titleSmall, color = ink, modifier = Modifier.weight(1f))

        Text(
            text = stringResource(R.string.insights_kept_cell, row.kept, row.outOf),
            style = TimeStyle,
            color = ink,
            modifier = Modifier.width(KeptColumn),
        )

        Text(
            text = slipText(row.slipMinutes),
            style = TimeStyle,
            color = if (row.isProblem) ink else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(SlipColumn),
        )
    }
}

@Composable
private fun slipText(minutes: Int?): String = when {
    minutes == null || minutes == 0 -> stringResource(R.string.insights_slip_none)
    minutes > 0 -> stringResource(R.string.insights_slip_late, minutes)
    else -> stringResource(R.string.insights_slip_early, -minutes)
}

/** The one change worth making, on a block of accent. */
@Composable
private fun SuggestionPanel(suggestion: SuggestionUi, onApply: () -> Unit, onDismiss: () -> Unit) {
    val ground = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onPrimary

    Column(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
            .fillMaxWidth()
            .background(ground),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Kicker(text = stringResource(R.string.insights_one_change), color = ink)

            Text(
                text = stringResource(answerTitle(suggestion.answer), suggestion.title),
                style = MaterialTheme.typography.headlineSmall,
                color = ink,
                modifier = Modifier.padding(top = 9.dp),
            )

            Text(
                text = suggestionBody(suggestion),
                style = MaterialTheme.typography.bodySmall,
                color = ink,
                modifier = Modifier.padding(top = 7.dp),
            )
        }

        Box(modifier = Modifier.fillMaxWidth().height(Theme.spacing.rule).background(ink))

        SuggestionActions(ink = ink, onApply = onApply, onDismiss = onDismiss)
    }
}

@Composable
private fun SuggestionActions(ink: androidx.compose.ui.graphics.Color, onApply: () -> Unit, onDismiss: () -> Unit) {
    Row {
        Text(
            text = stringResource(R.string.insights_apply).uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelMedium,
            color = ink,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onApply)
                .padding(start = 14.dp, top = 13.dp, bottom = 13.dp),
        )

        Box(modifier = Modifier.width(Theme.spacing.rule).height(40.dp).background(ink))

        Text(
            text = stringResource(R.string.insights_not_this_week).uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelMedium,
            color = ink,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onDismiss)
                .padding(start = 14.dp, top = 13.dp, bottom = 13.dp),
        )
    }
}

@Composable
private fun suggestionBody(suggestion: SuggestionUi): String {
    val slip = suggestion.slipMinutes
    return if (slip != null && slip > 0) {
        pluralStringResource(
            R.plurals.insights_suggestion_slipped,
            suggestion.outOf,
            slip,
            suggestion.misses,
            suggestion.outOf,
        )
    } else {
        pluralStringResource(
            R.plurals.insights_suggestion_missed,
            suggestion.misses,
            suggestion.misses,
            suggestion.outOf,
        )
    }
}

// Copy lookups -----------------------------------------------------------------

private fun answerTitle(answer: ReviewAnswer): Int = when (answer) {
    ReviewAnswer.MOVE_TIME -> R.string.suggestion_move_time
    ReviewAnswer.WIDEN_WINDOW -> R.string.suggestion_widen_window
    ReviewAnswer.RAISE_SALIENCE -> R.string.suggestion_raise_salience
    ReviewAnswer.USE_MINIMUM -> R.string.suggestion_use_minimum
    ReviewAnswer.KEEP_AND_FOCUS -> R.string.suggestion_keep_and_focus
    ReviewAnswer.LEAVE_IT -> R.string.suggestion_leave_it
    ReviewAnswer.REMOVE_ITEM -> R.string.suggestion_remove_item
}

private fun storyText(story: ReviewStory): Int = when (story) {
    ReviewStory.ON_TRACK -> R.string.story_on_track
    ReviewStory.PLAN_TOO_SMALL -> R.string.story_plan_too_small
    ReviewStory.TIMING_PROBLEM -> R.string.story_timing_problem
    ReviewStory.REMINDER_PROBLEM -> R.string.story_reminder_problem
    ReviewStory.LOSING_GRIP -> R.string.story_losing_grip
    ReviewStory.SETTLING_IN -> R.string.story_settling_in
    ReviewStory.MIXED -> R.string.story_mixed
}

// Preview -----------------------------------------------------------------------

@Preview(name = "Insights", showBackground = true)
@Composable
private fun InsightsPreview() {
    BuildOrBreakTheme {
        InsightsContent(state = PreviewState, onPeriod = {}, onApply = {}, onDismiss = {})
    }
}

// Fixture data, literal on purpose so the preview can be read at a glance.
private val PreviewState = InsightsUiState(
    period = InsightsPeriod.WEEK,
    hasPlan = true,
    range = "1 Sep – 7 Sep",
    weekNumber = 36,
    percent = 78,
    kept = 49,
    total = 63,
    changePoints = 11,
    averageSlipMinutes = 12,
    bars = persistentListOf(
        BarUi("M", 0.78f, false, false),
        BarUi("T", 1f, false, true),
        BarUi("W", 0.67f, false, false),
        BarUi("T", 0.89f, false, false),
        BarUi("F", 0.56f, false, false),
        BarUi("S", 0.33f, true, false),
        BarUi("S", null, true, false),
    ),
    steps = persistentListOf(
        StepRowUi(1, "Wake + water", 7, 7, 2, false),
        StepRowUi(2, "Gym", 6, 7, 9, false),
        StepRowUi(3, "Deep work block 2", 3, 7, 41, true),
        StepRowUi(4, "Language drill", 5, 7, null, false),
    ),
    suggestion = SuggestionUi(
        itemId = 3,
        title = "Deep work block 2",
        answer = ReviewAnswer.MOVE_TIME,
        misses = 4,
        outOf = 7,
        slipMinutes = 41,
        weekStart = LocalDate.of(2026, 9, 7),
    ),
    story = ReviewStory.TIMING_PROBLEM,
)
