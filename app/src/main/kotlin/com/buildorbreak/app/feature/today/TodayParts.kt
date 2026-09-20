package com.buildorbreak.app.feature.today

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.buildorbreak.app.R
import com.buildorbreak.core.designsystem.component.Badge
import com.buildorbreak.core.designsystem.component.BlockButton
import com.buildorbreak.core.designsystem.component.HairlineRule
import com.buildorbreak.core.designsystem.component.HeavyRule
import com.buildorbreak.core.designsystem.component.Kicker
import com.buildorbreak.core.designsystem.component.Label
import com.buildorbreak.core.designsystem.component.NoticeBar
import com.buildorbreak.core.designsystem.component.OutlineButton
import com.buildorbreak.core.designsystem.component.Panel
import com.buildorbreak.core.designsystem.component.ProgressRing
import com.buildorbreak.core.designsystem.component.SegmentedTabs
import com.buildorbreak.core.designsystem.component.rememberFeedback
import com.buildorbreak.core.designsystem.theme.Theme
import com.buildorbreak.core.designsystem.theme.TimeStyle
import java.text.NumberFormat
import java.util.Locale

private const val CARD_RISE = 3
private const val CARD_FADE_MILLIS = 180

/** The shift options offered when the day is running late, in minutes. */
private val SHIFT_OPTIONS = listOf(15, 30, 60, 90)

/**
 * The ring, the count, and the run.
 *
 * "4 of 9" rather than a percentage, because the nine steps are listed right
 * below and a count is a thing somebody can check. The run line says how many
 * days in a row the plan has been kept, or that it has not started yet.
 */
/** How long the kept count takes to catch up. Long enough to see, short enough not to wait. */
private const val COUNT_MILLIS = 420

@Composable
internal fun RingRow(
    header: DayHeader,
    runDays: Int,
    consistency: Consistency? = null,
    points: PointsUi? = null,
) {
    // At a large font size three columns do not fit, and the label beside
    // the ring was the thing that gave way. The score moves under the row
    // instead, where it has the whole width.
    val stacked = LocalDensity.current.fontScale > LARGE_FONT

    Column {
        Row(
            modifier = Modifier.padding(horizontal = Theme.spacing.medium, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProgressRing(fraction = header.fraction)

            RingCounts(header = header, runDays = runDays, consistency = consistency, modifier = Modifier.weight(1f))

            if (!stacked) points?.let { PointsFigure(points = it) }
        }

        if (stacked) {
            points?.let {
                PointsFigure(
                    points = it,
                    modifier = Modifier.padding(start = Theme.spacing.medium, bottom = Theme.spacing.medium),
                    alignEnd = false,
                )
            }
        }

        HairlineRule()
    }
}

/** Above this font scale the ring row gives the score its own line. */
private const val LARGE_FONT = 1.3f

/**
 * The score, on the right of the ring.
 *
 * One number, counted up as it changes, with today's share under it so the
 * total is explained by the day it is sitting on. A missed step is worth
 * nothing, not less than nothing, so the only way this falls is an undo.
 */
@Composable
private fun PointsFigure(points: PointsUi, modifier: Modifier = Modifier, alignEnd: Boolean = true) {
    val counted by animateIntAsState(
        targetValue = points.total,
        animationSpec = tween(COUNT_MILLIS),
        label = "points",
    )
    val format = remember { NumberFormat.getIntegerInstance() }

    Column(
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
        modifier = if (alignEnd) modifier.padding(start = Theme.spacing.inset) else modifier,
    ) {
        Text(
            text = format.format(counted),
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Black,
                fontFeatureSettings = TimeStyle.fontFeatureSettings,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )

        Label(text = stringResource(R.string.today_points), modifier = Modifier.padding(top = Theme.spacing.tight))

        // Today's share, in the accent. Zero is left off: "+0 today" under a
        // number is the app pointing out that nothing has happened yet,
        // and the empty ring beside it already says so more kindly.
        if (points.today > 0) {
            Text(
                text = stringResource(R.string.today_points_today, points.today),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = Theme.spacing.tight),
            )
        }
    }
}

/** The three lines beside the ring: the count, the run, and the steadier number. */
/** How many days in a row, or the invitation to start one. */
@Composable
private fun RunLine(runDays: Int) {
    Text(
        text = if (runDays > 0) {
            pluralStringResource(R.plurals.today_run_days, runDays, runDays)
        } else {
            stringResource(R.string.today_run_none)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Theme.spacing.small),
    )
}

