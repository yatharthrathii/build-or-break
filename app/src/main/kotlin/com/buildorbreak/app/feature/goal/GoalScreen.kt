package com.buildorbreak.app.feature.goal

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buildorbreak.app.R
import com.buildorbreak.app.feature.insights.standingText
import com.buildorbreak.core.designsystem.component.BlockButton
import com.buildorbreak.core.designsystem.component.EmptyState
import com.buildorbreak.core.designsystem.component.GhostButton
import com.buildorbreak.core.designsystem.component.HairlineRule
import com.buildorbreak.core.designsystem.component.HeavyRule
import com.buildorbreak.core.designsystem.component.Kicker
import com.buildorbreak.core.designsystem.component.Label
import com.buildorbreak.core.designsystem.component.OutlineButton
import com.buildorbreak.core.designsystem.component.Panel
import com.buildorbreak.core.designsystem.component.SectionLabel
import com.buildorbreak.core.designsystem.component.SquareToggle
import com.buildorbreak.core.designsystem.component.TrailColumns
import com.buildorbreak.core.designsystem.theme.BuildOrBreakTheme
import com.buildorbreak.core.designsystem.theme.HeroNumberStyle
import com.buildorbreak.core.designsystem.theme.Theme
import com.buildorbreak.core.designsystem.theme.TimeStyle
import com.buildorbreak.core.domain.goal.GoalStanding
import com.buildorbreak.core.model.enums.GoalKind
import com.buildorbreak.core.model.enums.ValueKind
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.collections.immutable.persistentListOf

private val BarHeight = 14.dp
private val TrailHeight = 72.dp

/** A bar is never invisible. Nothing done still leaves a mark to grow from. */
private const val MIN_BAR = 0.015f

/**
 * One goal, and how it is actually going.
 *
 * The pace line is the whole design. Forty percent of a goal means nothing
 * until you know whether forty percent of the time has gone, so the marker
 * sits on the bar and the sentence underneath says which side of it you are
 * on. A progress bar with no pace line is a number that always looks like
 * progress, right up to the week it runs out.
 *
 * A goal is optional. The empty state is an offer, never a nag.
 */
@Composable
fun GoalScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenReadings: () -> Unit = {},
    onAddReading: () -> Unit = {},
    viewModel: GoalViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    GoalContent(
        state = state,
        onOpenReadings = onOpenReadings,
        onAddReading = onAddReading,
        onWeekCounted = { week, counted -> viewModel.onWeekCounted(week, counted) },
        onNew = viewModel::onNew,
        onEdit = viewModel::onEdit,
        onChange = viewModel::onChange,
        onSave = viewModel::onSave,
        onCancel = viewModel::onCancel,
        onRetire = viewModel::onRetire,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun GoalContent(
    state: GoalUiState,
    onNew: () -> Unit,
    onEdit: () -> Unit,
    onChange: (GoalDraft) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onRetire: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenReadings: () -> Unit = {},
    onAddReading: () -> Unit = {},
    onWeekCounted: (LocalDate, Boolean) -> Unit = { _, _ -> },
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .imePadding(),
    ) {
        GoalHeader(state = state, onBack = onBack, onCancel = onCancel, onSave = onSave)

        when {
            state.draft != null -> GoalForm(draft = state.draft, items = state.items, onChange = onChange)
            state.goal != null -> GoalBody(
                goal = state.goal,
                onNew = onNew,
                onEdit = onEdit,
                onRetire = onRetire,
                onOpenReadings = onOpenReadings,
                onAddReading = onAddReading,
                onWeekCounted = onWeekCounted,
            )
            state.loaded -> NoGoal(onNew = onNew)
            // Nothing, rather than "no goal yet", before the first read.
            else -> Unit
        }
    }
}

@Composable
private fun GoalHeader(
    state: GoalUiState,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Theme.spacing.medium, end = Theme.spacing.medium, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(role = Role.Button) { if (state.isEditing) onCancel() else onBack() },
            )

            Text(
                text = stringResource(R.string.goal_title).uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Theme.spacing.inset),
            )

            if (state.draft != null) {
                com.buildorbreak.core.designsystem.component.FillButton(
                    text = stringResource(R.string.editor_save),
                    onClick = onSave,
                    enabled = state.draft.canSave,
                )
            }
        }

        HeavyRule()

        state.draft?.blocker?.let { BlockerLine(text = stringResource(goalBlockerText(it))) }

        if (state.saveFailed) {
            BlockerLine(text = stringResource(R.string.editor_save_failed), error = true)
        }
    }
}

@Composable
private fun BlockerLine(text: String, error: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (error) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primaryContainer,
            )
            .padding(horizontal = Theme.spacing.medium, vertical = Theme.spacing.small),
    )
}

