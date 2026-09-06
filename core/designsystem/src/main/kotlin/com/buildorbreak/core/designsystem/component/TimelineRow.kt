package com.buildorbreak.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.buildorbreak.core.designsystem.theme.Theme
import com.buildorbreak.core.designsystem.theme.TimeStyle

private val TimeColumnWidth = 46.dp
private val RailColumnWidth = 18.dp
private val RailWidth = 2.dp
private val MarkerSize = 12.dp
private val MarkerTop = 14.dp

/** Where a row sits in the day. Decides its colour and its marker. */
enum class RowState {
    /** Settled and done. Greyed, marker filled grey. */
    DONE,

    /** Settled and not done. Greyed, marker hollow. */
    MISSED,

    /** The one the day has reached. Accent time and marker. */
    NEXT,

    /** Still to come. Ink, hollow marker. */
    UPCOMING,
}

/**
 * One line of the day.
 *
 * A fixed time column, a rail with a square marker, and the body. The rail is
 * continuous down the list, which is what turns a list of rows into a timeline:
 * the eye runs down the line and the markers sit on it like stations.
 *
 * **State is shown by weight and position, not by colour, except for one row.**
 * The next thing to happen gets the accent on its time and its marker. Done
 * rows go grey and keep their place, because the day is a record of what
 * happened and a list that empties as it goes gives back no sense of a morning
 * actually done.
 */
@Composable
fun TimelineRow(
    time: String,
    title: String,
    state: RowState,
    modifier: Modifier = Modifier,
    badge: String? = null,
    note: String? = null,
    noteAccent: Boolean = false,
    last: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val settled = state == RowState.DONE || state == RowState.MISSED

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            // Intrinsic height so the rail can fill the row: without it a
            // fillMaxHeight child of a Row measures as zero.
            .height(IntrinsicSize.Min),
    ) {
        Text(
            text = time,
            style = TimeStyle,
            color = timeColour(state),
            textAlign = TextAlign.End,
            modifier = Modifier
                .width(TimeColumnWidth)
                .padding(top = 13.dp, end = Theme.spacing.small),
        )

        Rail(state = state)

        RowBody(
            title = title,
            settled = settled,
            badge = badge,
            note = note,
            accentBadge = state == RowState.NEXT,
            noteAccent = noteAccent,
            last = last,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RowBody(
    title: String,
    settled: Boolean,
    badge: String?,
    note: String?,
    accentBadge: Boolean,
    noteAccent: Boolean,
    last: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(start = 12.dp, top = 11.dp, bottom = 13.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = if (settled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        RowMeta(badge = badge, note = note, accentBadge = accentBadge, noteAccent = noteAccent)

        if (!last) {
            HairlineRule(Modifier.padding(top = 13.dp))
        }
    }
}

/** The vertical line and the marker that sits on it. */
@Composable
private fun Rail(state: RowState) {
    val marker by animateColorAsState(markerFill(state), label = "marker")
    val border = markerBorder(state)

    Box(
        modifier = Modifier
            .width(RailColumnWidth)
            .fillMaxHeight(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .width(RailWidth)
                .fillMaxHeight()
                .background(Theme.colours.rail),
        )

        Box(
            modifier = Modifier
                .padding(top = MarkerTop)
                .size(MarkerSize)
                .background(marker)
                .border(Theme.spacing.rule, border),
        )
    }
}

@Composable
private fun RowMeta(
    badge: String?,
    note: String?,
    accentBadge: Boolean,
    noteAccent: Boolean,
) {
    if (badge == null && note == null) return

    Row(
        modifier = Modifier.padding(top = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (badge != null) {
            Badge(text = badge, accent = accentBadge)
        }

        if (note != null) {
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = if (noteAccent) MaterialTheme.colorScheme.onPrimaryContainer else Theme.colours.faint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun timeColour(state: RowState): Color = when (state) {
    RowState.DONE, RowState.MISSED -> Theme.colours.faint
    RowState.NEXT -> MaterialTheme.colorScheme.primary
    RowState.UPCOMING -> MaterialTheme.colorScheme.onSurface
}

@Composable
private fun markerFill(state: RowState): Color = when (state) {
    RowState.DONE -> Theme.colours.faint
    RowState.MISSED -> MaterialTheme.colorScheme.surface
    RowState.NEXT -> MaterialTheme.colorScheme.primary
    RowState.UPCOMING -> MaterialTheme.colorScheme.surface
}

@Composable
private fun markerBorder(state: RowState): Color = when (state) {
    RowState.NEXT -> MaterialTheme.colorScheme.primary
    else -> Theme.colours.faint
}
