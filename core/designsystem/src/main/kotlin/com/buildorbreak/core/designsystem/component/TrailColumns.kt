package com.buildorbreak.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A column is never invisible. The lowest value is still a value somebody logged. */
private const val MIN_COLUMN = 0.015f

private val ColumnGap = 3.dp

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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(ColumnGap),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEach { value ->
            val fraction = ((value - bottom) / span).toFloat().coerceIn(0f, 1f)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(fraction.coerceAtLeast(MIN_COLUMN))
                    .background(MaterialTheme.colorScheme.onSurface),
            )
        }
    }
}
