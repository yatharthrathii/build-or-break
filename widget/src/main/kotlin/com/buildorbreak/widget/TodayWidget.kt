package com.buildorbreak.widget

import android.content.Context
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
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
 * The three faces, by the room the launcher gives.
 *
 * Glance picks the largest of these that fits the cell the widget was placed
 * in, and the face is chosen from that. A widget resized by the user moves
 * between faces on its own, so a two by one strip and a four by four card
 * are the same widget at two sizes rather than two things to maintain.
 */
internal object WidgetSizes {
    /** A strip: the next step and a Done square. */
    val SMALL = DpSize(110.dp, 48.dp)

    /** The card: the next step with Done and Snooze under it. */
    val MEDIUM = DpSize(250.dp, 110.dp)

    /** The card, and the day under it. */
    val LARGE = DpSize(250.dp, 230.dp)
}

/** What the tall face spends on everything above the list, and on each row of it. */
private val LIST_HEADROOM = 150.dp
private val ROW_HEIGHT = 24.dp

/** Glance allows ten children in a column, and the rows get a column of their own. */
private const val MAX_ROWS = 10

/**
 * The day, on the home screen, without opening anything.
 *
 * One job at every size: what is next, and the answer to it. The strip is
 * the step and a tick, the card adds a snooze, and the tall one adds the
 * rest of the day underneath so a glance at breakfast says what the evening
 * holds. None of them is a widget nobody reads: the next step with Done
 * beside it is the smallest thing that saves a launch, which is the only
 * reason a widget earns its space.
 *
 * Drawn flat and in the app's own colours rather than the system's. Glance
 * cannot use the Compose design system, so the handful of values the widget
 * needs are restated in [WidgetColours] and nowhere else.
 */
class TodayWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(WidgetSizes.SMALL, WidgetSizes.MEDIUM, WidgetSizes.LARGE),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetData.read(context)

        provideContent { Face(snapshot) }
    }

    @Composable
    private fun Face(snapshot: WidgetSnapshot) {
        val size = LocalSize.current

        GlanceTheme(colors = WidgetColours.providers) {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(GlanceTheme.colors.background)
                    .clickable(actionRunCallback<OpenAppAction>()),
            ) {
                when {
                    size.height < WidgetSizes.MEDIUM.height -> Strip(snapshot)
                    size.height < WidgetSizes.LARGE.height ->
                        Card(snapshot, modifier = GlanceModifier.fillMaxSize().padding(14.dp))
                    else -> Tall(snapshot, size)
                }
            }
        }
    }

    /** The strip: time, title, one square. The smallest thing that saves a launch. */
    @Composable
    private fun Strip(snapshot: WidgetSnapshot) {
        Row(
            modifier = GlanceModifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) { StripText(snapshot) }

            if (snapshot.title != null) {
                Spacer(modifier = GlanceModifier.width(8.dp))

                Action(
                    text = snapshot.tick,
                    ground = GlanceTheme.colors.primary,
                    ink = GlanceTheme.colors.onPrimary,
                    modifier = GlanceModifier.width(36.dp),
                    onClick = WidgetActions.done(snapshot.occurrenceId),
                )
            }
        }
    }

    @Composable
    private fun StripText(snapshot: WidgetSnapshot) {
        if (snapshot.title == null) {
            Line(snapshot.emptyLine, GlanceTheme.colors.onSurfaceVariant)
            return
        }

        Text(
            text = snapshot.time.orEmpty(),
            style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold),
        )

        Text(
            text = snapshot.title,
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Bold),
        )
    }

    /** The card: header, next step, Done and Snooze. */
    @Composable
    private fun Card(snapshot: WidgetSnapshot, modifier: GlanceModifier = GlanceModifier) {
        Column(modifier = modifier) {
            Header(snapshot)

            Spacer(modifier = GlanceModifier.height(10.dp))

            when {
                snapshot.title == null -> Line(snapshot.emptyLine, GlanceTheme.colors.onSurfaceVariant)
                else -> Next(snapshot)
            }
        }
    }

    /**
     * The card with the day under it, as many rows as the height allows.
     *
     * Grouped into inner columns on purpose. Glance refuses a column with more
     * than ten children, and the header, the card and the rows add up to
     * more than that on any phone tall enough to want this face.
     */
    @Composable
    private fun Tall(snapshot: WidgetSnapshot, size: DpSize) {
        val roomFor = ((size.height - LIST_HEADROOM) / ROW_HEIGHT).toInt().coerceIn(1, MAX_ROWS)

        Column(modifier = GlanceModifier.fillMaxSize().padding(14.dp)) {
            Card(snapshot)

            Spacer(modifier = GlanceModifier.height(10.dp))
            Spacer(modifier = GlanceModifier.height(1.dp).fillMaxWidth().background(GlanceTheme.colors.outline))

            // The day from the next step on. What is already done is behind
            // the count in the header, and a list that opened with six
            // finished rows would push the evening off the bottom.
            val from = snapshot.rows.indexOfFirst { it.isNext }.coerceAtLeast(0)
            Column { snapshot.rows.drop(from).take(roomFor).forEach { DayRow(it) } }
        }
    }

    @Composable
    private fun DayRow(row: WidgetRow) {
        val ink = when {
            row.isNext -> GlanceTheme.colors.primary
            row.settled -> GlanceTheme.colors.outline
            else -> GlanceTheme.colors.onSurfaceVariant
        }

        Row(
            modifier = GlanceModifier.fillMaxWidth().height(ROW_HEIGHT),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.time,
                style = TextStyle(color = ink, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.width(44.dp),
            )

            Text(
                text = row.title,
                maxLines = 1,
                style = TextStyle(
                    color = ink,
                    fontSize = 12.sp,
                    fontWeight = if (row.isNext) FontWeight.Bold else FontWeight.Normal,
                ),
                modifier = GlanceModifier.defaultWeight(),
            )
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
                .height(36.dp)
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
