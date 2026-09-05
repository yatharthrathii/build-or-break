package com.buildorbreak.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Paper and rust.
 *
 * The palette is warm and quiet on purpose. This app is looked at first thing in
 * the morning and last thing at night, often half awake, and a bright white
 * surface at six in the morning is hostile. Paper is off white with a little
 * warmth in it, ink is not quite black, and the single accent is a rust that
 * carries the small amount of urgency the app ever needs.
 *
 * **One accent, used sparingly.** Everything that matters on the Today screen is
 * conveyed by time, weight and position rather than by colour, so that the one
 * rust element on screen is genuinely the thing to look at. A timeline where six
 * things are coloured is a timeline where none of them stand out.
 *
 * Light is the default. design.md section 2, and the launch window in
 * `themes.xml` already paints the paper surface so the very first frame is the
 * app's own colour rather than a white flash.
 */
internal object Paper {
    val Surface = Color(0xFFF7F4EF)
    val SurfaceDim = Color(0xFFEFEBE3)
    val SurfaceLifted = Color(0xFFFFFDF9)

    val Ink = Color(0xFF1C1917)
    val InkMuted = Color(0xFF6B635A)
    val InkFaint = Color(0xFF9C938A)

    val Rust = Color(0xFFA8452B)
    val RustSoft = Color(0xFFF0E0D9)

    val Line = Color(0xFFDFD8CC)
    val Warning = Color(0xFF8A5A00)
}

/**
 * The same room with the light off.
 *
 * Not the light palette inverted. A true black background next to a warm accent
 * reads as cold and makes the rust look orange, so the dark surface keeps the
 * warmth and the accent is lifted rather than kept, because a dark surface needs
 * more luminance from an accent to reach the same contrast.
 */
internal object Ink {
    val Surface = Color(0xFF17150F)
    val SurfaceDim = Color(0xFF100E0A)
    val SurfaceLifted = Color(0xFF221F18)

    val Paper = Color(0xFFEDE8DF)
    val PaperMuted = Color(0xFFA79E92)
    val PaperFaint = Color(0xFF7A7268)

    val Rust = Color(0xFFE0734F)
    val RustSoft = Color(0xFF3A241C)

    val Line = Color(0xFF322D25)
    val Warning = Color(0xFFD9A441)
}

internal val LightScheme: ColorScheme = lightColorScheme(
    primary = Paper.Rust,
    onPrimary = Color.White,
    primaryContainer = Paper.RustSoft,
    onPrimaryContainer = Paper.Rust,
    background = Paper.Surface,
    onBackground = Paper.Ink,
    surface = Paper.Surface,
    onSurface = Paper.Ink,
    surfaceVariant = Paper.SurfaceDim,
    onSurfaceVariant = Paper.InkMuted,
    surfaceContainer = Paper.SurfaceLifted,
    outline = Paper.Line,
    outlineVariant = Paper.Line,
    error = Paper.Rust,
    onError = Color.White,
)

internal val DarkScheme: ColorScheme = darkColorScheme(
    primary = Ink.Rust,
    onPrimary = Color(0xFF2B120A),
    primaryContainer = Ink.RustSoft,
    onPrimaryContainer = Ink.Rust,
    background = Ink.Surface,
    onBackground = Ink.Paper,
    surface = Ink.Surface,
    onSurface = Ink.Paper,
    surfaceVariant = Ink.SurfaceDim,
    onSurfaceVariant = Ink.PaperMuted,
    surfaceContainer = Ink.SurfaceLifted,
    outline = Ink.Line,
    outlineVariant = Ink.Line,
    error = Ink.Rust,
    onError = Color(0xFF2B120A),
)

/**
 * The few colours Material's scheme has no slot for.
 *
 * Kept as a separate token set rather than bent into an unused Material role.
 * Putting a timeline rule colour in `tertiary` because it happened to be free is
 * how a palette becomes impossible to reason about six months later.
 */
data class BuildOrBreakColours(
    val faint: Color,
    val rule: Color,
    val warning: Color,
    val lifted: Color,
)

internal val LightExtras = BuildOrBreakColours(
    faint = Paper.InkFaint,
    rule = Paper.Line,
    warning = Paper.Warning,
    lifted = Paper.SurfaceLifted,
)

internal val DarkExtras = BuildOrBreakColours(
    faint = Ink.PaperFaint,
    rule = Ink.Line,
    warning = Ink.Warning,
    lifted = Ink.SurfaceLifted,
)
