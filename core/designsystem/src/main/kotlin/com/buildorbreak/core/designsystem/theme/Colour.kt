package com.buildorbreak.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Concrete and signal red.
 *
 * The palette is a printed schedule board: a cool, almost neutral grey ground,
 * near black ink, and one vermilion that means "now". Nothing is warm and
 * nothing is soft. Every surface is flat, every edge is square, and the only
 * colour on the screen is the one thing the day has reached.
 *
 * **One accent, one warning.** The accent marks what is next and what is
 * primary. There is no second brand colour; a "problem" is the same red on a
 * tint, and a settled step is simply greyer than a pending one. A timeline
 * where six things are coloured is a timeline where none of them stand out.
 *
 * Light is the default. `themes.xml` in :app paints the same ground on the
 * launch window so the first frame is already the app's own colour.
 */
internal object Light {
    val Ground = Color(0xFFF3F2F2)
    val Raised = Color(0xFFEAE9E9)
    val Badge = Color(0xFFDDD9D9)

    val Ink = Color(0xFF201E1D)
    val Muted = Color(0xFF605D5D)
    val Faint = Color(0xFF9B9797)

    val Accent = Color(0xFFEC3013)
    val Pressed = Color(0xFFDD2B0F)
    val Tint = Color(0xFFFFE0D9)
    val OnTint = Color(0xFFAE1800)

    val Rail = Color(0xFFD7D3D3)
    val Hairline = Color(0x24201E1D)
}

/**
 * The same board with the lights off.
 *
 * Not the light palette inverted. The ground keeps a trace of warmth so the
 * accent does not read as orange, and the accent is lifted a step because a
 * dark surface needs more luminance from a colour to reach the same contrast.
 */
internal object Dark {
    val Ground = Color(0xFF141312)
    val Raised = Color(0xFF1F1D1C)
    val Badge = Color(0xFF2D2B2B)

    val Ink = Color(0xFFF3F2F2)
    val Muted = Color(0xFFBAB6B6)
    val Faint = Color(0xFF7D7979)

    val Accent = Color(0xFFFF563C)
    val Pressed = Color(0xFFFF9783)
    val Tint = Color(0xFF4D170E)
    val OnTint = Color(0xFFFFC4B8)

    val Rail = Color(0xFF3A3736)
    val Hairline = Color(0x24F3F2F2)
}

internal val LightScheme: ColorScheme = lightColorScheme(
    primary = Light.Accent,
    onPrimary = Color.White,
    primaryContainer = Light.Tint,
    onPrimaryContainer = Light.OnTint,
    background = Light.Ground,
    onBackground = Light.Ink,
    surface = Light.Ground,
    onSurface = Light.Ink,
    surfaceVariant = Light.Raised,
    onSurfaceVariant = Light.Muted,
    surfaceContainer = Light.Raised,
    surfaceContainerHigh = Light.Raised,
    // Borders are ink, and heavy. A two pixel rule in the text colour is what
    // makes a box on this ground read as printed rather than drawn.
    outline = Light.Ink,
    outlineVariant = Light.Hairline,
    error = Light.Accent,
    onError = Color.White,
    errorContainer = Light.Tint,
    onErrorContainer = Light.OnTint,
)

internal val DarkScheme: ColorScheme = darkColorScheme(
    primary = Dark.Accent,
    onPrimary = Dark.Ground,
    primaryContainer = Dark.Tint,
    onPrimaryContainer = Dark.OnTint,
    background = Dark.Ground,
    onBackground = Dark.Ink,
    surface = Dark.Ground,
    onSurface = Dark.Ink,
    surfaceVariant = Dark.Raised,
    onSurfaceVariant = Dark.Muted,
    surfaceContainer = Dark.Raised,
    surfaceContainerHigh = Dark.Raised,
    outline = Dark.Ink,
    outlineVariant = Dark.Hairline,
    error = Dark.Accent,
    onError = Dark.Ground,
    errorContainer = Dark.Tint,
    onErrorContainer = Dark.OnTint,
)

/**
 * The colours Material's scheme has no slot for.
 *
 * Kept as their own token set rather than bent into an unused Material role.
 * Putting the timeline rail in `tertiary` because it happened to be free is how
 * a palette becomes impossible to reason about six months later.
 */
data class BuildOrBreakColours(
    /** Settled, done, out of the way. */
    val faint: Color,
    /** A one pixel rule between rows. */
    val hairline: Color,
    /** The vertical line the day hangs off. */
    val rail: Color,
    /** The ground of a neutral badge. */
    val badge: Color,
    /** A pressed primary surface. */
    val pressed: Color,
    /** A raised panel behind a card. */
    val raised: Color,
)

internal val LightExtras = BuildOrBreakColours(
    faint = Light.Faint,
    hairline = Light.Hairline,
    rail = Light.Rail,
    badge = Light.Badge,
    pressed = Light.Pressed,
    raised = Light.Raised,
)

internal val DarkExtras = BuildOrBreakColours(
    faint = Dark.Faint,
    hairline = Dark.Hairline,
    rail = Dark.Rail,
    badge = Dark.Badge,
    pressed = Dark.Pressed,
    raised = Dark.Raised,
)
