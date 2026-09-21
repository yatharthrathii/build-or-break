package com.buildorbreak.app.feature.goal

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.buildorbreak.app.R
import com.buildorbreak.core.designsystem.component.FillButton
import com.buildorbreak.core.designsystem.component.GhostButton
import com.buildorbreak.core.designsystem.component.Kicker
import com.buildorbreak.core.designsystem.component.Panel
import com.buildorbreak.core.designsystem.theme.Theme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private const val DAYS_IN_WEEK = 7

/** The most a month can spread over. Always drawn, so the dialog never changes height. */
private const val WEEKS_DRAWN = 6

private const val TURN_MILLIS = 200
private const val TURN_FRACTION = 6
private val CellHeight = 40.dp

/** The days the dialog will let through, both ends included. */
internal data class DateBounds(val earliest: LocalDate, val latest: LocalDate) {
    operator fun contains(date: LocalDate): Boolean = date in earliest..latest
}

/**
 * One month at a time, squares not circles, Monday first.
 *
 * Written here rather than borrowed from Material, whose picker arrives with
 * round day markers and its own colours and looks like a different app has
 * opened on top of this one. A month grid is forty two boxes, and owning
 * them is cheaper than restyling a borrowed one.
 *
 * Days outside [bounds] are drawn and cannot be tapped. Hiding them would
 * make the grid change shape from month to month for no reason a user could
 * see.
 */
@Composable
internal fun GoalDateDialog(
    initial: LocalDate,
    bounds: DateBounds,
    onDismiss: () -> Unit,
    onPicked: (LocalDate) -> Unit,
) {
    var picked by remember { mutableStateOf(initial.coerceIn(bounds.earliest, bounds.latest)) }
    var month by remember { mutableStateOf(YearMonth.from(picked)) }

    Dialog(onDismissRequest = onDismiss) {
        Panel {
            Column(modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)) {
                Kicker(
                    text = picked.format(longDate()),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                MonthBar(
                    month = month,
                    canGoBack = month > YearMonth.from(bounds.earliest),
                    canGoOn = month < YearMonth.from(bounds.latest),
                    onMove = { month = month.plusMonths(it) },
                )

                WeekdayRow()

                // Later months come in from the right and earlier ones from
                // the left, the way the arrows point. Six rows are always
                // kept, so a five week month does not make the buttons jump.
                AnimatedContent(
                    targetState = month,
                    transitionSpec = {
                        val forward = targetState > initialState
                        (
                            slideInHorizontally(tween(TURN_MILLIS)) {
                                if (forward) it / TURN_FRACTION else -it / TURN_FRACTION
                            } +
                                fadeIn(tween(TURN_MILLIS))
                            ) togetherWith fadeOut(tween(TURN_MILLIS / 2))
                    },
                    label = "month",
                ) { shown ->
                    MonthGrid(month = shown, picked = picked, bounds = bounds, onPick = { picked = it })
                }

                DialogActions(onDismiss = onDismiss, onSet = { onPicked(picked) })
            }
        }
    }
}

@Composable
private fun DialogActions(onDismiss: () -> Unit, onSet: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GhostButton(text = stringResource(R.string.editor_cancel), onClick = onDismiss)
        FillButton(text = stringResource(R.string.editor_set), onClick = onSet)
    }
}

@Composable
private fun MonthBar(
    month: YearMonth,
    canGoBack: Boolean,
    canGoOn: Boolean,
    onMove: (Long) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonthArrow(
            icon = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
            label = stringResource(R.string.goal_form_date_earlier),
            enabled = canGoBack,
            onClick = { onMove(-1) },
        )

        Text(
            text = month.format(monthTitle()),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )

        MonthArrow(
            icon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            label = stringResource(R.string.goal_form_date_later),
            enabled = canGoOn,
            onClick = { onMove(1) },
        )
    }
}

@Composable
private fun MonthArrow(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(CellHeight)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) MaterialTheme.colorScheme.onSurface else Theme.colours.faint,
        )
    }
}

/** M T W T F S S, in whatever language the phone is in. */
@Composable
private fun WeekdayRow() {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        DayOfWeek.entries.forEach { day ->
            Text(
                text = day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    picked: LocalDate,
    bounds: DateBounds,
    onPick: (LocalDate) -> Unit,
) {
    // Monday is one, so a month that starts on a Wednesday is pushed in by two.
    val lead = month.atDay(1).dayOfWeek.value - 1
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        repeat(WEEKS_DRAWN) { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(DAYS_IN_WEEK) { column ->
                    val dayOfMonth = row * DAYS_IN_WEEK + column - lead + 1
                    val date = dayOfMonth.takeIf { it in 1..month.lengthOfMonth() }?.let(month::atDay)

                    DayCell(
                        date = date,
                        chosen = date == picked,
                        enabled = date != null && date in bounds,
                        onPick = onPick,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate?,
    chosen: Boolean,
    enabled: Boolean,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = when {
        chosen -> MaterialTheme.colorScheme.onPrimary
        enabled -> MaterialTheme.colorScheme.onSurface
        else -> Theme.colours.faint
    }

    Box(
        modifier = modifier
            .height(CellHeight)
            .background(if (chosen) MaterialTheme.colorScheme.primary else Theme.colours.raised)
            .clickable(enabled = enabled, role = Role.RadioButton) { date?.let(onPick) },
        contentAlignment = Alignment.Center,
    ) {
        if (date != null) {
            Text(text = date.dayOfMonth.toString(), style = MaterialTheme.typography.titleSmall, color = ink)
        }
    }
}

/** Functions, not values: a value would keep the language the app started in. */
internal fun longDate(): DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())

private fun monthTitle(): DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
