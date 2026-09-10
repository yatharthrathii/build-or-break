package com.buildorbreak.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.buildorbreak.core.designsystem.theme.BlockLabelStyle
import com.buildorbreak.core.designsystem.theme.Theme
import java.util.Locale

private val BlockHeight = 56.dp
private val BlockIconSize = 20.dp
private const val PRESSED_SCALE = 0.96f

/** Whether a finger is on this control right now. */
@Composable
private fun MutableInteractionSource.isPressed(): Boolean {
    val pressed by collectIsPressedAsState()
    return pressed
}

/**
 * The primary action: a full width block of accent with a black label.
 *
 * One per screen at most. It is the loudest thing this design has, and two of
 * them side by side would be two things shouting. Pressing it scales the whole
 * block down a fraction on a spring, which is the one piece of motion that
 * every tap in the app shares.
 */
@Composable
fun BlockButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val feedback = rememberFeedback()
    val interaction = remember { MutableInteractionSource() }
    val pressed = interaction.isPressed() && enabled
    val scale by animateFloatAsState(if (pressed) PRESSED_SCALE else 1f, spring(), label = "press")
    val ground by animateColorAsState(blockGround(enabled, pressed), spring(stiffness = 1200f), label = "ground")
    val ink = when {
        !enabled -> Theme.colours.faint
        pressed -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.onPrimary
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(BlockHeight)
            .scale(scale)
            .background(ground)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    // The primary action is the one somebody commits to, so it
                    // gets the heavier of the two ticks.
                    feedback.confirm()
                    onClick()
                },
            )
            .padding(horizontal = Theme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = text.uppercase(Locale.getDefault()), style = BlockLabelStyle, color = ink)

        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = ink, modifier = Modifier.size(BlockIconSize))
        }
    }
}

/**
 * Pressed goes to ink, not to a darker red. A darker red is invisible on a
 * phone in daylight; ink on the accent block is unmistakable.
 */
@Composable
private fun blockGround(enabled: Boolean, pressed: Boolean): Color = when {
    !enabled -> Theme.colours.badge
    pressed -> MaterialTheme.colorScheme.onSurface
    else -> MaterialTheme.colorScheme.primary
}

/**
 * A small bordered action: UNDO, NOT NOW, RUNNING LATE.
 *
 * Two pixel border in ink, no fill. [muted] draws it in the secondary colour
 * for the option somebody is allowed to ignore.
 */
@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    enabled: Boolean = true,
) {
    val feedback = rememberFeedback()
    val interaction = remember { MutableInteractionSource() }
    val pressed = interaction.isPressed() && enabled

    val ink = when {
        !enabled -> Theme.colours.faint
        pressed -> MaterialTheme.colorScheme.surface
        muted -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    val border = if (muted || !enabled) Theme.colours.faint else MaterialTheme.colorScheme.onSurface

    Text(
        text = text.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelMedium,
        color = ink,
        modifier = modifier
            .border(Theme.spacing.rule, border)
            // Filled in ink while pressed. The outline is the quiet control,
            // so its press has to be the loud moment.
            .background(if (pressed) MaterialTheme.colorScheme.onSurface else Color.Transparent)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    feedback.tap()
                    onClick()
                },
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

/** A small filled action: ALLOW, APPLY, OPEN SETTINGS. */
@Composable
fun FillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val feedback = rememberFeedback()
    val interaction = remember { MutableInteractionSource() }
    val pressed = interaction.isPressed() && enabled

    val ground = when {
        !enabled -> Theme.colours.badge
        pressed -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.primary
    }
    val ink = when {
        !enabled -> Theme.colours.faint
        pressed -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.onPrimary
    }

    Text(
        text = text.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelMedium,
        color = ink,
        modifier = modifier
            .background(ground)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    feedback.tap()
                    onClick()
                },
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

/** A label that is also a button, with nothing drawn around it. */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val feedback = rememberFeedback()

    Text(
        text = text.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = modifier
            .clickable(role = Role.Button) {
                feedback.tap()
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 12.dp),
    )
}
