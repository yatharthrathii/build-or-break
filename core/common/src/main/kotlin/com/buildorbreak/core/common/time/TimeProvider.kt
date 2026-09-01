package com.buildorbreak.core.common.time

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The only sanctioned source of the current time in this project.
 *
 * rules.md section 4 forbids calling [Instant.now] or [LocalDateTime.now]
 * directly, anywhere, including in tests. Time is the core of this product. A
 * clock that cannot be controlled makes the entire timeline engine untestable,
 * and an untestable timeline engine is a product that quietly breaks on a
 * Wednesday six months from now.
 *
 * Inject this. Always.
 */
interface TimeProvider {
    /** The current instant on the system clock. */
    fun now(): Instant

    /** The zone the user is currently in. Can change while the app is running. */
    fun zone(): ZoneId

    /** Today's date in [zone]. */
    fun today(): LocalDate = LocalDate.ofInstant(now(), zone())

    /** The current local date and time in [zone]. */
    fun localNow(): LocalDateTime = LocalDateTime.ofInstant(now(), zone())
}

/**
 * The production implementation. Reads the real system clock and the real
 * default zone on every call, because both can change while the process is
 * alive: the user travels, or changes the device time.
 */
class SystemTimeProvider(
    private val clock: Clock = Clock.systemDefaultZone(),
) : TimeProvider {
    override fun now(): Instant = clock.instant()

    override fun zone(): ZoneId = ZoneId.systemDefault()
}
