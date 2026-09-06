package com.buildorbreak.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.buildorbreak.core.designsystem.R

/**
 * Archivo, at every weight from one file.
 *
 * A bundled typeface after all. The earlier decision to use the system font was
 * about cost, and the cost has been paid down: this is a single variable font
 * subset to Latin, a hundred and forty kilobytes, with the tabular figures the
 * clock column needs. What it buys is the whole identity. The heavy, tight,
 * uppercase headlines and the compact meta text are Archivo, and the system font
 * cannot do either without looking like a settings screen.
 *
 * Variable weight rather than six static files: the same file is declared once
 * per weight the type scale uses, and the renderer instances the axis. Requires
 * API 26, which is this app's minimum.
 */
private val Archivo = FontFamily(
    archivo(FontWeight.Normal),
    archivo(FontWeight.Medium),
    archivo(FontWeight.SemiBold),
    archivo(FontWeight.Bold),
    archivo(FontWeight.ExtraBold),
    archivo(FontWeight.Black),
)

// Variable font axes are still behind the experimental text API, several
// releases in. Writing six static font files to avoid one opt in would cost
// five hundred kilobytes for the same glyphs.
@OptIn(ExperimentalTextApi::class)
private fun archivo(weight: FontWeight): Font = Font(
    resId = R.font.archivo,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Digits that line up. The one feature a clock column cannot do without. */
private const val TABULAR = "tnum"

/**
 * The scale, taken from the identity sheet.
 *
 * Weight and tracking carry the hierarchy. Headlines are black and tight,
 * labels are extra bold, small and widely tracked, and body text is regular.
 * The two ends of the scale are far apart on purpose: a screen title at 24
 * next to a label at 11 reads as a poster, and a routine app that reads as a
 * poster is one somebody looks at rather than scrolls past.
 */
internal val BuildOrBreakTypography = Typography(
    // The hero number: "4 of 9", "78%".
    displaySmall = TextStyle(
        fontFamily = Archivo,
        fontWeight = FontWeight.Black,
        fontSize = 34.sp,
        lineHeight = 34.sp,
        letterSpacing = (-1).sp,
        fontFeatureSettings = TABULAR,
    ),

    // A screen title: TODAY, PLAN, INSIGHTS.
    headlineMedium = TextStyle(
        fontFamily = Archivo,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 24.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.5).sp,
    ),

    // The next thing to happen, and an editor title.
    headlineSmall = TextStyle(
        fontFamily = Archivo,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.4).sp,
    ),

    titleLarge = TextStyle(
        fontFamily = Archivo,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 22.sp,
    ),

    // A step on the timeline. The thing actually being read.
    titleMedium = TextStyle(
        fontFamily = Archivo,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),

    titleSmall = TextStyle(
        fontFamily = Archivo,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),

    bodyLarge = TextStyle(
        fontFamily = Archivo,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),

    // Meta: "Window 09:15 to 10:00 · kept 6/7".
    bodyMedium = TextStyle(
        fontFamily = Archivo,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),

    bodySmall = TextStyle(
        fontFamily = Archivo,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),

    // A tracked uppercase label: NEXT UP, STEPS KEPT TODAY.
    labelLarge = TextStyle(
        fontFamily = Archivo,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 11.sp,
        lineHeight = 12.sp,
        letterSpacing = 1.5.sp,
    ),

    // A kicker above a title, and a button label.
    labelMedium = TextStyle(
        fontFamily = Archivo,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 10.sp,
        lineHeight = 11.sp,
        letterSpacing = 1.4.sp,
    ),

    // A badge: FIXED, WINDOW, +15 AFTER GYM.
    labelSmall = TextStyle(
        fontFamily = Archivo,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 9.sp,
        lineHeight = 10.sp,
        letterSpacing = 1.1.sp,
    ),
)

/**
 * The clock column.
 *
 * Tabular figures and a fixed alignment so 06:30 and 11:45 occupy exactly the
 * same width. Without it the times jitter down the list and the eye cannot run
 * down the column, which is the one thing that column is for.
 */
val TimeStyle: TextStyle = TextStyle(
    fontFamily = Archivo,
    fontWeight = FontWeight.SemiBold,
    fontSize = 13.sp,
    lineHeight = 16.sp,
    fontFeatureSettings = TABULAR,
)

/** A number big enough to be the whole point of a screen: 78%. */
val HeroNumberStyle: TextStyle = TextStyle(
    fontFamily = Archivo,
    fontWeight = FontWeight.Black,
    fontSize = 54.sp,
    lineHeight = 50.sp,
    letterSpacing = (-2.4).sp,
    fontFeatureSettings = TABULAR,
)

/** The block button label: DONE, GET STARTED. */
val BlockLabelStyle: TextStyle = TextStyle(
    fontFamily = Archivo,
    fontWeight = FontWeight.Black,
    fontSize = 17.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.4.sp,
)

/** The wordmark. */
val WordmarkStyle: TextStyle = TextStyle(
    fontFamily = Archivo,
    fontWeight = FontWeight.Black,
    fontSize = 20.sp,
    lineHeight = 20.sp,
    letterSpacing = (-0.6).sp,
)
