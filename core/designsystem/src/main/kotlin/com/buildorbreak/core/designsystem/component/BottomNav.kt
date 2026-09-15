package com.buildorbreak.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buildorbreak.core.designsystem.theme.Theme
import java.util.Locale

private val NavIconSize = 20.dp

/** A rail cell is a square-ish block a thumb can find without looking. */
private val RailWidth = 88.dp
private val RailCellHeight = 80.dp

/** Above this font scale the labels lose their tracking to stay on one line. */
private const val LARGE_FONT = 1.3f

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

/**
 * The same four cells, stood on end down the left edge.
 *
 * For a tablet or a phone on its side. A bar across the bottom of a screen
 * twelve hundred pixels wide is four cells each wider than a phone, with
 * the label lost in the middle of each; a rail keeps every cell the size
 * of a thumb and leaves the width to the page.
 */
@Composable
fun NavRail(
    destinations: List<NavDestination>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxHeight().background(MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start))
                .width(RailWidth),
        ) {
            destinations.forEachIndexed { index, destination ->
                NavCell(
                    destination = destination,
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    modifier = Modifier.fillMaxWidth().height(RailCellHeight),
                )
            }
        }

        // The heavy rule, stood on end.
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(Theme.spacing.rule)
                .background(MaterialTheme.colorScheme.onSurface),
        )
    }
}

@Composable
private fun RowScope.NavCell(destination: NavDestination, selected: Boolean, onClick: () -> Unit) {
    NavCell(destination = destination, selected = selected, onClick = onClick, modifier = Modifier.weight(1f))
}

@Composable
private fun NavCell(
    destination: NavDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val feedback = rememberFeedback()
    val ground by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        label = "cell",
    )
    val ink = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
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
            .padding(top = Theme.spacing.small, bottom = Theme.spacing.inset),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(NavIconSize),
        )

        // One line, always. At a large font size the tracked capitals do
        // not fit four across, and a label split mid word ("INSIGHT / S")
        // is worse than one set tight. The tracking goes first, then the
        // end of the word.
        val large = LocalDensity.current.fontScale > LARGE_FONT
        val style = MaterialTheme.typography.labelSmall

        Text(
            text = destination.label.uppercase(Locale.getDefault()),
            style = if (large) style.copy(letterSpacing = 0.sp) else style,
            color = ink,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