/**
 * See the numbers, and add one.
 *
 * The two sit together because they are the same question from either end: a
 * step ticked from a notification settles the day and asks for no figure, and
 * the figure still has to go somewhere afterwards.
 */
@Composable
private fun ReadingActions(onOpenReadings: () -> Unit, onAddReading: () -> Unit) {
    Row(
        modifier = Modifier.padding(start = Theme.spacing.tight, top = Theme.spacing.small),
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GhostButton(
            text = stringResource(R.string.goal_see_readings),
            onClick = onOpenReadings,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )

        GhostButton(
            text = stringResource(R.string.readings_add),
            onClick = onAddReading,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun GoalBody(
    goal: GoalCardUi,
    onNew: () -> Unit,
    onEdit: () -> Unit,
    onRetire: () -> Unit,
    onOpenReadings: () -> Unit,
    onAddReading: () -> Unit,
    onWeekCounted: (LocalDate, Boolean) -> Unit,
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        if (goal.isFinished) Finished(goal = goal, onNew = onNew)

        GoalHero(goal = goal)
        PaceBar(goal = goal)
        GoalNumbers(goal = goal)

        if (goal.trail.size > 1) {
            SectionLabel(text = stringResource(R.string.goal_so_far))
            Trail(values = goal.trail)
        }

        // Only a measured goal has readings. A count of gym sessions is
        // corrected by un-ticking the step, not by editing a figure.
        if (goal.kind == GoalKind.NUMBER) {
            ReadingActions(onOpenReadings = onOpenReadings, onAddReading = onAddReading)
        }

        // Not on a finished goal. There is no forecast left for a week to bend.
        if (goal.weeks.isNotEmpty() && !goal.isFinished) {
            Weeks(weeks = goal.weeks, onWeekCounted = onWeekCounted)
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(Theme.spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlineButton(text = stringResource(R.string.goal_edit), onClick = onEdit)
            GhostButton(
                text = stringResource(R.string.goal_retire),
                onClick = onRetire,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * The weeks that can be left out, each with a switch.
 *
 * A week of flu should not decide where a goal is heading. The switch leaves
 * the week out of the pace and the forecast and touches nothing else: the
 * days stay saved, and switching it back on brings them straight back.
 */
@Composable
private fun Weeks(weeks: List<GoalWeekUi>, onWeekCounted: (LocalDate, Boolean) -> Unit) {
    SectionLabel(text = stringResource(R.string.goal_weeks_title))

    Text(
        text = stringResource(R.string.goal_weeks_body),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Theme.spacing.medium).padding(bottom = Theme.spacing.small),
    )

    HairlineRule()

    weeks.forEach { week ->
        WeekRow(week = week, onCounted = { onWeekCounted(week.start, it) })
        HairlineRule()
    }
}

@Composable
private fun WeekRow(week: GoalWeekUi, onCounted: (Boolean) -> Unit) {
    val range = stringResource(R.string.goal_week_range, week.start.format(shortDate()), week.end.format(shortDate()))
    val status = stringResource(if (week.counted) R.string.goal_week_counts else R.string.goal_week_left_out)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Theme.spacing.medium, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = when (week.weeksAgo) {
                    0 -> stringResource(R.string.goal_week_this)
                    1 -> stringResource(R.string.goal_week_last)
                    else -> range
                },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                // An older week is already titled with its dates. Saying them twice
                // made the row read like a mistake.
                text = if (week.weeksAgo > 1) status else stringResource(R.string.goal_week_dated, range, status),
                style = MaterialTheme.typography.bodySmall,
                color = if (week.counted) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        SquareToggle(
            checked = week.counted,
            onCheckedChange = onCounted,
            modifier = Modifier.padding(start = Theme.spacing.medium),
        )
    }
}

/** "21 Sep". A function, so it follows the language the phone is in now. */
private fun shortDate(): DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

/**
 * The verdict, once the goal is over.
 *
 * It says which of the two happened and then offers the next one, because
 * the moment a goal ends is the only moment somebody is actually thinking
 * about setting another. Left to itself the screen would sit on a full bar
 * and "0 days left" for as long as the goal was never retired, which reads
 * as an app that has not noticed.
 *
 * Nothing here is congratulatory about a miss and nothing is grudging about
 * a win. It is the same two lines either way, with the numbers changed.
 */
@Composable
private fun Finished(goal: GoalCardUi, onNew: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = Theme.spacing.medium, vertical = Theme.spacing.inset)) {
        Panel {
            Column {
                Verdict(goal = goal)

                BlockButton(text = stringResource(R.string.goal_finished_new), onClick = onNew)
            }
        }
    }
}

/** Which of the two happened, and the numbers that say so. */
@Composable
private fun Verdict(goal: GoalCardUi) {
    val unit = stringResource(goalUnit(goal.valueKind, goal.kind))

    Column(modifier = Modifier.padding(Theme.spacing.inset)) {
        Kicker(text = stringResource(R.string.goal_finished_kicker), color = MaterialTheme.colorScheme.primary)

        Text(
            text = stringResource(if (goal.reached) R.string.goal_finished_reached else R.string.goal_finished_missed),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Theme.spacing.small),
        )

        Text(
            text = verdictLine(goal = goal, unit = unit),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Theme.spacing.small),
        )

        Text(
            text = stringResource(R.string.goal_finished_keeps),
            style = MaterialTheme.typography.bodySmall,
            color = Theme.colours.faint,
            modifier = Modifier.padding(top = Theme.spacing.small),
        )
    }
}

/** A measured goal travelled between two levels; every other kind counted up to one. */
@Composable
private fun verdictLine(goal: GoalCardUi, unit: String): String = if (goal.kind == GoalKind.NUMBER) {
    stringResource(
        R.string.goal_finished_measured,
        format(goal.startValue) + " " + unit,
        format(goal.current) + " " + unit,
        format(goal.target) + " " + unit,
    )
} else {
    stringResource(R.string.goal_finished_counted, format(goal.current), format(goal.target) + " " + unit)
}

@Composable
private fun GoalHero(goal: GoalCardUi) {
    Column(
        modifier = Modifier.padding(
            start = Theme.spacing.medium,
            end = Theme.spacing.medium,
            top = Theme.spacing.medium,
            bottom = Theme.spacing.inset,
        ),
    ) {
        Kicker(text = goal.title.uppercase(Locale.getDefault()), color = MaterialTheme.colorScheme.primary)

        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = Theme.spacing.small)) {
            Text(
                text = stringResource(R.string.insights_percent, goal.percent),
                style = HeroNumberStyle,
                color = MaterialTheme.colorScheme.onSurface,
            )

            HeroFigures(goal = goal)
        }
    }
}

