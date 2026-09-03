package com.buildorbreak.core.testing.fixtures

import com.buildorbreak.core.model.enums.OccurrenceState
import com.buildorbreak.core.model.execution.Occurrence
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Builders for execution types, the counterpart to [PlanFixtures].
 *
 * The plan side says what was meant to happen. This side says what did. A
 * resolver test almost always needs one row of the second kind, and spelling
 * out eleven fields to say "this one was done at 08:40" hides the assertion.
 */
object ExecutionFixtures {

    /** A Monday, so a test that says nothing about weekdays still lands on one. */
    val DATE: LocalDate = LocalDate.of(2026, 1, 5)

    /** Fixed rather than the system default, so a test reads the same everywhere. */
    val ZONE: ZoneId = ZoneId.of("Asia/Kolkata")

    fun occurrence(
        itemId: Long,
        id: Long = itemId,
        date: LocalDate = DATE,
        plannedAt: LocalDateTime = date.atTime(PlanFixtures.DEFAULT_HOUR, 0),
        state: OccurrenceState = OccurrenceState.PENDING,
        scheduledAt: Instant? = null,
        firedAt: Instant? = null,
        settledAt: Instant? = null,
        shiftMinutes: Int = 0,
        snoozeCount: Int = 0,
        sequenceInDay: Int = 0,
    ): Occurrence = Occurrence(
        id = id,
        itemId = itemId,
        date = date,
        plannedAt = plannedAt,
        scheduledAt = scheduledAt,
        firedAt = firedAt,
        settledAt = settledAt,
        state = state,
        shiftMinutes = shiftMinutes,
        snoozeCount = snoozeCount,
        sequenceInDay = sequenceInDay,
    )

    /** Completed, at a real local moment. This is what a RELATIVE child hangs off. */
    fun completedAt(itemId: Long, at: LocalDateTime, zone: ZoneId = ZONE): Occurrence = occurrence(
        itemId = itemId,
        state = OccurrenceState.DONE,
        settledAt = at.atZone(zone).toInstant(),
    )

    /** Settled without happening. Carries a time, but never a learnable one. */
    fun skipped(itemId: Long, zone: ZoneId = ZONE): Occurrence = occurrence(
        itemId = itemId,
        state = OccurrenceState.SKIPPED,
        settledAt = DATE.atTime(PlanFixtures.DEFAULT_HOUR, 0).atZone(zone).toInstant(),
    )

    /** Still pending, but moved. The children are expected to move with it. */
    fun snoozedBy(itemId: Long, minutes: Int): Occurrence = occurrence(
        itemId = itemId,
        state = OccurrenceState.SNOOZED,
        shiftMinutes = minutes,
        snoozeCount = 1,
    )
}
