package com.buildorbreak.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.util.Locale

private val NavIconSize = 20.dp

/** One destination on the bar. The label is natural case; the bar sets it. */
@Immutable
data class NavDestination(val label: String, val icon: ImageVector)

/**
 * How a test finds one tab, given its label.
 *
 * A tag rather than the label itself, because the label is a word the app also
 * uses in headings and body copy. A walkthrough looking for the text "Plan"
 * found "My routine plan" in the header of the screen it was already on, tapped
 * that, and passed for weeks without ever opening the tab it was written to
 * test. A tag cannot be matched by accident.
 */
fun navTag(label: String): String = "nav:$label"

/**
 * Four cells across the bottom, one of them filled.
 *
 * The selected cell is a solid block of accent with the label in the ground
 * colour, which is louder than Material's pill and is meant to be. Somebody
 * glancing at the phone should know which screen they are on from across the
 * room. The bar sits on the ground colour with a heavy rule above it and pads
 * itself for the gesture area.
 */
@Composable
fun BottomNavBar(
    destinations: List<NavDestination>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        HeavyRule()

        Row(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
            destinations.forEachIndexed { index, destination ->
                NavCell(
                    destination = destination,
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                )
            }
        }
    }
}

@Composable
private fun RowScope.NavCell(destination: NavDestination, selected: Boolean, onClick: () -> Unit) {
    val feedback = rememberFeedback()
    val ground by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        label = "cell",
    )
    val ink = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .weight(1f)
            .background(ground)
            .clickable(role = Role.Tab) {
                feedback.tap()
                onClick()
            }
            // Merged so a screen reader announces the cell once, as a tab, and
            // says whether it is the one currently open. Without `selected` the
            // bar reads as four identical buttons.
            .semantics(mergeDescendants = true) { this.selected = selected }
            .testTag(navTag(destination.label))
            .padding(top = 9.dp, bottom = 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(NavIconSize),
        )

        Text(
            text = destination.label.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            color = ink,
        )
    }
}
