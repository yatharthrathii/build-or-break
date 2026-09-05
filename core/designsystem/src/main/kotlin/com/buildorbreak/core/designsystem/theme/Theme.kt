package com.buildorbreak.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The spacing scale.
 *
 * Six steps, and nothing between them. A scale with a value for every occasion
 * is a scale nobody follows, and the result is a screen where two things are
 * eleven pixels apart and three others are twelve. Anything that needs a spacing
 * not on this list is usually a layout that needs rethinking.
 */
data class Spacing(
    val hairline: Dp = 1.dp,
    val tight: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val section: Dp = 32.dp,
)

private val LocalSpacing = staticCompositionLocalOf { Spacing() }

private val LocalColours = staticCompositionLocalOf { LightExtras }

/**
 * Everything Compose draws sits inside this.
 *
 * **No dynamic colour.** Material You would take the palette from the user's
 * wallpaper, and this app's whole visual argument is that it is calm, warm and
 * the same every morning. A routine app that is lilac this week because the
 * wallpaper changed has given up the one thing that made it feel steady.
 */
@Composable
fun BuildOrBreakTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    val extras = if (darkTheme) DarkExtras else LightExtras

    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalColours provides extras,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = BuildOrBreakTypography,
            content = content,
        )
    }
}

/**
 * The tokens Material has no slot for, reached the same way as everything else.
 *
 * `MaterialTheme.colorScheme` and `Theme.colours` sitting side by side is
 * deliberate: a reader can tell at a glance which values are Material's and
 * which are this app's.
 */
object Theme {
    val spacing: Spacing
        @Composable @ReadOnlyComposable
        get() = LocalSpacing.current

    val colours: BuildOrBreakColours
        @Composable @ReadOnlyComposable
        get() = LocalColours.current
}
