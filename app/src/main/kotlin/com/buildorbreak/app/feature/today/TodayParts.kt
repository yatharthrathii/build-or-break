package com.buildorbreak.app.feature.today

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import com.buildorbreak.core.designsystem.theme.Theme
import com.buildorbreak.core.designsystem.theme.TimeStyle
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
@Composable
internal fun RingRow(header: DayHeader, runDays: Int) {
    Column {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProgressRing(fraction = header.fraction)

            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(
                    text = stringResource(R.string.today_of, header.doneCount, header.total),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Label(text = stringResource(R.string.today_steps_kept), modifier = Modifier.padding(top = 5.dp))

                Text(
                    text = if (runDays > 0) {
                        pluralStringResource(R.plurals.today_run_days, runDays, runDays)
                    } else {
                        stringResource(R.string.today_run_none)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 9.dp),
                )
            }
        }

        HairlineRule()
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
internal fun ShiftBar(state: TodayUiState, onRunningLate: () -> Unit, onReset: () -> Unit) {
    if (state.isShifted) {
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
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            when {
                card != null -> NextUpPanel(card, onDone, onDoneMinimum, onSnooze, onSkip)
                allDone -> DayDonePanel()
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

/** The kicker, the time, the title and the badges. Everything above the button. */
@Composable
private fun NextUpHeading(card: NextUp) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 11.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Kicker(
            text = stringResource(R.string.today_next_up),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )

        Text(text = card.time, style = TimeStyle, color = MaterialTheme.colorScheme.onSurface)
    }

    Text(
        text = card.title,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 12.dp).padding(top = 6.dp),
    )

    Row(
        modifier = Modifier.padding(horizontal = 12.dp).padding(top = 7.dp, bottom = 12.dp),
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

    Row(modifier = Modifier.height(IntrinsicHeight)) {
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
            enabled = card.isActionable,
            onClick = { onSkip(card.occurrenceId) },
            modifier = Modifier.weight(1f),
            muted = true,
        )
    }
}

private val IntrinsicHeight = 40.dp

@Composable
private fun ActionCell(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
) {
    Text(
        text = text.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelMedium,
        color = when {
            !enabled -> Theme.colours.faint
            muted -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.onSurface
        },
        modifier = modifier
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(start = 14.dp, top = 13.dp, bottom = 13.dp),
    )
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .width(Theme.spacing.rule)
            .height(IntrinsicHeight)
            .background(MaterialTheme.colorScheme.onSurface),
    )
}

/** Every step settled. The card's place is taken by a quiet full stop. */
@Composable
private fun DayDonePanel() {
    Panel {
        Column(modifier = Modifier.padding(14.dp)) {
            Kicker(text = stringResource(R.string.today_all_done_kicker), color = MaterialTheme.colorScheme.primary)

            Text(
                text = stringResource(R.string.today_all_done_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 6.dp),
            )

            Text(
                text = stringResource(R.string.today_all_done_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 5.dp),
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
internal fun RunningLateSheet(onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 28.dp)) {
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

@Composable
internal fun noteText(note: EntryNote): String = when (note) {
    EntryNote.Pinned -> stringResource(R.string.note_pinned)
    is EntryNote.DoneAt -> when {
        note.overMinutes > 0 -> stringResource(R.string.note_done_over, note.time, note.overMinutes)
        note.overMinutes < 0 -> stringResource(R.string.note_done_early, note.time, -note.overMinutes)
        else -> stringResource(R.string.note_done_at, note.time)
    }

    is EntryNote.Moved -> if (note.minutes > 0) {
        stringResource(R.string.note_moved_later, note.minutes)
    } else {
        stringResource(R.string.note_moved_earlier, -note.minutes)
    }

    is EntryNote.Ends -> stringResource(R.string.note_ends, note.time)
    EntryNote.Missed -> stringResource(R.string.note_missed)
    EntryNote.Skipped -> stringResource(R.string.note_skipped)
    is EntryNote.Snoozed -> stringResource(R.string.note_snoozed, note.count)
    EntryNote.Degraded -> stringResource(R.string.note_degraded)
}
