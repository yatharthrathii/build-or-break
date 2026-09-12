package com.buildorbreak.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.buildorbreak.core.designsystem.theme.Theme
import java.util.Locale

private val ToggleWidth = 46.dp
private val ToggleHeight = 26.dp
private val ToggleKnob = 18.dp
private val ToggleInset = 2.dp
private val StepperCell = 40.dp
private val FieldHeight = 44.dp

/**
 * Joined boxes, one selected: WEEKDAY | WEEKEND, FIXED | RELATIVE | WINDOW.
 *
 * The selected cell is filled in ink, or in accent when [accent] is true, and
 * the rest share a single two pixel border. It reads as one control with one
 * answer, which a row of chips does not.
 */
@Composable
fun SegmentedTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    stretch: Boolean = false,
) {
    Row(modifier = modifier.border(Theme.spacing.rule, MaterialTheme.colorScheme.onSurface)) {
        options.forEachIndexed { index, option ->
            Segment(
                text = option,
                selected = index == selectedIndex,
                accent = accent,
                onClick = { onSelect(index) },
                modifier = if (stretch) Modifier.weight(1f) else Modifier,
            )

            if (index < options.lastIndex) {
                Box(
                    modifier = Modifier
                        .width(Theme.spacing.rule)
                        .height(FieldHeight - Theme.spacing.small)
                        .background(MaterialTheme.colorScheme.onSurface),
                )
            }
        }
    }
}

@Composable
private fun Segment(
    text: String,
    selected: Boolean,
    accent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val feedback = rememberFeedback()

    val ground by animateColorAsState(
        when {
            selected && accent -> MaterialTheme.colorScheme.primary
            selected -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.surface
        },
        label = "segment",
    )
    val ink = when {
        selected && accent -> MaterialTheme.colorScheme.onPrimary
        selected -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Text(
        text = text.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelMedium,
        color = ink,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = modifier
            .background(ground)
            .clickable(role = Role.Tab) {
                feedback.tick()
                onClick()
            }
            .padding(horizontal = Theme.spacing.inset, vertical = Theme.spacing.inset),
    )
}

/**
 * A square switch.
 *
 * A bordered track, a square knob, and a spring when it moves. Material's pill
 * switch would be the one rounded thing on the screen.
 */
@Composable
fun SquareToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val travel = ToggleWidth - ToggleKnob - ToggleInset * 2 - Theme.spacing.rule * 2
    val offset by animateDpAsState(if (checked) travel else 0.dp, spring(), label = "knob")
    val track by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        label = "track",
    )
    val knob = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .size(ToggleWidth, ToggleHeight)
            .border(Theme.spacing.rule, MaterialTheme.colorScheme.onSurface)
            .background(track)
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(Theme.spacing.rule + ToggleInset),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                // The lambda overload, so the spring drives layout without a
                // recomposition per frame.
                .offset { IntOffset(offset.roundToPx(), 0) }
                .size(ToggleKnob)
                .background(knob),
        )
    }
}

/**
 * A number and two buttons: minus, value, plus.
 *
 * For offsets and durations, where typing is slower than tapping and a wrong
 * digit is an alarm at the wrong hour.
 */
@Composable
fun Stepper(
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .border(Theme.spacing.rule, MaterialTheme.colorScheme.onSurface)
            .height(FieldHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton(text = "−", onClick = onDecrement)

        Box(
            modifier = Modifier
                .width(Theme.spacing.rule)
                .height(FieldHeight)
                .background(MaterialTheme.colorScheme.onSurface),
        )

        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )

        Box(
            modifier = Modifier
                .width(Theme.spacing.rule)
                .height(FieldHeight)
                .background(MaterialTheme.colorScheme.onSurface),
        )

        StepperButton(text = "+", onClick = onIncrement)
    }
}

@Composable
private fun StepperButton(text: String, onClick: () -> Unit) {
    val feedback = rememberFeedback()

    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .width(StepperCell)
            .height(FieldHeight)
            .clickable(role = Role.Button) {
                feedback.tick()
                onClick()
            }
            .padding(top = 10.dp),
    )
}

/**
 * A bordered value that opens something when tapped: a time, a parent step.
 *
 * [label] sits above in a kicker; [value] is the thing itself. Tapping the
 * whole box is the affordance, so there is no separate button to find.
 */
@Composable
fun PickerField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier = modifier) {
        Kicker(text = label)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Theme.spacing.small)
                .border(Theme.spacing.rule, MaterialTheme.colorScheme.onSurface)
                .clickable(role = Role.Button, onClick = onClick)
                .height(FieldHeight)
                .padding(horizontal = Theme.spacing.inset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )

            trailing()
        }
    }
}

/**
 * The little bars at the bottom of a first run screen: two of three done.
 *
 * Structure that encodes something true. There are exactly three steps, and
 * the bars say which one this is without a word.
 */
@Composable
fun StepBars(total: Int, completed: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        repeat(total) { index ->
            val filled = index < completed
            val colour by animateColorAsState(
                if (filled) MaterialTheme.colorScheme.primary else Theme.colours.rail,
                label = "bar",
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = if (index < total - 1) 5.dp else 0.dp)
                    .height(4.dp)
                    .background(colour),
            )
        }
    }
}
