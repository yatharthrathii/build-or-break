package com.buildorbreak.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A column is never invisible. The lowest value is still a value somebody logged. */
private const val MIN_COLUMN = 0.015f

private val ColumnGap = 3.dp

private const val RISE_MILLIS = 320
private const val SWEEP_MILLIS = 280

/**
 * A run of numbers as columns, lowest to highest, oldest first.
 *
 * Scaled between the smallest and largest value rather than from zero. A
 * body weight that moves between 49 and 52 drawn from zero is a row of
 * identical blocks, and the whole reason to draw it is the movement.
 *
 * [description] is what a screen reader hears in place of the columns. The
 * caller supplies it because the words belong to the screen: this draws.
 */
@Composable
fun TrailColumns(
    values: List<Double>,
    description: String,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    if (values.isEmpty()) return

    val top = values.max()
    val bottom = values.min()
    val span = (top - bottom).takeIf { it > 0.0 } ?: 1.0

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(ColumnGap),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEachIndexed { index, value ->
            val fraction = ((value - bottom) / span).toFloat().coerceIn(0f, 1f)

            // Each column a beat after the one before it, so the line is
            // drawn rather than switched on. The whole sweep takes the same
            // time however many columns there are: thirty of them must not
            // take ten times as long to arrive as three.
            val risen by animateFloatAsState(
                targetValue = if (shown) fraction.coerceAtLeast(MIN_COLUMN) else MIN_COLUMN,
                animationSpec = tween(RISE_MILLIS, delayMillis = SWEEP_MILLIS * index / values.size),
                label = "column",
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(risen)
                    .background(MaterialTheme.colorScheme.onSurface),
            )
        }
    }
}
