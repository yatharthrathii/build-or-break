package com.buildorbreak.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.buildorbreak.core.designsystem.R
import com.buildorbreak.core.designsystem.theme.Theme
import com.buildorbreak.core.designsystem.theme.TimeStyle
import com.buildorbreak.core.model.enums.Salience

/** How loudly a row announces itself, expressed without using colour. */
private val DotSize = 7.dp
private val AlarmDotSize = 9.dp
private val TimeColumnWidth = 52.dp

/**
 * One line of the day.
 *
 * The layout is a fixed time column, a marker, and the title. That order matters
 * more than it looks: the eye runs down the times, and a title that started at a
 * different x on every row would make the column unreadable, which is why the
 * time width is fixed rather than wrapped.
 *
 * **Salience is shown by weight, not by colour.** An alarm gets a filled dot, a
 * reminder an outlined one, a quiet item a small faint one. Colour is left alone
 * so the single rust element on the screen keeps meaning something.
 */
@Composable
fun TimelineRow(
    time: String,
    title: String,
    salience: Salience,
    modifier: Modifier = Modifier,
    detail: String? = null,
    isDone: Boolean = false,
    isMissed: Boolean = false,
    isNext: Boolean = false,
    isPinned: Boolean = false,
    isDegraded: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val spacing = Theme.spacing
    val settled = isDone || isMissed

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            // The next thing to happen is lifted rather than tinted. On a list
            // where everything else is flat, a slightly raised surface is enough
            // to find without adding another colour to the screen.
            .background(if (isNext) Theme.colours.lifted else Color.Transparent)
            .padding(horizontal = spacing.medium, vertical = spacing.small),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = time,
            style = TimeStyle,
            color = timeColour(settled, isNext),
            modifier = Modifier.width(TimeColumnWidth),
        )

        Box(
            modifier = Modifier
                .padding(horizontal = spacing.small)
                // Nudged down so the dot sits on the first line of the title
                // rather than above it when a row wraps to two lines.
                .padding(top = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            SalienceDot(salience = salience, muted = settled)
        }

        RowBody(
            title = title,
            detail = detail,
            isDone = isDone,
            isMissed = isMissed,
            isPinned = isPinned,
            isDegraded = isDegraded,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RowBody(
    title: String,
    detail: String?,
    isDone: Boolean,
    isMissed: Boolean,
    isPinned: Boolean,
    isDegraded: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.tight),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = titleColour(isDone, isMissed),
            // A completed item is struck through rather than removed. The day is
            // a record of what happened, and a list that empties as it goes gives
            // back no sense of a morning actually done.
            textDecoration = if (isDone) TextDecoration.LineThrough else null,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        detail?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = Theme.colours.faint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        RowNotes(isPinned = isPinned, isDegraded = isDegraded)
    }
}

/**
 * The two things about a row a user may need to be told.
 *
 * Both are rare, which is the point of putting them here rather than in the
 * title: a note that appears on every row is noise, and one that appears twice a
 * month is information.
 */
@Composable
private fun RowNotes(isPinned: Boolean, isDegraded: Boolean) {
    if (!isPinned && !isDegraded) return

    val notes = buildList {
        if (isPinned) add(stringResource(R.string.row_note_pinned))
        if (isDegraded) add(stringResource(R.string.row_note_degraded))
    }

    Text(
        text = notes.joinToString(stringResource(R.string.row_note_separator)),
        style = MaterialTheme.typography.labelMedium,
        color = if (isDegraded) Theme.colours.warning else Theme.colours.faint,
    )
}

@Composable
private fun SalienceDot(salience: Salience, muted: Boolean) {
    val colour = when {
        muted -> Theme.colours.faint
        salience == Salience.ALARM -> MaterialTheme.colorScheme.primary
        salience == Salience.NOTIFY -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> Theme.colours.faint
    }

    Box(
        modifier = Modifier
            .size(if (salience == Salience.ALARM) AlarmDotSize else DotSize)
            .background(colour, CircleShape),
    )
}

@Composable
private fun timeColour(settled: Boolean, isNext: Boolean) = when {
    settled -> Theme.colours.faint
    isNext -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun titleColour(isDone: Boolean, isMissed: Boolean) = when {
    isDone -> Theme.colours.faint
    isMissed -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.onSurface
}