@Composable
private fun RingCounts(
    header: DayHeader,
    runDays: Int,
    consistency: Consistency?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(start = Theme.spacing.medium)) {
        // Counted up rather than swapped. The number is the one thing on this
        // screen that says the day is going well, and a digit that changes
        // while the ring fills is worth watching; one that has already changed
        // by the time the eye arrives is not.
        val counted by animateIntAsState(
            targetValue = header.doneCount,
            animationSpec = tween(COUNT_MILLIS),
            label = "kept",
        )

        Text(
            text = stringResource(R.string.today_of, counted, header.total),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Label(
            text = stringResource(R.string.today_steps_kept),
            modifier = Modifier.padding(top = Theme.spacing.tight),
            maxLines = 2,
        )

        RunLine(runDays = runDays)

        // The number that survives a bad day. A run resets to nothing the
        // first morning somebody oversleeps; this goes down by one and can go
        // back up tomorrow, which is the difference between a measure and a
        // punishment.
        if (consistency != null && consistency.hasEnough) {
            Text(
                text = pluralStringResource(
                    R.plurals.today_consistency,
                    consistency.days,
                    consistency.goodDays,
                    consistency.days,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Theme.spacing.tight),
            )
        }
    }
}

/**
 * Where the day stands against the clock, and the one control that moves it.
 *
 * On a tint when the day has been shifted, because a moved day is a state
 * worth noticing every time the screen is opened, and with an undo, because a
 * shift chosen at 07:00 in a hurry is a shift somebody may want back.
 */
@Composable
internal fun ShiftBar(
    state: TodayUiState,
    onRunningLate: () -> Unit,
    onReset: () -> Unit,
    onNormalDay: () -> Unit,
) {
    if (state.isReduced) {
        // Honest about having changed nothing. A sick day only shrinks the
        // steps that were given a smaller version in advance, and a banner
        // claiming otherwise on a plan that has none is the app taking credit
        // for work it did not do.
        val line = if (state.sickDayChangedNothing) {
            stringResource(R.string.today_sick_none)
        } else {
            pluralStringResource(R.plurals.today_sick_line, state.reducedCount, state.reducedCount)
        }

        NoticeBar(text = line) {
            OutlineButton(text = stringResource(R.string.today_back_to_normal), onClick = onNormalDay)
        }
    } else if (state.isShifted) {
        NoticeBar(
            text = pluralStringResource(
                R.plurals.today_shift_line,
                state.movedCount,
                state.shiftMinutes,
                state.movedCount,
            ),
        ) {
            OutlineButton(text = stringResource(R.string.today_undo), onClick = onReset)
        }
    } else {
        NoticeBar(text = stringResource(R.string.today_on_time), tinted = false) {
            OutlineButton(text = stringResource(R.string.today_running_late), onClick = onRunningLate)
        }
    }
}

/**
 * The next thing to do, in a frame, with a block button that does it.
 *
 * Keyed on the occurrence so completing a step slides the card up and out and
 * springs the next one in from below. That one motion is what makes a tap
 * feel like it moved the day, rather than just changing a row.
 */
@Composable
internal fun NextUpCard(
    next: NextUp?,
    allDone: Boolean,
    /** Settled, but none of it kept. The same state, and not the same news. */
    keptNothing: Boolean,
    onDone: (Long) -> Unit,
    onDoneMinimum: (Long) -> Unit,
    onSnooze: (Long) -> Unit,
    onSkip: (Long) -> Unit,
) {
    AnimatedContent(
        targetState = next,
        contentKey = { it?.occurrenceId ?: -1L },
        transitionSpec = {
            (
                slideInVertically(
                    spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
                ) { it / CARD_RISE } +
                    fadeIn(tween(CARD_FADE_MILLIS))
                )
                .togetherWith(
                    slideOutVertically(tween(CARD_FADE_MILLIS)) { -it / CARD_RISE } + fadeOut(tween(CARD_FADE_MILLIS)),
                )
        },
        label = "next",
    ) { card ->
        Box(modifier = Modifier.padding(horizontal = Theme.spacing.medium, vertical = 14.dp)) {
            when {
                card != null -> NextUpPanel(card, onDone, onDoneMinimum, onSnooze, onSkip)
                allDone -> DayDonePanel(keptNothing = keptNothing)
            }
        }
    }
}

