package com.buildorbreak.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * The system font, used carefully.
 *
 * No bundled typeface. A custom font would cost a download, a licence and a
 * cold start budget, and would be read at six in the morning by somebody who
 * does not care what it is. The system font is already the one every other app
 * on the phone uses, already hinted for that screen, and already respects the
 * user's own font size setting, which matters more here than any typeface
 * choice: somebody with large text turned on has told the phone something and
 * the app should listen.
 *
 * Weight and size carry the hierarchy instead of colour, which is what leaves
 * the single rust accent free to mean something.
 */
private val Default = FontFamily.Default

internal val BuildOrBreakTypography = Typography(
    // The date at the top of Today. Large, light, quiet.
    headlineMedium = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp,
    ),

    titleLarge = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),

    // An item title on the timeline. The thing actually being read.
    titleMedium = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),

    bodyLarge = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),

    bodyMedium = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),

    labelLarge = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),

    labelMedium = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
    ),
)

/**
 * The clock column on the timeline.
 *
 * Tabular figures and a fixed alignment so 06:30 and 11:45 occupy exactly the
 * same width. Without it the times jitter left and right down the list and the
 * eye cannot run down the column, which is the one thing that column is for.
 */
val TimeStyle: TextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    textAlign = TextAlign.End,
    letterSpacing = (-0.2).sp,
)
