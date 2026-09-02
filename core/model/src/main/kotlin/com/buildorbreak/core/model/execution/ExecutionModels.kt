package com.buildorbreak.core.model.execution

import com.buildorbreak.core.model.enums.DayMode
import com.buildorbreak.core.model.enums.OccurrenceState
import com.buildorbreak.core.model.enums.SkipChip
import com.buildorbreak.core.model.enums.ValueKind
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * One item on one day. This is the record of what actually happened.
 *
 * architecture.md section 1: the plan is stored and what happened is stored, but
 * the resolved day is computed. An occurrence is materialised when an item is
 * first scheduled or first touched, because an alarm needs a concrete row to
 * point at.
 */
data class Occurrence(
    val id: Long,
    val itemId: Long,
    val date: LocalDate,
    /** What the resolver computed at scheduling time. */
    val plannedAt: LocalDateTime,
    /** What was handed to AlarmManager. Null for TIMELINE items. */
    val scheduledAt: Instant?,
    val firedAt: Instant?,
    val settledAt: Instant?,
    val state: OccurrenceState,
    val shiftMinutes: Int = 0,
    val snoozeCount: Int = 0,
    /** Distinguishes repeats of an INTERVAL item within one day. */
    val sequenceInDay: Int = 0,
) {
    val shift: Duration get() = shiftMinutes.minutes

    val isSettled: Boolean get() = state.isSettled

    val isDone: Boolean get() = state.isDone

    /** Where the item ended up after any snoozes. */
    val effectiveAt: LocalDateTime get() = plannedAt.plusMinutes(shiftMinutes.toLong())

    /**
     * The instant worth learning from, used by the median time shift detector.
     *
     * Only a completed item teaches anything about when something really
     * happens. Converting this to a local time needs a zone, which is the
     * domain's job, not the model's.
     */
    val learnableInstant: Instant? get() = if (isDone) settledAt else null
}

/**
 * Why something did not happen.
 *
 * Always optional, always after the fact. Asking someone to justify themselves
 * at the moment they are already having a bad day is how the data stops
 * arriving at all.
 */
data class SkipReason(
    val id: Long,
    val occurrenceId: Long,
    val chip: SkipChip?,
    val text: String?,
    val createdAt: Instant,
) {
    val isEmpty: Boolean get() = chip == null && text.isNullOrBlank()
}

/** A number the user logged: weight, reps, pages, minutes. */
data class Measurement(
    val id: Long,
    val itemId: Long,
    val occurrenceId: Long?,
    val date: LocalDate,
    val value: Double,
    val kind: ValueKind,
    val note: String? = null,
)

/** Which template ran on a date, and how far the whole day was shifted. */
data class DayLog(
    val date: LocalDate,
    val planId: Long,
    val templateId: Long,
    val dayShiftMinutes: Int,
    val mode: DayMode,
    val chosenAt: Instant,
) {
    val dayShift: Duration get() = dayShiftMinutes.minutes
}
