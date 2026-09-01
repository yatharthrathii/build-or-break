package com.buildorbreak.core.testing.time

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Proves the test clock behaves before anything is built on top of it.
 *
 * If these fail, every scheduling test written after them is meaningless, so
 * this is the first test in the project on purpose.
 *
 * It lives in `:core:testing` rather than `:core:common` because
 * `:core:testing` already depends on `:core:common`, and testing the fixture
 * from the module it supports would create a project dependency cycle.
 */
class FakeTimeProviderTest {

    @Test
    fun `today is derived from the provider zone, not the system zone`() {
        // 2026-09-01T20:30Z is already 2026-09-02 in Kolkata.
        val time = FakeTimeProvider(
            initial = Instant.parse("2026-09-01T20:30:00Z"),
            currentZone = ZoneId.of("Asia/Kolkata"),
        )

        assertThat(time.today()).isEqualTo(LocalDate.of(2026, 9, 2))
    }

    @Test
    fun `moving zone changes the local view without changing the instant`() {
        val time = FakeTimeProvider(
            initial = Instant.parse("2026-09-01T20:30:00Z"),
            currentZone = ZoneId.of("Asia/Kolkata"),
        )
        val instantBefore = time.now()

        time.moveToZone(ZoneId.of("UTC"))

        assertThat(time.now()).isEqualTo(instantBefore)
        assertThat(time.today()).isEqualTo(LocalDate.of(2026, 9, 1))
    }

    @Test
    fun `advancing moves the clock forward by exactly the requested amount`() {
        val time = FakeTimeProvider(initial = Instant.parse("2026-09-01T08:00:00Z"))

        time.advanceByMinutes(90)

        assertThat(time.now()).isEqualTo(Instant.parse("2026-09-01T09:30:00Z"))
    }

    @Test
    fun `advancing backwards is rejected rather than silently applied`() {
        val time = FakeTimeProvider()

        assertThrows<IllegalArgumentException> {
            time.advanceBy(Duration.ofMinutes(-10))
        }
    }

    @Test
    fun `setting a wall clock time resolves through the current zone`() {
        val time = FakeTimeProvider(currentZone = ZoneId.of("Asia/Kolkata"))

        time.setTo(LocalDateTime.of(2026, 9, 1, 8, 0))

        // 08:00 in Kolkata is 02:30 UTC.
        assertThat(time.now()).isEqualTo(Instant.parse("2026-09-01T02:30:00Z"))
        assertThat(time.localNow()).isEqualTo(LocalDateTime.of(2026, 9, 1, 8, 0))
    }
}
