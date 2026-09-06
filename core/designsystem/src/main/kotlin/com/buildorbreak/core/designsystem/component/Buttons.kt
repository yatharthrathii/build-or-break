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
private const val PRESSED_SCALE = 0.97f

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
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) PRESSED_SCALE else 1f, spring(), label = "press")

    val ground by animateColorAsState(
        when {
            !enabled -> Theme.colours.badge
            pressed -> Theme.colours.pressed
            else -> MaterialTheme.colorScheme.primary
        },
        label = "ground",
    )
    val ink = if (enabled) MaterialTheme.colorScheme.onPrimary else Theme.colours.faint

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
                onClick = onClick,
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
    val ink = when {
        !enabled -> Theme.colours.faint
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
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
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
    val ground = if (enabled) MaterialTheme.colorScheme.primary else Theme.colours.badge
    val ink = if (enabled) MaterialTheme.colorScheme.onPrimary else Theme.colours.faint

    Text(
        text = text.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelMedium,
        color = ink,
        modifier = modifier
            .background(ground)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
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
    Text(
        text = text.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = modifier
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    )
}
