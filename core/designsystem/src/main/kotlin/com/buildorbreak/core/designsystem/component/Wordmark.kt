package com.buildorbreak.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.buildorbreak.core.designsystem.theme.WordmarkStyle

private val MarkSize = 20.dp

/**
 * BUILD/BREAK, with the slash in accent and a square beside it.
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
        Box(
            modifier = Modifier
                .size(MarkSize)
                .background(MaterialTheme.colorScheme.primary),
        )

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
