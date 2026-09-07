package com.buildorbreak.app.feature.today

import androidx.compose.runtime.Immutable
import com.buildorbreak.core.model.enums.DeliveryTier
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Everything the Today screen draws, and nothing it has to work out.
 *
 * architecture.md section 9. Three rules shape this file:
 *
 * **No loading flag.** The resolver is pure and runs in well under a frame, so
 * there is nothing to wait for after the first emission. A spinner here would be
 * shown for a length of time nobody can perceive and would then have to be
 * designed, tested and reasoned about forever.
 *
 * **Null means absent, not pending.** `next == null` means the day is done,
 * not that the app is still deciding.
 *
 * **Facts, not sentences.** A kind is an enum and a note is a sealed type with
 * numbers in it. The screen turns them into words from string resources, so
 * adding Hindi is a translation job and not a rewrite of this file.
 */
@Immutable
data class TodayUiState(
    val header: DayHeader,
    /** Consecutive kept days up to yesterday. Zero on a first day. */
    val runDays: Int,
    /** How far the whole day has been moved. Zero means on time. */
    val shiftMinutes: Int,
    /** How many unsettled, unpinned steps the shift moved. */
    val movedCount: Int,
    /** The one thing the day has reached. Null when everything is settled. */
    val next: NextUp?,
    val entries: ImmutableList<TimelineEntry>,
    /** Index of [next] in [entries], or -1. */
    val nowIndex: Int,
    val budget: BudgetNotice?,
    /** Null when alarms will work. Anything else is worth a line on screen. */
    val degradedTier: DeliveryTier?,
    val hasPlan: Boolean,
) {
    val isEmptyDay: Boolean get() = hasPlan && entries.isEmpty()

    val isAllDone: Boolean get() = hasPlan && entries.isNotEmpty() && next == null

    val isShifted: Boolean get() = shiftMinutes != 0

    companion object {
        val Empty = TodayUiState(
            header = DayHeader(dateLine = "", templateName = "", clock = "", doneCount = 0, total = 0),
            runDays = 0,
            shiftMinutes = 0,
            movedCount = 0,
            next = null,
            entries = persistentListOf(),
            nowIndex = -1,
            budget = null,
            degradedTier = null,
            hasPlan = false,
        )
    }
}

/**
 * The top of the screen.
 *
 * Counts rather than a percentage. "4 of 9" is a fact somebody can check
 * against the list below it; "44%" is a number they have to trust.
 */
@Immutable
data class DayHeader(
    val dateLine: String,
    val templateName: String,
    val clock: String,
    val doneCount: Int,
    val total: Int,
) {
    val fraction: Float get() = if (total == 0) 0f else doneCount.toFloat() / total
}

/** The card. The same facts as a row, plus what the buttons need. */
@Immutable
data class NextUp(
    val occurrenceId: Long,
    val itemId: Long,
    val time: String,
    val title: String,
    val kind: EntryKind,
    val note: EntryNote?,
    val durationMinutes: Int?,
    val hasMinimum: Boolean,
) {
    /** Only a materialised occurrence can be completed from the screen. */
    val isActionable: Boolean get() = occurrenceId > 0
}

/** One row, already formatted. */
@Immutable
data class TimelineEntry(
    val occurrenceId: Long,
    val itemId: Long,
    val time: String,
    val title: String,
    val kind: EntryKind,
    val note: EntryNote?,
    val isDone: Boolean,
    val isMissed: Boolean,
    /** Which repeat of the item this is. Zero for anything that runs once. */
    val sequence: Int = 0,
) {
    val isSettled: Boolean get() = isDone || isMissed
}

/** What kind of time a step keeps. Shown as a badge. */
@Immutable
sealed interface EntryKind {
    data object Fixed : EntryKind

    data class After(val parentTitle: String, val offsetMinutes: Int) : EntryKind

    data class Window(val from: String, val to: String) : EntryKind

    data class Every(val minutes: Int) : EntryKind
}

/**
 * The one thing worth saying about a row beside its badge.
 *
 * At most one. A row that said "pinned, moved, snoozed twice, ends 10:20"
 * would be a row nobody read, so the ViewModel picks the fact that matters
 * most and the rest is on the editor.
 */
@Immutable
sealed interface EntryNote {
    /** Does not move when the day shifts. */
    data object Pinned : EntryNote

    /** Done at [time]; [overMinutes] positive when late, negative when early. */
    data class DoneAt(val time: String, val overMinutes: Int) : EntryNote

    /** Moved from where it was planned, by [minutes]. */
    data class Moved(val minutes: Int) : EntryNote

    /** A window that closes at [time]. */
    data class Ends(val time: String) : EntryNote

    data object Missed : EntryNote

    data object Skipped : EntryNote

    data class Snoozed(val count: Int) : EntryNote

    /** The resolver had to guess. Worth a look on the editor. */
    data object Degraded : EntryNote
}

/**
 * The day would make more noise than rules.md allows.
 *
 * Counts only. Shown inline, never as a dialog, and never as a refusal. Every
 * alarm is still scheduled: silently dropping the fourth one would be a routine
 * app skipping part of somebody's routine.
 */
@Immutable
data class BudgetNotice(val alarms: Int, val notifications: Int, val alarmsOverBudget: Boolean)