@Composable
private fun NextUpPanel(
    card: NextUp,
    onDone: (Long) -> Unit,
    onDoneMinimum: (Long) -> Unit,
    onSnooze: (Long) -> Unit,
    onSkip: (Long) -> Unit,
) {
    Panel {
        Column {
            NextUpHeading(card = card)

            // Said plainly rather than left to a greyed out button. A control
            // that does nothing and does not say why is the most annoying
            // thing a screen can contain.
            if (!card.hasArrived) {
                Text(
                    text = stringResource(R.string.today_not_yet, card.time),
                    style = MaterialTheme.typography.bodySmall,
                    color = Theme.colours.faint,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                )
            }

            BlockButton(
                text = stringResource(R.string.today_done),
                onClick = { onDone(card.occurrenceId) },
                enabled = card.isActionable,
                icon = Icons.Outlined.Check,
            )

            SecondaryActions(card = card, onDoneMinimum = onDoneMinimum, onSnooze = onSnooze, onSkip = onSkip)
        }
    }
}

/**
 * The title, and the line the user wrote under it.
 *
 * The note is shown at the one moment it is worth anything: when the step has
 * arrived and they are about to do it. On the plan it is a mark; here it is
 * the sentence.
 */
@Composable
private fun NextUpTitle(card: NextUp) {
    Text(
        text = card.title,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 12.dp).padding(top = 6.dp),
    )

    card.detail?.let { detail ->
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp).padding(top = Theme.spacing.tight),
        )
    }
}

