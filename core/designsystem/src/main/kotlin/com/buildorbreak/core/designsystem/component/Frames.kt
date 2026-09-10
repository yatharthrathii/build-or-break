package com.buildorbreak.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.buildorbreak.core.designsystem.theme.Theme
import java.util.Locale

private val HeaderMark = 12.dp

/**
 * The top of every screen: a kicker, a title, a heavy rule.
 *
 * No Material top bar. The title is set like a poster heading, the kicker above
 * it carries the date or the count, and the two pixel rule underneath is what
 * separates the heading from the day. [trailing] is for the one or two things
 * that belong up here, such as the clock or an import icon.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    kicker: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Theme.spacing.medium, end = Theme.spacing.medium, top = 14.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Blank counts as absent. A screen that has not read its data
                // yet passes an empty string rather than inventing a label, and
                // an empty kicker would otherwise reserve a line of nothing.
                if (!kicker.isNullOrBlank()) {
                    Kicker(text = kicker)
                }

                Text(
                    text = title.uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = if (kicker != null) 6.dp else 0.dp),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Theme.spacing.small),
                content = trailing,
            )
        }

        HeavyRule()
    }
}

/** The small accent square that sits beside the clock. The app's full stop. */
@Composable
fun AccentMark(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(HeaderMark)
            .background(MaterialTheme.colorScheme.primary),
    )
}

/** A two pixel rule in ink. Separates sections, frames a table. */
@Composable
fun HeavyRule(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Theme.spacing.rule)
            .background(MaterialTheme.colorScheme.onSurface),
    )
}

/** A one pixel rule at low opacity. Separates rows. */
@Composable
fun HairlineRule(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Theme.spacing.hairline)
            .background(Theme.colours.hairline),
    )
}

/** A tracked label introducing a section: THE DAY, ALARMS, YOUR DATA. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, underlined: Boolean = false) {
    Column(modifier = modifier.fillMaxWidth()) {
        Label(
            text = text,
            modifier = Modifier.padding(
                start = Theme.spacing.medium,
                end = Theme.spacing.medium,
                top = 18.dp,
                bottom = if (underlined) 9.dp else 6.dp,
            ),
        )

        if (underlined) {
            HeavyRule(Modifier.padding(horizontal = Theme.spacing.medium))
        }
    }
}

/**
 * A box with a heavy ink border and a raised ground.
 *
 * This is the only "card" the design has. No shadow, no radius, no tint: a
 * printed frame around the thing that matters most on the screen.
 */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .border(Theme.spacing.rule, MaterialTheme.colorScheme.onSurface)
            .background(Theme.colours.raised),
    ) {
        content()
    }
}

/**
 * Something the app noticed, in a strip across the screen.
 *
 * Never a dialog. A dialog interrupts whatever somebody opened the app to do,
 * and a routine app that interrupts is an app that gets closed before the
 * routine is read. On a tint when it is about the day; on the raised ground
 * when it is plain information.
 */
@Composable
fun NoticeBar(
    text: String,
    modifier: Modifier = Modifier,
    tinted: Boolean = true,
    action: @Composable RowScope.() -> Unit = {},
) {
    val ground = if (tinted) MaterialTheme.colorScheme.primaryContainer else Theme.colours.raised
    val ink = if (tinted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ground)
                .padding(horizontal = Theme.spacing.medium, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.small),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = ink,
                modifier = Modifier.weight(1f),
            )

            action()
        }

        HairlineRule()
    }
}

/**
 * A screen with nothing on it yet, and one thing to do about that.
 *
 * An empty state that only says "nothing here" is a dead end. Every one in this
 * app names the next action, because a person looking at an empty routine app
 * has not failed at anything, they have simply not started.
 */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = Theme.spacing.section),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.medium, Alignment.CenterVertically),
    ) {
        Text(
            text = title.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        actions()
    }
}
