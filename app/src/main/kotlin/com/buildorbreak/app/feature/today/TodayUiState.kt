package com.buildorbreak.app.feature.today

import androidx.compose.runtime.Immutable
import com.buildorbreak.core.model.enums.DeliveryTier
import com.buildorbreak.core.model.enums.Salience
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
 * **Null means absent, not pending.** `close == null` means the day is not over,
 * not that the app is still deciding.
 *
 * **Lists are immutable.** `ImmutableList` is what lets Compose strong skipping
 * actually skip. A plain `List` parameter is treated as unstable and every row
 * recomposes whenever anything on the screen changes.
 */
@Immutable
data class TodayUiState(
    val header: DayHeader,
    val entries: ImmutableList<TimelineEntry>,
    /** Index of the next unsettled entry, or -1 when the day is done. */
    val nowIndex: Int,
    val budget: BudgetNotice?,
    /** Null when alarms will work. Anything else is worth a line on screen. */
    val degradedTier: DeliveryTier?,
    val hasPlan: Boolean,
) {
    val isEmptyDay: Boolean get() = hasPlan && entries.isEmpty()

    companion object {
        val Empty = TodayUiState(
            header = DayHeader(date = "", subtitle = "", doneCount = 0, total = 0),
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
 * Counts rather than a percentage. "4 of 11" is a fact somebody can check
 * against the list below it; "36%" is a number they have to trust.
 */
@Immutable
data class DayHeader(
    val date: String,
    val subtitle: String,
    val doneCount: Int,
    val total: Int,
)

/**
 * One row, already formatted.
 *
 * The time arrives as a string and the flags as booleans, so the composable has
 * no `LocalDateTime` to format and no state to interpret. A leaf that formats
 * its own time is a leaf that needs a locale, a zone and a test.
 */
@Immutable
data class TimelineEntry(
    val occurrenceId: Long,
    val itemId: Long,
    val time: String,
    val title: String,
    val detail: String?,
    val salience: Salience,
    val isDone: Boolean,
    val isMissed: Boolean,
    val isPinned: Boolean,
    val isDegraded: Boolean,
    val hasMinimum: Boolean,
) {
    /** Settled either way. Nothing more is going to be asked of the user. */
    val isSettled: Boolean get() = isDone || isMissed

    /** Only a materialised occurrence can be completed from the screen. */
    val isActionable: Boolean get() = occurrenceId > 0 && !isSettled
}

/**
 * The day would make more noise than rules.md allows.
 *
 * Counts only. The sentence built from them is a string resource, because
 * rules.md section 9 keeps every user visible word out of Kotlin so that adding
 * Hindi and a Hinglish variant later is a translation job rather than a refactor.
 *
 * Shown inline, never as a dialog, and never as a refusal. Every alarm is still
 * scheduled: silently dropping the fourth one would be a routine app skipping
 * part of somebody's routine.
 */
@Immutable
data class BudgetNotice(val alarms: Int, val notifications: Int, val alarmsOverBudget: Boolean)