/** The kicker, the time, the title and the badges. Everything above the button. */
@Composable
private fun NextUpHeading(card: NextUp) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = Theme.spacing.inset),
        verticalAlignment = Alignment.Bottom,
    ) {
        Kicker(
            text = stringResource(if (card.isOverdue) R.string.today_still_open else R.string.today_next_up),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )

        Text(text = card.time, style = TimeStyle, color = MaterialTheme.colorScheme.onSurface)
    }

    NextUpTitle(card = card)

    Row(
        modifier = Modifier.padding(horizontal = 12.dp).padding(top = Theme.spacing.small, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Badge(text = kindText(card.kind), accent = true)

        card.durationMinutes?.let { Badge(text = stringResource(R.string.today_duration_min, it)) }

        card.note?.let {
            Text(
                text = noteText(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Two or three cells under the block button, separated by heavy rules. */
@Composable
private fun SecondaryActions(
    card: NextUp,
    onDoneMinimum: (Long) -> Unit,
    onSnooze: (Long) -> Unit,
    onSkip: (Long) -> Unit,
) {
    HeavyRule()

    // Sized to its tallest label rather than to a number. Three cells on a
    // narrow phone put "Smaller version" on two lines, and a fixed height cut
    // the second one off.
    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        if (card.hasMinimum) {
            ActionCell(
                text = stringResource(R.string.today_done_minimum),
                enabled = card.isActionable,
                onClick = { onDoneMinimum(card.occurrenceId) },
                modifier = Modifier.weight(1f),
            )
            Divider()
        }

        ActionCell(
            text = stringResource(R.string.today_snooze_10),
            enabled = card.isActionable,
            onClick = { onSnooze(card.occurrenceId) },
            modifier = Modifier.weight(1f),
        )

        Divider()

        ActionCell(
            text = stringResource(R.string.today_skip_today),
            enabled = card.isSkippable,
            onClick = { onSkip(card.occurrenceId) },
            modifier = Modifier.weight(1f),
            muted = true,
        )
    }
}

/** The shortest an action cell gets, so a one word label still has a tappable target. */
private val CellMinHeight = 44.dp

@Composable
private fun ActionCell(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
) {
    val feedback = rememberFeedback()

    Box(
        modifier = modifier
            .heightIn(min = CellMinHeight)
            .clickable(enabled = enabled, role = Role.Button) {
                feedback.tap()
                onClick()
            }
            .padding(horizontal = Theme.spacing.small, vertical = Theme.spacing.inset),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            color = when {
                !enabled -> Theme.colours.faint
                muted -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .width(Theme.spacing.rule)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.onSurface),
    )
}

/** Every step settled. The card's place is taken by a quiet full stop. */
@Composable
private fun DayDonePanel(keptNothing: Boolean) {
    Panel {
        Column(modifier = Modifier.padding(14.dp)) {
            Kicker(
                text = stringResource(
                    if (keptNothing) R.string.today_all_settled_kicker else R.string.today_all_done_kicker,
                ),
                color = MaterialTheme.colorScheme.primary,
            )

            Text(
                text = stringResource(
                    if (keptNothing) R.string.today_all_settled_title else R.string.today_all_done_title,
                ),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 6.dp),
            )

            Text(
                text = stringResource(
                    if (keptNothing) R.string.today_all_settled_body else R.string.today_all_done_body,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Theme.spacing.tight),
            )
        }
    }
}

/**
 * How late? Four answers, one tap.
 *
 * A sheet rather than a number field, because at 07:40 with the morning gone
 * nobody wants to type. Ninety minutes is the top: anything beyond that is a
 * different day and belongs to the template switch, not the shift.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RunningLateSheet(
    state: TodayUiState,
    onPick: (Int) -> Unit,
    onRunTemplate: (Long) -> Unit,
    onSickDay: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier.padding(
                start = Theme.spacing.medium,
                end = Theme.spacing.medium,
                top = 22.dp,
                bottom = 28.dp,
            ),
        ) {
            Text(
                text = stringResource(R.string.today_late_title).uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = stringResource(R.string.today_late_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 18.dp),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SHIFT_OPTIONS.forEach { minutes ->
                    OutlineButton(
                        text = stringResource(R.string.today_late_option, minutes),
                        onClick = { onPick(minutes) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            DifferentDay(state = state, onRunTemplate = onRunTemplate, onSickDay = onSickDay)
        }
    }
}

/**
 * The other thing a morning can need: not later, but different.
 *
 * Every template on the plan as a tab, and a sick day, which runs the same
 * template with every smaller version in place of the full one. Both reset a
 * shift, because a different day is not the old day moved.
 */
@Composable
private fun DifferentDay(state: TodayUiState, onRunTemplate: (Long) -> Unit, onSickDay: () -> Unit) {
    Column(modifier = Modifier.padding(top = 24.dp)) {
        Label(text = stringResource(R.string.today_different_day))

        if (state.templates.size > 1) {
            SegmentedTabs(
                options = state.templates.map { it.name },
                selectedIndex = state.templates.indexOfFirst { it.id == state.currentTemplateId }.coerceAtLeast(0),
                onSelect = { onRunTemplate(state.templates[it].id) },
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        if (!state.isReduced) {
            OutlineButton(
                text = stringResource(R.string.today_sick_day),
                onClick = onSickDay,
                modifier = Modifier.padding(top = 10.dp),
            )

            Text(
                text = stringResource(R.string.today_sick_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

// Copy lookups -----------------------------------------------------------------

@Composable
internal fun kindText(kind: EntryKind): String = when (kind) {
    EntryKind.Fixed -> stringResource(R.string.kind_fixed)
    is EntryKind.After -> stringResource(R.string.kind_after, kind.offsetMinutes, kind.parentTitle)
    is EntryKind.Window -> stringResource(R.string.kind_window)
    is EntryKind.Every -> stringResource(R.string.kind_every, kind.minutes)
}

/**
 * A length of time, in the units a person would use for it.
 *
 * Minutes up to an hour, then hours and minutes. A snooze reads as "+10" and
 * a step pulled back from the morning into the afternoon reads as "+8h 45m"
 * rather than as "+525", which is a number nobody converts in their head.
 */
@Composable
private fun spanText(minutes: Int): String {
    if (minutes < MINUTES_PER_HOUR) return stringResource(R.string.span_minutes, minutes)

    val hours = minutes / MINUTES_PER_HOUR
    val rest = minutes % MINUTES_PER_HOUR

    return if (rest == 0) {
        stringResource(R.string.span_hours, hours)
    } else {
        stringResource(R.string.span_hours_minutes, hours, rest)
    }
}

private const val MINUTES_PER_HOUR = 60

@Composable
internal fun noteText(note: EntryNote): String = when (note) {
    EntryNote.Pinned -> stringResource(R.string.note_pinned)
    is EntryNote.DoneAt -> when {
        note.overMinutes > 0 -> stringResource(R.string.note_done_over, note.time, spanText(note.overMinutes))
        note.overMinutes < 0 -> stringResource(R.string.note_done_early, note.time, spanText(-note.overMinutes))
        else -> stringResource(R.string.note_done_at, note.time)
    }

    is EntryNote.Moved -> if (note.minutes > 0) {
        stringResource(R.string.note_moved_later, spanText(note.minutes))
    } else {
        stringResource(R.string.note_moved_earlier, spanText(-note.minutes))
    }

    is EntryNote.Ends -> stringResource(R.string.note_ends, note.time)
    EntryNote.Missed -> stringResource(R.string.note_missed)
    EntryNote.Skipped -> stringResource(R.string.note_skipped)
    is EntryNote.Snoozed -> stringResource(R.string.note_snoozed, note.count)
    EntryNote.Degraded -> stringResource(R.string.note_degraded)
    EntryNote.Reduced -> stringResource(R.string.note_reduced)
}
