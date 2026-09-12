package com.buildorbreak.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.buildorbreak.core.designsystem.theme.Theme
import java.util.Locale

/**
 * A tracked, uppercase label.
 *
 * The uppercase happens here rather than in the string resource, so a
 * translator writes "Next up" and the design decides how it is set. A resource
 * file full of shouting is a resource file nobody can reuse in a sentence.
 */
@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text = text.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The smaller label that sits above a title or inside a button. */
@Composable
fun Kicker(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    /** Two where the line is a sentence about a step rather than a label. */
    maxLines: Int = 1,
) {
    Text(
        text = text.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = modifier,
        maxLines = maxLines,
        // Tracked capitals need the room back between the lines that the
        // label line height takes out, or two lines touch.
        lineHeight = MaterialTheme.typography.labelMedium.fontSize * LINE_GAP,
        overflow = TextOverflow.Ellipsis,
    )
}

/** A tracked capital is wider than it is tall; its lines need more air, not less. */
private const val LINE_GAP = 1.45f

/**
 * A square badge: FIXED, WINDOW, +15 AFTER GYM.
 *
 * The badge is how a row says what kind of time it keeps without a sentence.
 * Accent means "this is the one the day has reached"; everything else is a
 * neutral block that reads at a glance and disappears when it is not needed.
 */
@Composable
fun Badge(text: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    val ground = if (accent) MaterialTheme.colorScheme.primary else Theme.colours.badge
    val ink = if (accent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Text(
        text = text.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelSmall,
        color = ink,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(ground)
            .padding(horizontal = 5.dp, vertical = 4.dp),
    )
}
