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
 * **Loading is not the same as empty.** There is no spinner, but there is a
 * difference between "no plan" and "not read yet", and drawing the first as the
 * second is how somebody with six weeks of history gets shown "no plan yet" for
 * two frames on every cold start.
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
    /** The first open step, overdue or not. Null when everything is settled. */
    val next: NextUp?,
    val entries: ImmutableList<TimelineEntry>,
    /** Index of [next] in [entries], or -1. */
    val nowIndex: Int,
    val budget: BudgetNotice?,
    /** Null when alarms will work. Anything else is worth a line on screen. */
    val degradedTier: DeliveryTier?,
    val hasPlan: Boolean,
    /** True until the first day has been read. Nothing is drawn under the header. */
    val isLoading: Boolean = false,
    /** A sick day: every step with a smaller version runs as that version. */
    val isReduced: Boolean = false,
    /** How many steps actually run as their smaller version today. */
    val reducedCount: Int = 0,
    /** The day is empty only because the plan was made after these steps had passed. */
    val startsTomorrow: Boolean = false,
    /**
     * The last thing settled, while it can still be taken back.
     *
     * Null most of the time, and null again a few seconds after a tap. Held
     * outside the resolved day on purpose: the day is what the database says,
     * and this is a moment in the interface that no other screen shares.
     */
    val undo: UndoOffer? = null,
    /** A skip made outside the app that nobody has been asked about yet. */
    val askAbout: SkipAsk? = null,
    /**
     * The last thing the user asked for did not happen.
     *
     * Every action on this screen used to ignore its own result. A settle that
     * failed left the row looking done, because the optimistic entry was never
     * withdrawn, and the day the user could see stopped matching the day the
     * app had recorded. Silence is the worst possible answer here.
     */
    val actionFailed: Boolean = false,
    /** Every template on the plan, so the sheet can run a different one today. */
    val templates: ImmutableList<DayChoice> = persistentListOf(),
    val currentTemplateId: Long = 0,
    val planId: Long = 0,
) {
    val isEmptyDay: Boolean get() = hasPlan && entries.isEmpty()

    /** A sick day where nothing has a smaller version does nothing, and says so. */
    val sickDayChangedNothing: Boolean get() = isReduced && reducedCount == 0

    val isAllDone: Boolean get() = hasPlan && entries.isNotEmpty() && next == null

    /**
     * Everything is settled and none of it happened.
     *
     * rules.md section 2 rule 8: a poor day gets no praise. "Nothing left on
     * the rails" over a day where every step was skipped is the app
     * congratulating somebody for giving up, which is the fastest way to lose
     * their trust in everything else it says.
     */
    val keptNothing: Boolean get() = isAllDone && header.doneCount == 0

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

        /** Before the first read. Not the same as having no plan. */
        val Loading = Empty.copy(isLoading = true)
    }
}

/** Which of the three ways a step was settled. Changes only the wording. */
enum class SettleKind { DONE, MINIMUM, SKIPPED }

/**
 * A settle that has not yet become permanent.
 *
 * The tap this exists for is Done pressed on the row above the one meant, on a
 * phone held in one hand. Without a way back the only fix is the editor, and
 * the history quietly stops matching what happened. Every number in the app is
 * built on that history, so a wrong row is not a cosmetic problem.
 */
@Immutable
data class UndoOffer(val occurrenceId: Long, val title: String, val kind: SettleKind)

/**
 * Which of the three questions the skip sheet is asking.
 *
 * They read almost the same and mean quite different things, and getting the
 * wrong one is the difference between a sheet that sounds attentive and one
 * that sounds broken.
 */
enum class SkipAskMode {
    /** Its time has passed and nobody has said what happened. */
    HAPPENED,

    /** It has not come round yet. Deciding in advance is a real decision. */
    AHEAD,

    /** Already skipped, from a notification. The step is settled; only the reason is open. */
    AFTER_THE_FACT,
}

/**
 * One skip that arrived from a notification, and the question owed to it.
 *
 * Asked once, later, and never twice. See `ObserveUnexplainedSkipsUseCase` for
 * why the question has to be deferred rather than asked where the skip is made.
 */
@Immutable
data class SkipAsk(val occurrenceId: Long, val title: String)

/** One template, as something the day can be switched to. */
@Immutable
data class DayChoice(val id: Long, val name: String)

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
    /** This one rings and takes over the screen. */
    val isAlarm: Boolean = false,
    /** Running as its smaller version today. Done means the minimum was done. */
    val isReduced: Boolean = false,
    /** Its time has passed and nobody has said what happened. */
    val isOverdue: Boolean = false,
    /**
     * Its time has arrived, give or take the few minutes somebody might be early.
     *
     * A step four hours away is not something to tick off. Letting it be ticked
     * turns the day into a checklist that can be emptied at breakfast, and the
     * kept count into a number that means nothing.
     */
    val hasArrived: Boolean = true,
) {
    /** Only a materialised occurrence that has come round can be completed. */
    val isActionable: Boolean get() = occurrenceId > 0 && hasArrived

    /** Skipping in advance is allowed. Deciding not to do something later is a real decision. */
    val isSkippable: Boolean get() = occurrenceId > 0
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
    val isReduced: Boolean = false,
    /**
     * This one rings and takes over the screen. Everything else is a notification.
     *
     * Shown because it is the difference between an app that wakes somebody and
     * an app that does not, and because a pasted routine makes every step a
     * reminder unless the line said otherwise. Somebody who cannot see which
     * steps will ring finds out at six in the morning.
     */
    val isAlarm: Boolean = false,
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

    /** A sick day: the smaller version is what counts. */
    data object Reduced : EntryNote
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
