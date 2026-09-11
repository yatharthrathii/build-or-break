package com.buildorbreak.core.domain.review

import com.buildorbreak.core.model.audit.DeliveryAudit
import com.buildorbreak.core.model.enums.DeliveryTier
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.jupiter.api.Test

private val NOW: Instant = Instant.parse("2026-01-05T12:00:00Z")

private const val WINDOW_DAYS = 14

class DeliveryStatsTest {

    private fun audit(id: Long, minutesAgo: Long, lateSeconds: Long? = 0): DeliveryAudit {
        val scheduled = NOW.minusSeconds(minutesAgo * 60)

        return DeliveryAudit(
            id = id,
            occurrenceId = id,
            scheduledFor = scheduled,
            firedAt = lateSeconds?.let { scheduled.plusSeconds(it) },
            tier = DeliveryTier.FULL_SCREEN_ALARM,
            deviceModel = "Pixel 8",
            manufacturer = "Google",
            sdkInt = 36,
            wasDeviceIdle = false,
            latencySeconds = lateSeconds,
        )
    }

    private fun statsOf(vararg rows: DeliveryAudit) = DeliveryStats.of(rows.toList(), WINDOW_DAYS, NOW)

    @Test
    fun `nothing scheduled yet is not a hundred percent of nothing`() {
        assertThat(statsOf().hasData).isFalse()
        assertThat(statsOf().accuracy).isEqualTo(0f)
    }

    @Test
    fun `an alarm that fired on the minute counts as on time`() {
        val stats = statsOf(audit(id = 1, minutesAgo = 60, lateSeconds = 3))

        assertThat(stats.onTime).isEqualTo(1)
        assertThat(stats.late).isEqualTo(0)
        assertThat(stats.accuracy).isEqualTo(1f)
    }

    @Test
    fun `an alarm four minutes late is late, not missed`() {
        // Different problem, different cause, different fix. Collapsing the two
        // hides the one the user can actually do something about.
        val stats = statsOf(audit(id = 1, minutesAgo = 60, lateSeconds = 240))

        assertThat(stats.late).isEqualTo(1)
        assertThat(stats.missed).isEqualTo(0)
        assertThat(stats.onTime).isEqualTo(0)
    }

    @Test
    fun `an alarm that never fired is missed`() {
        val stats = statsOf(audit(id = 1, minutesAgo = 60, lateSeconds = null))

        assertThat(stats.missed).isEqualTo(1)
        assertThat(stats.fired).isEqualTo(0)
    }

    @Test
    fun `an alarm still in the future is not counted against the phone`() {
        // Otherwise the figure would get worse every time somebody opened the
        // screen before their evening alarms had happened.
        val stats = statsOf(audit(id = 1, minutesAgo = -180, lateSeconds = null))

        assertThat(stats.scheduled).isEqualTo(0)
        assertThat(stats.hasData).isFalse()
    }

    @Test
    fun `the middle lateness is reported, so one bad night does not define the week`() {
        val stats = statsOf(
            audit(id = 1, minutesAgo = 300, lateSeconds = 120),
            audit(id = 2, minutesAgo = 240, lateSeconds = 180),
            audit(id = 3, minutesAgo = 180, lateSeconds = 3600),
        )

        assertThat(stats.medianLateSeconds).isEqualTo(180)
        assertThat(stats.worstLateSeconds).isEqualTo(3600)
    }

    @Test
    fun `with nothing late there is no lateness to report`() {
        val stats = statsOf(audit(id = 1, minutesAgo = 60, lateSeconds = 0))

        assertThat(stats.medianLateSeconds).isNull()
        assertThat(stats.worstLateSeconds).isNull()
    }

    @Test
    fun `accuracy counts only the ones that arrived on time, out of everything due`() {
        val stats = statsOf(
            audit(id = 1, minutesAgo = 300, lateSeconds = 0),
            audit(id = 2, minutesAgo = 240, lateSeconds = 600),
            audit(id = 3, minutesAgo = 180, lateSeconds = null),
            audit(id = 4, minutesAgo = 120, lateSeconds = 5),
        )

        assertThat(stats.scheduled).isEqualTo(4)
        assertThat(stats.accuracy).isEqualTo(0.5f)
    }
}
