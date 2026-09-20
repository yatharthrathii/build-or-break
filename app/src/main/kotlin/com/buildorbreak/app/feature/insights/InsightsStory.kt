package com.buildorbreak.app.feature.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.buildorbreak.app.R
import com.buildorbreak.core.designsystem.component.HairlineRule
import com.buildorbreak.core.designsystem.component.Kicker
import com.buildorbreak.core.designsystem.component.SectionLabel
import com.buildorbreak.core.designsystem.theme.Theme
import com.buildorbreak.core.designsystem.theme.TimeStyle
import com.buildorbreak.core.domain.goal.GoalStanding
import com.buildorbreak.core.domain.review.SkipCause
import com.buildorbreak.core.model.enums.ReviewStory

/**
 * The week in words, above every chart on the screen.
 *
 * A wall of numbers is read once and skipped forever, so this is the shape of
 * the week in one line, the single thing that went best, and the single thing
 * that did not. Everything below it is the evidence; this is the reading.
 *
 * The win comes first even on a bad week, and that is deliberate rather than
 * kind. A report that opens with what is wrong is a report people stop
 * opening, and a report nobody opens changes nothing at all.
 */
@Composable
internal fun StoryPanel(state: InsightsUiState) {
    SectionLabel(text = stringResource(R.string.insights_reading))

    Column(modifier = Modifier.padding(horizontal = Theme.spacing.medium)) {
        Text(
            text = stringResource(storyHeadline(state.story)),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Text(
            text = stringResource(storyText(state.story)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )

        state.win?.let { WinLine(win = it) }

        state.suggestion?.let { ProblemLine(problem = it) }
    }
}

@Composable
private fun WinLine(win: WinUi) {
    StoryLine(
        kicker = stringResource(R.string.insights_went_best),
        text = if (win.isPerfect) {
            stringResource(R.string.insights_win_perfect, win.title, win.outOf)
        } else {
            pluralStringResource(R.plurals.insights_win, win.outOf, win.title, win.kept, win.outOf)
        },
        accent = true,
    )
}

@Composable
private fun ProblemLine(problem: SuggestionUi) {
    StoryLine(
        kicker = stringResource(R.string.insights_went_worst),
        text = pluralStringResource(
            R.plurals.insights_problem,
            problem.outOf,
            problem.title,
            problem.misses,
            problem.outOf,
        ),
        accent = false,
    )
}

@Composable
private fun StoryLine(kicker: String, text: String, accent: Boolean) {
    Row(modifier = Modifier.padding(top = 14.dp)) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp, end = Theme.spacing.inset)
                .width(Theme.spacing.rule * MARK_WIDTH)
                .height(MARK_HEIGHT)
                .background(
                    if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                ),
        )

        Column {
            Kicker(text = kicker)

            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = Theme.spacing.tight),
            )
        }
    }
}

private const val MARK_WIDTH = 3f
private val MARK_HEIGHT = 34.dp

/**
 * Every step being missed often enough to mention, and why.
 *
 * More than the one the suggestion picks. The suggestion has to choose a
 * single fix and act on it; this is the honest list, and three steps sharing
 * one cause is itself the finding: it is not three problems, it is one.
 */
@Composable
internal fun PatternSection(patterns: List<PatternUi>) {
    if (patterns.isEmpty()) return

    SectionLabel(text = stringResource(R.string.insights_patterns))

    Column(modifier = Modifier.padding(horizontal = Theme.spacing.medium)) {
        patterns.forEach { pattern ->
            Column(modifier = Modifier.padding(vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = pattern.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )

                    Text(
                        text = stringResource(
                            R.string.insights_pattern_count,
                            pattern.misses,
                            pattern.opportunities,
                        ),
                        style = TimeStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    text = patternText(pattern),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Theme.spacing.tight),
                )
            }

            HairlineRule()
        }
    }
}

/** A weekday cluster outranks a cause: it is the more specific thing to say. */
@Composable
private fun patternText(pattern: PatternUi): String = when {
    pattern.weekday != null -> stringResource(R.string.insights_pattern_weekday, pattern.weekday)
    else -> stringResource(causeText(pattern.cause))
}

/**
 * The goal, in one line, on the screen where somebody is already reviewing.
 *
 * A reminder rather than a report. The goal has a screen of its own; this
 * exists so that a goal set in week one is still visible in week six, which
 * is the week it stops being obvious and starts mattering.
 */
@Composable
internal fun GoalStrip(goal: GoalStripUi, onOpen: () -> Unit) {
    SectionLabel(text = stringResource(R.string.insights_goal))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(horizontal = Theme.spacing.medium, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = goal.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = if (goal.hasData) {
                    stringResource(standingText(goal.standing))
                } else {
                    stringResource(R.string.goal_nothing_yet)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Theme.spacing.tight),
            )
        }

        GoalNumbers(goal = goal)
    }

    HairlineRule()
}

@Composable
private fun GoalNumbers(goal: GoalStripUi) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = stringResource(R.string.insights_percent, goal.percent),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Text(
            text = pluralStringResource(R.plurals.goal_days_left, goal.daysLeft, goal.daysLeft),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun standingText(standing: GoalStanding): Int = when (standing) {
    GoalStanding.AHEAD -> R.string.goal_ahead
    GoalStanding.ON_PACE -> R.string.goal_on_pace
    GoalStanding.BEHIND -> R.string.goal_behind
    GoalStanding.REACHED -> R.string.goal_reached
    GoalStanding.OVER -> R.string.goal_over
    GoalStanding.UNKNOWN -> R.string.goal_too_early
}

private fun causeText(cause: SkipCause): Int = when (cause) {
    SkipCause.TIMING -> R.string.cause_timing
    SkipCause.MOTIVATION -> R.string.cause_motivation
    SkipCause.REMINDER -> R.string.cause_reminder
    SkipCause.UNKNOWN -> R.string.cause_unknown
}

/** The one line headline for the shape of the week. */
internal fun storyHeadline(story: ReviewStory): Int = when (story) {
    ReviewStory.ON_TRACK -> R.string.story_head_on_track
    ReviewStory.PLAN_TOO_SMALL -> R.string.story_head_plan_too_small
    ReviewStory.TIMING_PROBLEM -> R.string.story_head_timing_problem
    ReviewStory.REMINDER_PROBLEM -> R.string.story_head_reminder_problem
    ReviewStory.LOSING_GRIP -> R.string.story_head_losing_grip
    ReviewStory.SETTLING_IN -> R.string.story_head_settling_in
    ReviewStory.MIXED -> R.string.story_head_mixed
}

internal fun storyText(story: ReviewStory): Int = when (story) {
    ReviewStory.ON_TRACK -> R.string.story_on_track
    ReviewStory.PLAN_TOO_SMALL -> R.string.story_plan_too_small
    ReviewStory.TIMING_PROBLEM -> R.string.story_timing_problem
    ReviewStory.REMINDER_PROBLEM -> R.string.story_reminder_problem
    ReviewStory.LOSING_GRIP -> R.string.story_losing_grip
    ReviewStory.SETTLING_IN -> R.string.story_settling_in
    ReviewStory.MIXED -> R.string.story_mixed
}
