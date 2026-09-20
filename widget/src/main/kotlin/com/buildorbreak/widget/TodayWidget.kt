package com.buildorbreak.widget

import android.content.Context
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/**
 * The day, on the home screen, without opening anything.
 *
 * One size and one job: what is next, and the two answers to it. A widget that
 * lists the whole day is a widget nobody reads, and one that only shows a count
 * is a widget nobody keeps. The next step with Done beside it is the smallest
 * thing that saves a launch, which is the only reason a widget earns its space.
 *
 * Drawn flat and in the app's own colours rather than the system's. Glance
 * cannot use the Compose design system, so the handful of values the widget
 * needs are restated in [WidgetColours] and nowhere else.
 */
class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetData.read(context)

        provideContent { Face(snapshot) }
    }

    @Composable
    private fun Face(snapshot: WidgetSnapshot) {
        GlanceTheme(colors = WidgetColours.providers) {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(GlanceTheme.colors.background)
                    .padding(14.dp)
                    .clickable(actionRunCallback<OpenAppAction>()),
            ) {
                Header(snapshot)

                Spacer(modifier = GlanceModifier.height(10.dp))

                when {
                    snapshot.title == null -> Line(snapshot.emptyLine, GlanceTheme.colors.onSurfaceVariant)
                    else -> Next(snapshot)
                }
            }
        }
    }

    @Composable
    private fun Header(snapshot: WidgetSnapshot) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = snapshot.kicker,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
                modifier = GlanceModifier.defaultWeight(),
            )

            Text(
                text = snapshot.count,
                style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold),
            )
        }

        Spacer(modifier = GlanceModifier.height(8.dp).fillMaxWidth().background(GlanceTheme.colors.outline))
    }

    @Composable
    private fun Next(snapshot: WidgetSnapshot) {
        Text(
            text = snapshot.time.orEmpty(),
            style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold),
        )

        Spacer(modifier = GlanceModifier.height(3.dp))

        Text(
            text = snapshot.title.orEmpty(),
            maxLines = 2,
            style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 17.sp, fontWeight = FontWeight.Bold),
        )

        Spacer(modifier = GlanceModifier.height(12.dp))

        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Action(
                text = snapshot.doneLabel,
                ground = GlanceTheme.colors.primary,
                ink = GlanceTheme.colors.onPrimary,
                modifier = GlanceModifier.defaultWeight(),
                onClick = WidgetActions.done(snapshot.occurrenceId),
            )

            Spacer(modifier = GlanceModifier.width(8.dp))

            Action(
                text = snapshot.snoozeLabel,
                ground = GlanceTheme.colors.surfaceVariant,
                ink = GlanceTheme.colors.onBackground,
                modifier = GlanceModifier.defaultWeight(),
                onClick = WidgetActions.snooze(snapshot.occurrenceId),
            )
        }
    }

    /** A block of colour with a label, matching the app's buttons as closely as Glance allows. */
    @Composable
    private fun Action(
        text: String,
        ground: ColorProvider,
        ink: ColorProvider,
        modifier: GlanceModifier,
        onClick: androidx.glance.action.Action,
    ) {
        Row(
            modifier = modifier
                .height(38.dp)
                .background(ground)
                // Square, like everything else. Glance insists on a value here
                // on Android 12 and above, so it is stated rather than left to
                // whatever the launcher would have chosen.
                .cornerRadius(0.dp)
                .clickable(onClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = text, style = TextStyle(color = ink, fontSize = 12.sp, fontWeight = FontWeight.Bold))
        }
    }

    @Composable
    private fun Line(text: String, colour: ColorProvider) {
        Text(text = text, style = TextStyle(color = colour, fontSize = 14.sp))
    }
}

/**
 * The palette, handed to Glance as a theme.
 *
 * Glance draws through RemoteViews and cannot see the Compose design system, so
 * the tokens the widget needs are restated here as a pair of colour schemes.
 * Giving them to `GlanceTheme` rather than reaching for a colour directly is
 * what makes the widget follow the launcher into dark mode, which is the
 * configuration that actually matters: the widget lives on somebody else's
 * screen, not inside the app.
 */
internal object WidgetColours {

    private val light = lightColorScheme(
        primary = Color(0xFFEC3013),
        onPrimary = Color(0xFFFFFFFF),
        background = Color(0xFFF3F2F2),
        onBackground = Color(0xFF201E1D),
        surface = Color(0xFFF3F2F2),
        onSurface = Color(0xFF201E1D),
        surfaceVariant = Color(0xFFEAE9E9),
        onSurfaceVariant = Color(0xFF605D5D),
        outline = Color(0xFFD7D3D3),
    )

    private val dark = darkColorScheme(
        primary = Color(0xFFFF563C),
        onPrimary = Color(0xFF141312),
        background = Color(0xFF141312),
        onBackground = Color(0xFFF3F2F2),
        surface = Color(0xFF141312),
        onSurface = Color(0xFFF3F2F2),
        surfaceVariant = Color(0xFF1F1D1C),
        onSurfaceVariant = Color(0xFFBAB6B6),
        outline = Color(0xFF3A3736),
    )

    val providers = ColorProviders(light = light, dark = dark)
}
