package com.buildorbreak.app.feature.insights

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.buildorbreak.app.R
import com.buildorbreak.core.designsystem.component.HairlineRule
import com.buildorbreak.core.designsystem.component.Kicker
import com.buildorbreak.core.designsystem.component.Label
import com.buildorbreak.core.designsystem.component.SectionLabel
import com.buildorbreak.core.designsystem.theme.Theme
import com.buildorbreak.core.designsystem.theme.TimeStyle
import com.buildorbreak.core.model.enums.Milestone
import java.text.NumberFormat
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** Three to a row, three rows: the nine, in the order they are declared. */
private const val BADGES_PER_ROW = 3

private val BadgeMark = 18.dp
private val BadgeBorder = 2.dp

/** Nothing typed, nothing closed. A dash, not a zero: zero is a score. */
private const val NO_FIGURE = "—"

/** How far an unearned badge is turned down. Still legible, clearly not lit. */
private const val UNEARNED_ALPHA = 0.55f

/** An unlit square sits a little smaller, so the lit ones read as grown. */
private const val UNEARNED_SCALE = 0.85f

/**
 * The score and the wall.
 *
 * Every number on this screen is one the user can check against the list
 * under it, and the points are no different: ten a step, five for a
 * smaller version, twenty for a whole day, and the legend says so in one
 * line. The badges are the nine milestones the app already awards, laid
 * out whole, lit or not, so what is still to come is as visible as what
 * has been done.
 */
@Immutable
data class RewardsUi(
    /** Every closed day. Today joins it at midnight. */
    val banked: Int,
    val thisWeek: Int,
    val bestDay: Int?,
    val badges: ImmutableList<BadgeUi>,
) {
    val earnedCount: Int get() = badges.count { it.earnedOn != null }
}

/** One of the nine. [earnedOn] is already formatted, or null when not yet. */
@Immutable
data class BadgeUi(val milestone: Milestone, val earnedOn: String?)

@Composable
internal fun RewardsSection(rewards: RewardsUi) {
    SectionLabel(text = stringResource(R.string.insights_rewards))

    PointsFigures(rewards = rewards)

    Text(
        text = stringResource(R.string.insights_points_legend),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Theme.spacing.medium).padding(top = Theme.spacing.inset),
    )

    BadgeWall(rewards = rewards)

    HairlineRule()
}

/** Three equal columns, so the row reads as a table and not as a sentence. */
@Composable
private fun PointsFigures(rewards: RewardsUi) {
    val format = remember { NumberFormat.getIntegerInstance() }

    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Theme.spacing.medium)) {
        Figure(
            value = format.format(rewards.banked),
            label = stringResource(R.string.insights_points_banked),
            strong = true,
        )
        Figure(value = format.format(rewards.thisWeek), label = stringResource(R.string.insights_points_week))
        Figure(
            value = rewards.bestDay?.let(format::format) ?: NO_FIGURE,
            label = stringResource(R.string.insights_points_best),
        )
    }
}

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

/**
 * The nine, three to a row.
 *
 * A fixed grid rather than a lazy one: it is nine cells inside a scrolling
 * column, and a lazy grid inside a scroll is a fight nobody wins.
 */
@Composable
private fun BadgeWall(rewards: RewardsUi) {
    Column(
        modifier = Modifier
            .padding(horizontal = Theme.spacing.medium)
            .padding(top = Theme.spacing.large, bottom = Theme.spacing.medium),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Kicker(text = stringResource(R.string.insights_badges), modifier = Modifier.weight(1f))

            Kicker(
                text = stringResource(R.string.insights_badges_count, rewards.earnedCount, rewards.badges.size),
                color = MaterialTheme.colorScheme.primary,
            )
        }

        rewards.badges.chunked(BADGES_PER_ROW).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth().padding(top = Theme.spacing.medium)) {
                row.forEach { badge -> BadgeCell(badge = badge) }
            }
        }
    }
}

/** A square, lit when earned, with the name and the day under it. */
@Composable
private fun RowScope.BadgeCell(badge: BadgeUi) {
    val earned = badge.earnedOn != null
    val title = stringResource(badgeTitle(badge.milestone))
    val earnedLine = badge.earnedOn ?: stringResource(R.string.insights_badge_not_yet)
    // The state read aloud with the name, so a screen reader hears "Halfway,
    // not yet" and not two unrelated lines.
    val description = "$title, $earnedLine"

    Column(
        modifier = Modifier
            .weight(1f)
            .padding(end = Theme.spacing.small)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        BadgeMark(earned = earned)

        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Theme.spacing.small).graphicsLayer {
                alpha = if (earned) 1f else UNEARNED_ALPHA
            },
        )

        Text(
            text = earnedLine,
            style = MaterialTheme.typography.bodySmall,
            color = if (earned) MaterialTheme.colorScheme.onSurfaceVariant else Theme.colours.faint,
            modifier = Modifier.padding(top = Theme.spacing.hairline),
        )
    }
}

/**
 * The square. Lit badges land: the mark springs up to full size when the
 * wall is first drawn, so a new one is seen arriving rather than found.
 */
@Composable
private fun BadgeMark(earned: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (earned) 1f else UNEARNED_SCALE,
        animationSpec = spring(stiffness = 220f),
        label = "badge",
    )

    Box(
        modifier = Modifier
            .size(BadgeMark)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (earned) {
                    Modifier.background(MaterialTheme.colorScheme.primary)
                } else {
                    Modifier.border(BadgeBorder, Theme.colours.rail)
                },
            ),
    )
}

/** Short names for the wall. The banner on Today keeps its full sentences. */
internal fun badgeTitle(milestone: Milestone): Int = when (milestone) {
    Milestone.FIRST_COMPLETION -> R.string.badge_first_completion
    Milestone.FIRST_FULL_DAY -> R.string.badge_first_full_day
    Milestone.FIRST_WEEK -> R.string.badge_first_week
    Milestone.GOAL_QUARTER -> R.string.badge_goal_quarter
    Milestone.GOAL_HALF -> R.string.badge_goal_half
    Milestone.GOAL_THREE_QUARTERS -> R.string.badge_goal_three_quarters
    Milestone.GOAL_REACHED -> R.string.badge_goal_reached
    Milestone.BEST_WEEK -> R.string.badge_best_week
    Milestone.ITEM_THIRTY_DAY_RUN -> R.string.badge_thirty_day_run
}

/** Every badge unearned. For previews and for a state before the first read. */
internal fun emptyWall(): ImmutableList<BadgeUi> = Milestone.entries.map { BadgeUi(it, null) }.toImmutableList()
