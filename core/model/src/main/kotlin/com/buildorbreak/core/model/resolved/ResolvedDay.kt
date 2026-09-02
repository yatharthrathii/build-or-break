package com.buildorbreak.core.model.resolved

import com.buildorbreak.core.model.enums.DayMode
import com.buildorbreak.core.model.enums.Salience
import com.buildorbreak.core.model.execution.Occurrence
import com.buildorbreak.core.model.plan.Block
import com.buildorbreak.core.model.plan.DayTemplate
import com.buildorbreak.core.model.plan.Item
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.time.Duration

/**
 * One day, fully resolved.
 *
 * architecture.md section 1: this is computed on every read and never stored.
 * The moment a resolved schedule is persisted it can disagree with the plan,
 * and then nobody can tell which of the two is right.
 */
data class ResolvedDay(
    val date: LocalDate,
    val template: DayTemplate,
    val entries: List<ResolvedEntry>,
    val dayShift: Duration,
    val mode: DayMode,
    val budgetWarning: BudgetWarning?,
    val issues: List<ResolveIssue>,
) {
    val doneCount: Int get() = entries.count { it.occurrence?.isDone == true }

    val settledCount: Int get() = entries.count { it.occurrence?.isSettled == true }

    val total: Int get() = entries.size

    fun entryFor(itemId: Long): ResolvedEntry? = entries.firstOrNull { it.item.id == itemId }

    /** The next thing that has not been settled, or null when the day is done. */
    fun next(after: LocalDateTime): ResolvedEntry? =
        entries.firstOrNull { it.at >= after && it.occurrence?.isSettled != true }
}

/** One item at one concrete moment. */
data class ResolvedEntry(
    val item: Item,
    val block: Block?,
    val at: LocalDateTime,
    /** Null until the occurrence has been materialised. */
    val occurrence: Occurrence?,
    /** Set for INTERVAL items, which resolve to several entries in a day. */
    val sequenceInDay: Int = 0,
    /** True when this entry was resolved from a fallback because of an issue. */
    val degraded: Boolean = false,
) {
    val salience: Salience get() = block?.salience ?: item.salience

    val isInBlock: Boolean get() = block != null
}

/**
 * Raised when a day exceeds the notification budget in rules.md section 1.
 *
 * Surfaced inline on Today, never as a dialog, and never by silently firing
 * everything anyway.
 */
data class BudgetWarning(val alarmCount: Int, val notifyCount: Int) {
    companion object {
        const val MAX_ALARMS = 3
        const val MAX_NOTIFY = 10
    }
}

/**
 * Something wrong with the plan that the resolver worked around.
 *
 * The day still resolves. A broken anchor never produces an empty screen, it
 * produces a degraded entry and a note the editor can show.
 */
sealed interface ResolveIssue {
    /** Two or more RELATIVE items point at each other. Broken at the weakest link. */
    data class AnchorCycle(val itemIds: List<Long>) : ResolveIssue

    /** A RELATIVE item whose parent no longer exists on this template. */
    data class MissingParent(val itemId: Long, val parentItemId: Long) : ResolveIssue

    /** A shift pushed an item past midnight. Clamped to 23:59. */
    data class ClampedToMidnight(val itemId: Long) : ResolveIssue
}

/**
 * What moving one item does to the rest of the day.
 *
 * This is the snooze consequence preview. Every competing app offers a snooze
 * and none of them tell you what it costs. It is only possible because the
 * resolver is a pure function that can be run twice and diffed.
 */
data class CascadePreview(
    val itemId: Long,
    val shift: Duration,
    val moved: List<MovedEntry>,
    val collisions: List<Collision>,
) {
    val movesNothing: Boolean get() = moved.isEmpty()
}

data class MovedEntry(val itemId: Long, val title: String, val from: LocalDateTime, val to: LocalDateTime)

/** Two items ending up closer than a threshold after a shift. */
data class Collision(val firstItemId: Long, val secondItemId: Long, val gap: Duration)
