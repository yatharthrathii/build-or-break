package com.buildorbreak.app.feature.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.buildorbreak.app.R
import com.buildorbreak.core.designsystem.component.GhostButton
import com.buildorbreak.core.designsystem.component.HeavyRule
import com.buildorbreak.core.designsystem.component.Kicker
import com.buildorbreak.core.designsystem.component.OutlineButton
import com.buildorbreak.core.designsystem.theme.Theme

private const val FADE_MILLIS = 180

/**
 * The last tap, still takeable back.
 *
 * Across the bottom rather than as a dialog, and it leaves on its own. The
 * whole point is that it costs nothing to ignore: somebody who meant to press
 * Done should be able to carry on reading the day without dismissing anything,
 * and somebody who did not should find the way back exactly where their thumb
 * already is.
 *
 * It sits over the timeline instead of pushing it down. A bar that moved the
 * list every time a step was completed would make the next row jump out from
 * under the finger about to tap it.
 */
@Composable
internal fun UndoBar(offer: UndoOffer?, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = offer != null,
        enter = slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(tween(FADE_MILLIS)),
        exit = slideOutVertically(tween(FADE_MILLIS)) { it } + fadeOut(tween(FADE_MILLIS)),
        modifier = modifier,
    ) {
        // Kept across the exit animation, so the bar does not blank out its own
        // words on the way off screen.
        val shown = offer ?: return@AnimatedVisibility

        Column(modifier = Modifier.fillMaxWidth()) {
            HeavyRule()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(start = Theme.spacing.medium, end = Theme.spacing.inset, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Kicker(
                        text = stringResource(settledLabel(shown.kind)),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )

                    Text(
                        text = shown.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                OutlineButton(text = stringResource(R.string.today_undo), onClick = onUndo)
            }
        }
    }
}

private fun settledLabel(kind: SettleKind): Int = when (kind) {
    SettleKind.DONE -> R.string.today_undo_done
    SettleKind.MINIMUM -> R.string.today_undo_minimum
    SettleKind.SKIPPED -> R.string.today_undo_skipped
}

/**
 * The question owed to a skip made from a notification.
 *
 * Most of this app's interactions are meant to finish in the shade, which is
 * also why most skips arrive by the one route that cannot ask why. Left alone,
 * the weekly review would be assembled from the small minority of skips made
 * with the app already open, and would present that biased sample as a finding.
 *
 * So it is asked here instead: once, quietly, in a strip that can be waved away
 * and is never shown for the same skip twice.
 */
@Composable
internal fun SkipAskBar(ask: SkipAsk, onAnswer: () -> Unit, onWaveAway: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Theme.colours.raised)
            .padding(horizontal = Theme.spacing.medium, vertical = 12.dp),
    ) {
        Kicker(text = stringResource(R.string.today_ask_kicker))

        Text(
            text = ask.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )

        Text(
            text = stringResource(R.string.today_ask_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlineButton(text = stringResource(R.string.today_ask_answer), onClick = onAnswer)

            GhostButton(
                text = stringResource(R.string.today_ask_dismiss),
                onClick = onWaveAway,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
