package com.buildorbreak.core.testing.time

import com.buildorbreak.core.common.time.TimeProvider
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * A controllable clock for tests.
 *
 * Every test in this project that touches time uses this. It supports moving
 * time forward, jumping to a specific wall clock moment, and changing zone
 * mid test, which is how the travel and DST cases in the M2 exit criteria are
 * exercised.
 */
class FakeTimeProvider(
    initial: Instant = Instant.parse("2026-09-01T08:00:00Z"),
    private var currentZone: ZoneId = ZoneId.of("Asia/Kolkata"),
) : TimeProvider {

    private var current: Instant = initial

    override fun now(): Instant = current

    override fun zone(): ZoneId = currentZone

    /** Moves the clock forward. Negative durations are rejected, not silently applied. */
    fun advanceBy(duration: Duration) {
        require(!duration.isNegative) { "Time does not move backwards. Use setTo() if that is the intent." }
        current = current.plus(duration)
    }

    fun advanceByMinutes(minutes: Long): Unit = advanceBy(Duration.ofMinutes(minutes))

    fun advanceByHours(hours: Long): Unit = advanceBy(Duration.ofHours(hours))

    fun advanceByDays(days: Long): Unit = advanceBy(Duration.ofDays(days))

    /** Jumps to an absolute instant. Used for device time change tests. */
    fun setTo(instant: Instant) {
        current = instant
    }

    /** Jumps to a wall clock moment in the current zone. */
    fun setTo(local: LocalDateTime) {
        current = local.atZone(currentZone).toInstant()
    }

    /** Simulates the user travelling, or a device timezone change. */
    fun moveToZone(zone: ZoneId) {
        currentZone = zone
    }
}
