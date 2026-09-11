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
import com.buildorbreak.core.designsystem.component.SectionLabel
import com.buildorbreak.core.designsystem.theme.BuildOrBreakTheme
import com.buildorbreak.core.designsystem.theme.HeroNumberStyle
import com.buildorbreak.core.designsystem.theme.Theme
import com.buildorbreak.core.designsystem.theme.TimeStyle
import com.buildorbreak.core.domain.goal.GoalStanding
import com.buildorbreak.core.model.enums.GoalKind
import com.buildorbreak.core.model.enums.ValueKind
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
fun GoalScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: GoalViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    GoalContent(
        state = state,
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
            state.goal != null -> GoalBody(goal = state.goal, onEdit = onEdit, onRetire = onRetire)
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
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
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
                    .padding(start = 14.dp),
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
            .padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

@Composable
private fun GoalBody(goal: GoalCardUi, onEdit: () -> Unit, onRetire: () -> Unit) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        GoalHero(goal = goal)
        PaceBar(goal = goal)
        GoalNumbers(goal = goal)

        if (goal.trail.size > 1) {
            SectionLabel(text = stringResource(R.string.goal_so_far))
            Trail(values = goal.trail)
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
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

@Composable
private fun GoalHero(goal: GoalCardUi) {
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 14.dp)) {
        Kicker(text = goal.title.uppercase(Locale.getDefault()), color = MaterialTheme.colorScheme.primary)

        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 6.dp)) {
            Text(
                text = stringResource(R.string.insights_percent, goal.percent),
                style = HeroNumberStyle,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Column(modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)) {
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
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
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

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Bar(filled = filled, pace = (goal.pacePercent / HUNDRED).coerceIn(0f, 1f))

        Text(
            text = stringResource(R.string.goal_pace_marker, goal.pacePercent),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 7.dp),
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
    val top = values.max()
    val bottom = values.min()
    val span = (top - bottom).takeIf { it > 0.0 } ?: 1.0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(TrailHeight),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEach { value ->
            val fraction = ((value - bottom) / span).toFloat().coerceIn(0f, 1f)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(fraction.coerceAtLeast(MIN_BAR))
                    .background(MaterialTheme.colorScheme.onSurface),
            )
        }
    }

    Label(
        text = stringResource(R.string.goal_trail_range, format(bottom), format(top)),
        modifier = Modifier.padding(horizontal = 16.dp),
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
            state = GoalUiState(
                loaded = true,
                goal = GoalCardUi(
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
                    hasProjection = true,
                    trail = persistentListOf(1.0, 2.0, 2.0, 3.0, 4.0, 5.0, 5.0, 6.0, 7.0),
                ),
                draft = null,
                items = persistentListOf(),
            ),
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
