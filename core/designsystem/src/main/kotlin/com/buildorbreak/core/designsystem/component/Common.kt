package com.buildorbreak.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.buildorbreak.core.designsystem.theme.Theme

private val CardCorner = RoundedCornerShape(12.dp)

/**
 * A quiet card.
 *
 * Flat, with a hairline border rather than a shadow. Elevation on a warm paper
 * surface reads as a grey smudge, and a screen of raised cards turns a timeline
 * into a list of unrelated tiles. The border says "this is one thing" without
 * pretending the screen has depth it does not have.
 */
@Composable
fun QuietCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardCorner,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Theme.colours.rule),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        content()
    }
}

/**
 * Something the app noticed and the user may want to act on.
 *
 * Never a dialog. rules.md keeps warnings inline because a dialog interrupts
 * whatever somebody opened the app to do, and a routine app that interrupts is
 * an app that gets closed before the routine is read.
 */
@Composable
fun InlineNotice(
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    QuietCard(modifier = modifier) {
        Row(
            modifier = Modifier.padding(
                start = Theme.spacing.medium,
                end = Theme.spacing.small,
                top = Theme.spacing.small,
                bottom = Theme.spacing.small,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.small),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )

            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

/**
 * A screen with nothing on it yet, and one thing to do about that.
 *
 * An empty state that only says "nothing here" is a dead end. Every one in this
 * app names the next action, because a person looking at an empty routine app
 * has not failed at anything, they have simply not started.
 */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Theme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.small, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                modifier = Modifier.padding(top = Theme.spacing.small),
            ) {
                Text(actionLabel)
            }
        }
    }
}

/** A hairline rule, at the one weight the whole app uses. */
@Composable
fun Rule(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = Theme.colours.rule)
}
