package com.buildorbreak.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.buildorbreak.core.designsystem.theme.WordmarkStyle

private val MarkSize = 20.dp

/** The cut through the block, as a share of its side. The launcher icon uses the same. */
private const val CUT = 0.13f

/**
 * BUILD/BREAK, with the slash in accent and the mark beside it.
 *
 * Text, not an image, so it takes the theme's colours and scales with the
 * user's font size like everything else. The slash is the whole identity: the
 * app is about the line between the two, and one red character says so.
 */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Mark(colour = MaterialTheme.colorScheme.primary)

        Text(
            text = buildAnnotatedString {
                append("BUILD")
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("/") }
                append("BREAK")
            },
            style = WordmarkStyle,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * The mark: a block with one slash cut clean through it.
 *
 * The same two triangles as the launcher icon, drawn here so the wordmark
 * on the first screen and the icon that opened it are visibly one thing.
 * The cut is left unpainted, so whatever ground the mark sits on shows
 * through it.
 */
@Composable
fun Mark(colour: Color, modifier: Modifier = Modifier, size: Dp = MarkSize) {
    Canvas(modifier = modifier.size(size)) {
        val side = this.size.width
        val cut = side * CUT

        drawPath(
            path = Path().apply {
                moveTo(0f, 0f)
                lineTo(side - cut, 0f)
                lineTo(0f, side - cut)
                close()
            },
            color = colour,
        )

        drawPath(
            path = Path().apply {
                moveTo(side, side)
                lineTo(cut, side)
                lineTo(side, cut)
                close()
            },
            color = colour,
        )
    }
}