/** The two small lines beside the big percentage: where it is, and how that reads. */
@Composable
private fun HeroFigures(goal: GoalCardUi) {
    Column(modifier = Modifier.padding(start = Theme.spacing.medium, bottom = Theme.spacing.small)) {
        Text(
            text = stringResource(
                R.string.goal_current_of,
                format(goal.current),
                format(goal.target),
                stringResource(goalUnit(goal.valueKind, goal.kind)),
            ),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Text(
            text = if (goal.hasData) {
                stringResource(standingText(goal.standing))
            } else {
                stringResource(R.string.goal_nothing_yet)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (goal.standing == GoalStanding.BEHIND || goal.standing == GoalStanding.OVER) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            },
            modifier = Modifier.padding(top = Theme.spacing.tight),
        )
    }
}

/**
 * The bar, with the pace line marked on it.
 *
 * The one number that makes the bar mean anything. Being at forty percent is
 * good news in week two and bad news in week seven, and a bar without the
 * marker cannot tell the difference.
 */
@Composable
private fun PaceBar(goal: GoalCardUi) {
    val filled by animateFloatAsState(
        targetValue = (goal.percent / HUNDRED).coerceIn(0f, 1f),
        animationSpec = spring(stiffness = 120f),
        label = "goal",
    )

    Column(modifier = Modifier.padding(horizontal = Theme.spacing.medium)) {
        Bar(filled = filled, pace = (goal.pacePercent / HUNDRED).coerceIn(0f, 1f))

        Text(
            text = stringResource(R.string.goal_pace_marker, goal.pacePercent),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Theme.spacing.small),
        )
    }
}

/** The fill, with the pace marker drawn over it so it stays visible either side. */
@Composable
private fun Bar(filled: Float, pace: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(BarHeight)
            .background(Theme.colours.rail),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(filled.coerceAtLeast(MIN_BAR))
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary),
        )

        Box(
            modifier = Modifier.fillMaxWidth(pace).fillMaxHeight(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                modifier = Modifier
                    .width(Theme.spacing.rule * MARKER_WIDTH)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.onSurface),
            )
        }
    }
}

private const val HUNDRED = 100f
private const val MARKER_WIDTH = 2f

