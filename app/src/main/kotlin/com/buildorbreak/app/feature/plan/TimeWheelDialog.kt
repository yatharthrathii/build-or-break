package com.buildorbreak.app.feature.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.buildorbreak.app.R
import com.buildorbreak.app.format.rememberClockFormat
import com.buildorbreak.core.designsystem.component.FillButton
import com.buildorbreak.core.designsystem.component.GhostButton
import com.buildorbreak.core.designsystem.component.HairlineRule
import com.buildorbreak.core.designsystem.component.Kicker
import com.buildorbreak.core.designsystem.component.Panel
import com.buildorbreak.core.designsystem.component.rememberFeedback
import com.buildorbreak.core.designsystem.theme.Theme
import com.buildorbreak.core.designsystem.theme.TimeStyle
import java.time.LocalTime

private val RowHeight = 46.dp
private val WheelHeight = RowHeight * VISIBLE_ROWS
private const val VISIBLE_ROWS = 5
private const val EDGE_ROWS = 2
private const val NOON = 12
private const val MINUTES_IN_HOUR = 60
private const val HOURS_IN_DAY = 24

/**
 * Setting a time, in the app's own hand.
 *
 * The platform dial is a good control and the wrong one here. It is round in an
 * app with no round corners, it is drawn in a palette this app does not use, and
 * it asks for two taps and a drag to say something a list says with one flick.
 *
 * So: three columns that snap, a band of accent across the middle marking what
 * is chosen, and the numbers set in the same face as every other time in the
 * app. Each notch ticks under the finger, which is the part that makes a wheel
 * feel like a physical thing rather than a scrolling list.
 */
@Composable
internal fun TimeWheelDialog(initial: LocalTime, onDismiss: () -> Unit, onPicked: (LocalTime) -> Unit) {
    val clock = rememberClockFormat()
    val twelveHour = !clock.is24Hour

    val hours = remember(twelveHour) { if (twelveHour) (1..NOON).toList() else (0 until HOURS_IN_DAY).toList() }
    val minutes = remember { (0 until MINUTES_IN_HOUR).toList() }

    val hourState = rememberLazyListState(hours.indexOf(displayHour(initial.hour, twelveHour)).coerceAtLeast(0))
    val minuteState = rememberLazyListState(initial.minute)
    val afternoonState = rememberLazyListState(if (initial.hour >= NOON) 1 else 0)

    val hour by remember { derivedStateOf { hours.getOrElse(hourState.centred()) { initial.hour } } }
    val minute by remember { derivedStateOf { minutes.getOrElse(minuteState.centred()) { initial.minute } } }
    val afternoon by remember { derivedStateOf { afternoonState.centred() == 1 } }

    val picked = twentyFourHour(hour, afternoon, twelveHour, minute)

    Dialog(onDismissRequest = onDismiss) {
        Panel {
            Column(modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)) {
                Kicker(
                    text = clock.format(picked),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                Wheels(
                    hours = hours,
                    minutes = minutes,
                    hourState = hourState,
                    minuteState = minuteState,
                    afternoonState = afternoonState,
                    twelveHour = twelveHour,
                )

                HairlineRule()

                Footer(onDismiss = onDismiss, onSet = { onPicked(picked) })
            }
        }
    }
}

@Composable
private fun Footer(onDismiss: () -> Unit, onSet: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, start = 8.dp, end = 12.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GhostButton(text = stringResource(R.string.editor_cancel), onClick = onDismiss)
        FillButton(text = stringResource(R.string.editor_set), onClick = onSet)
    }
}

/**
 * The three columns, and the band that says which row counts.
 *
 * The band is drawn under the wheels rather than on them, so the numbers sit
 * inside it rather than behind a tint. Two rules and the tint are the same three
 * marks the timeline uses to say "this is the one".
 */
@Composable
private fun Wheels(
    hours: List<Int>,
    minutes: List<Int>,
    hourState: LazyListState,
    minuteState: LazyListState,
    afternoonState: LazyListState,
    twelveHour: Boolean,
) {
    Box(modifier = Modifier.height(WheelHeight), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(RowHeight)
                .background(MaterialTheme.colorScheme.primaryContainer),
        )

        Row(modifier = Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Wheel(values = hours, state = hourState, label = { it.toString().padStart(2, '0') })

            Text(
                text = ":",
                style = MaterialTheme.typography.displaySmall,
                color = Theme.colours.faint,
                modifier = Modifier.width(14.dp),
                textAlign = TextAlign.Center,
            )

            Wheel(values = minutes, state = minuteState, label = { it.toString().padStart(2, '0') })

            if (twelveHour) {
                Wheel(
                    values = listOf(0, 1),
                    state = afternoonState,
                    label = { if (it == 0) "AM" else "PM" },
                    wide = true,
                )
            }
        }
    }
}

/** One snapping column. Whatever sits in the middle is the answer. */
@Composable
private fun Wheel(
    values: List<Int>,
    state: LazyListState,
    label: (Int) -> String,
    wide: Boolean = false,
) {
    val feedback = rememberFeedback()
    val centre by remember { derivedStateOf { state.centred() } }

    // One tick per notch passed, which is what a wheel that turns should feel
    // like. Fired from the centred index rather than from the scroll, so a
    // fling ticks once per number rather than once per frame.
    LaunchedEffect(centre) { feedback.tick() }

    LazyColumn(
        state = state,
        flingBehavior = rememberSnapFlingBehavior(state),
        contentPadding = PaddingValues(vertical = RowHeight * EDGE_ROWS),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(if (wide) 64.dp else 56.dp)
            .height(WheelHeight),
    ) {
        items(items = values, key = { it }) { value ->
            val selected = values.indexOf(value) == centre

            Box(modifier = Modifier.height(RowHeight), contentAlignment = Alignment.Center) {
                Text(
                    text = label(value),
                    style = if (selected) MaterialTheme.typography.headlineMedium else TimeStyle,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else Theme.colours.faint,
                )
            }
        }
    }
}

/**
 * Which row is in the band.
 *
 * The list is padded by two rows at each end, so the first visible index is
 * already the centred one; the offset only decides which of two it is while a
 * scroll is in flight.
 */
private fun LazyListState.centred(): Int {
    val height = layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: return firstVisibleItemIndex

    return firstVisibleItemIndex + if (height > 0 && firstVisibleItemScrollOffset > height / 2) 1 else 0
}

private fun displayHour(hour: Int, twelveHour: Boolean): Int = when {
    !twelveHour -> hour
    hour % NOON == 0 -> NOON
    else -> hour % NOON
}

private fun twentyFourHour(
    hour: Int,
    afternoon: Boolean,
    twelveHour: Boolean,
    minute: Int,
): LocalTime {
    if (!twelveHour) return LocalTime.of(hour % HOURS_IN_DAY, minute)

    val base = if (hour == NOON) 0 else hour

    return LocalTime.of(if (afternoon) base + NOON else base, minute)
}
