package com.buildorbreak.app.feature.today

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.buildorbreak.app.R
import com.buildorbreak.app.feature.goal.format
import com.buildorbreak.app.feature.goal.goalUnit
import com.buildorbreak.app.feature.insights.standingText
import com.buildorbreak.core.designsystem.component.HairlineRule
import com.buildorbreak.core.designsystem.component.Kicker
import com.buildorbreak.core.designsystem.component.Label
import com.buildorbreak.core.designsystem.theme.Theme
import com.buildorbreak.core.designsystem.theme.TimeStyle
import com.buildorbreak.core.domain.goal.GoalStanding
import com.buildorbreak.core.model.enums.GoalKind
import java.util.Locale
import kotlin.math.abs

private val BarHeight = 4.dp
private val MarkerWidth = 2.dp

/** A bar is never invisible. Nothing done still leaves a mark to grow from. */
private const val MIN_BAR = 0.02f
private const val HUNDRED = 100f

/** Nothing typed today. A dash, not a zero: zero is a reading. */
private const val NO_READING = "—"

/**
 * The goal, at the top of Today, as a row of figures.
 *
 * Not a card. The one bordered box on this screen is the next step, and a
 * second one above it made the two compete. This is set like the numbers on
 * a scoreboard: four figures in four equal columns, a label under each, and
 * the thin pace bar underneath. The eye reads it in one pass and the step
 * below it is still the loudest thing on the page.
 *
 * The first figure is the honest one: a seven day average for a weight, a
 * tally for sessions. Today's own reading sits beside it, because somebody
 * who weighed 50.5 this morning and sees 49.8 will assume the app is wrong
 * unless the strip shows both and says which is which. Nothing here
 * flatters. Behind is said as behind, and a goal with nothing logged says so
 * instead of showing a zero.
 */
@Composable
internal fun GoalHeroCard(goal: GoalHeroUi, onOpen: () -> Unit) {
    Column {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onOpen)
                .padding(horizontal = Theme.spacing.medium)
                .padding(top = Theme.spacing.inset, bottom = Theme.spacing.medium),
        ) {
            Heading(goal = goal)
            Figures(goal = goal)
            PaceBar(goal = goal)
            StandingLine(goal = goal)
        }

        HairlineRule()
    }
}

/** The goal's name on the left; its unit and the time left on the right. */
@Composable
private fun Heading(goal: GoalHeroUi) {
    val unit = stringResource(goalUnit(goal.valueKind, goal.kind))
    val remaining = if (goal.isFinished) {
        stringResource(R.string.goal_hero_finished)
    } else {
        pluralStringResource(R.plurals.goal_days_left, goal.daysLeft, goal.daysLeft)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Kicker(
            text = goal.title.uppercase(Locale.getDefault()),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )

        Kicker(
            text = listOf(unit, remaining).filter { it.isNotBlank() }.joinToString(" · "),
            color = if (goal.isFinished) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/**
 * Equal columns.
 *
 * A measured goal: the average, today's reading, where it started and where
 * it is going. Everything else counts, so it shows what is done, what is
 * left and the target. Equal widths whatever the numbers, so the row reads
 * as a table and not as a sentence.
 */
@Composable
private fun Figures(goal: GoalHeroUi) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = Theme.spacing.inset)) {
        if (goal.kind == GoalKind.NUMBER) {
            Figure(
                value = if (goal.hasData) format(goal.current) else NO_READING,
                label = stringResource(R.string.goal_hero_average),
            )
            Figure(
                value = goal.todayReading?.let(::format) ?: NO_READING,
                label = stringResource(R.string.goal_hero_today),
            )
            Figure(value = format(goal.startValue), label = stringResource(R.string.goal_hero_start))
            Figure(value = format(goal.target), label = stringResource(R.string.goal_hero_target), strong = true)
        } else {
            Figure(value = format(goal.current), label = stringResource(R.string.goal_hero_done))
            Figure(
                value = format((goal.target - goal.current).coerceAtLeast(0.0)),
                label = stringResource(R.string.goal_hero_left),
            )
            Figure(value = format(goal.target), label = stringResource(R.string.goal_hero_target), strong = true)
        }
    }
}

/** One number, with what it is written underneath in small capitals. */
@Composable
private fun RowScope.Figure(value: String, label: String, strong: Boolean = false) {
    Column(modifier = Modifier.weight(1f)) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Black,
                fontFeatureSettings = TimeStyle.fontFeatureSettings,
            ),
            color = if (strong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )

        Label(text = label, color = Theme.colours.faint, modifier = Modifier.padding(top = Theme.spacing.tight))
    }
}

/** The thin bar with the pace mark on it. */
@Composable
private fun PaceBar(goal: GoalHeroUi) {
    val filled by animateFloatAsState(
        targetValue = (goal.percent / HUNDRED).coerceIn(0f, 1f),
        animationSpec = spring(stiffness = 120f),
        label = "goal",
    )
    val pace = (goal.pacePercent / HUNDRED).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Theme.spacing.medium)
            .height(BarHeight)
            .background(Theme.colours.rail),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(filled.coerceAtLeast(MIN_BAR))
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary),
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            if (pace > 0f) Spacer(modifier = Modifier.weight(pace))

            Box(
                modifier = Modifier
                    .width(MarkerWidth)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.onSurface),
            )

            if (pace < 1f) Spacer(modifier = Modifier.weight(1f - pace))
        }
    }
}

/**
 * Where it stands, and how far it has come.
 *
 * In the first week of a measured goal the average is built from too few
 * days to mean much, and the line says so rather than letting a two day
 * average read as a verdict.
 */
@Composable
private fun StandingLine(goal: GoalHeroUi) {
    Row(modifier = Modifier.padding(top = Theme.spacing.small), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = when {
                goal.isSettling && !goal.isFinished -> stringResource(R.string.goal_hero_settling)
                goal.hasData -> stringResource(standingText(goal.standing))
                else -> stringResource(R.string.goal_nothing_yet)
            },
            style = MaterialTheme.typography.bodySmall,
            // The accent is for standing somewhere. Nothing logged yet is
            // not a standing, and in red it reads as a fault.
            color = if (goal.hasData && goal.standing != GoalStanding.BEHIND && goal.standing != GoalStanding.OVER) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f),
        )

        ChangeSinceStart(goal = goal)
    }
}

/** "+1.8 kg since 1 Sep". Measured goals only; a tally is its own change. */
@Composable
private fun ChangeSinceStart(goal: GoalHeroUi) {
    if (goal.kind != GoalKind.NUMBER || !goal.hasData) return

    val unit = stringResource(goalUnit(goal.valueKind, goal.kind))
    val sign = if (goal.changeSinceStart < 0) "−" else "+"
    val change = sign + format(abs(goal.changeSinceStart)) + " " + unit

    Text(
        text = stringResource(R.string.goal_hero_since, change, goal.startDate),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