@Composable
private fun GoalNumbers(goal: GoalCardUi) {
    Column(modifier = Modifier.padding(top = 14.dp)) {
        HairlineRule()

        NumberRow(
            label = stringResource(R.string.goal_pace_target),
            value = format(goal.paceTarget),
        )
        // No verdict until a day has finished. A goal set this morning has
        // not fallen short of anything, and saying it has is the same lie as
        // a progress bar with no pace line: confident, early, and wrong in
        // the direction that makes somebody give up.
        NumberRow(
            label = stringResource(R.string.goal_projection),
            value = if (goal.hasProjection) format(goal.projected) else stringResource(R.string.goal_no_rate_yet),
            note = if (goal.hasProjection) {
                stringResource(if (goal.willReach) R.string.goal_will_reach else R.string.goal_will_miss)
            } else {
                stringResource(R.string.goal_no_rate_body)
            },
        )
        NumberRow(
            label = stringResource(R.string.goal_time),
            value = pluralStringResource(R.plurals.goal_days_left, goal.daysLeft, goal.daysLeft),
            note = stringResource(R.string.goal_day_of, goal.daysElapsed, goal.totalDays),
        )
    }
}

@Composable
private fun NumberRow(label: String, value: String, note: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Theme.spacing.medium, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            note?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        Text(text = value, style = TimeStyle, color = MaterialTheme.colorScheme.onSurface)
    }

    HairlineRule()
}

/** The line so far, one column per recorded day. Values only; no invented zeroes. */
@Composable
private fun Trail(values: List<Double>) {
    val range = stringResource(R.string.goal_trail_range, format(values.min()), format(values.max()))

    // The columns are the shape of the line; the range is what it says. A
    // reader gets the range once, from the chart itself.
    TrailColumns(
        values = values,
        description = range,
        height = TrailHeight,
        modifier = Modifier.padding(horizontal = Theme.spacing.medium, vertical = 12.dp),
    )

    Label(
        text = range,
        modifier = Modifier.padding(horizontal = Theme.spacing.medium).clearAndSetSemantics {},
    )
}

@Composable
private fun NoGoal(onNew: () -> Unit) {
    EmptyState(title = stringResource(R.string.goal_none_title), body = stringResource(R.string.goal_none_body)) {
        BlockButton(text = stringResource(R.string.goal_set_one), onClick = onNew)
    }
}

/** Whole numbers stay whole. Nobody wants to read 12.0 gym sessions. */
internal fun format(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.getDefault(), "%.1f", value)

internal fun goalUnit(valueKind: ValueKind, kind: GoalKind): Int = when {
    kind == GoalKind.COUNT -> R.string.goal_unit_times
    kind == GoalKind.DURATION -> R.string.unit_minutes
    kind == GoalKind.CONSISTENCY -> R.string.goal_unit_percent
    valueKind == ValueKind.WEIGHT_KG -> R.string.unit_kg
    valueKind == ValueKind.PAGES -> R.string.unit_pages
    valueKind == ValueKind.REPS -> R.string.unit_reps
    valueKind == ValueKind.MINUTES -> R.string.unit_minutes
    else -> R.string.unit_none
}

private fun goalBlockerText(blocker: GoalBlocker): Int = when (blocker) {
    GoalBlocker.NO_TITLE -> R.string.goal_reason_title
    GoalBlocker.NO_TARGET -> R.string.goal_reason_target
    GoalBlocker.NO_START -> R.string.goal_reason_start
    GoalBlocker.NO_ITEM -> R.string.goal_reason_item
    GoalBlocker.GOES_NOWHERE -> R.string.goal_reason_nowhere
}

// Preview -----------------------------------------------------------------------

@Preview(name = "Goal", showBackground = true)
@Composable
private fun GoalPreview() {
    BuildOrBreakTheme {
        GoalContent(
            state = GoalUiState(loaded = true, goal = previewGoal(), draft = null, items = persistentListOf()),
            onNew = {},
            onEdit = {},
            onChange = {},
            onSave = {},
            onCancel = {},
            onRetire = {},
            onBack = {},
        )
    }
}

// Fixture data, literal on purpose so the preview can be read at a glance.
@Suppress("MagicNumber")
private fun previewGoal() = GoalCardUi(
    id = 1,
    title = "Twelve gym sessions",
    kind = GoalKind.COUNT,
    valueKind = ValueKind.NONE,
    itemId = 3,
    current = 7.0,
    target = 12.0,
    paceTarget = 8.0,
    projected = 10.5,
    percent = 58,
    pacePercent = 67,
    standing = GoalStanding.BEHIND,
    daysLeft = 19,
    daysElapsed = 37,
    totalDays = 56,
    willReach = false,
    hasData = true,
    isFinished = false,
    reached = false,
    hasProjection = true,
    trail = persistentListOf(1.0, 2.0, 2.0, 3.0, 4.0, 5.0, 5.0, 6.0, 7.0),
)
