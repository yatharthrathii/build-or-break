package com.buildorbreak.core.domain.review

import com.buildorbreak.core.model.enums.SkipChip
import com.buildorbreak.core.model.execution.Occurrence
import com.buildorbreak.core.testing.fixtures.ExecutionFixtures
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.jupiter.api.Test

class SkipPatternDetectorTest {

    private val detector = SkipPatternDetector()

    /** 2026-01-05 is a Monday, so weekday arithmetic below reads plainly. */
    private val monday: LocalDate = LocalDate.of(2026, 1, 5)

    private fun missedOn(itemId: Long, vararg dates: LocalDate): List<Occurrence> = dates.mapIndexed { index, date ->
        ExecutionFixtures.missed(itemId = itemId, date = date, id = itemId * 100 + index)
    }

    private fun doneOn(itemId: Long, vararg dates: LocalDate): List<Occurrence> = dates.mapIndexed { index, date ->
        ExecutionFixtures.done(itemId = itemId, date = date, id = itemId * 100 + 50 + index)
    }

    @Test
    fun `two chances is not enough to call anything a pattern`() {
        val occurrences = missedOn(1, monday, monday.plusDays(1))

        assertThat(detector.detect(occurrences)).isEmpty()
    }

    @Test
    fun `one miss in a good week is not a pattern`() {
        val occurrences = missedOn(1, monday) +
            doneOn(1, monday.plusDays(1), monday.plusDays(2), monday.plusDays(3), monday.plusDays(4))

        assertThat(detector.detect(occurrences)).isEmpty()
    }

    @Test
    fun `missing most of the chances is a pattern`() {
        val occurrences = missedOn(1, monday, monday.plusDays(1), monday.plusDays(2)) +
            doneOn(1, monday.plusDays(3), monday.plusDays(4))

        val pattern = detector.detect(occurrences).single()

        assertThat(pattern.itemId).isEqualTo(1L)
        assertThat(pattern.misses).isEqualTo(3)
        assertThat(pattern.opportunities).isEqualTo(5)
    }

    @Test
    fun `a day still pending is not a chance that was missed`() {
        // Three misses and four days that have not happened yet is not a
        // seven out of seven failure, and counting it as one would make every
        // plan look broken by lunchtime.
        val pending = (3..6).map { ExecutionFixtures.occurrence(itemId = 1, id = 300L + it) }

        assertThat(detector.detect(missedOn(1, monday, monday.plusDays(1)) + pending)).isEmpty()
    }

    // The reason, not the count -----------------------------------------------

    @Test
    fun `work getting in the way is a timing problem`() {
        val missed = missedOn(1, monday, monday.plusDays(1), monday.plusDays(2))
        val reasons = missed.map { ExecutionFixtures.skipReason(it.id, SkipChip.WORK_CAME_UP) }

        val pattern = detector.detect(missed, reasons).single()

        assertThat(pattern.cause).isEqualTo(SkipCause.TIMING)
        assertThat(pattern.cause.suggestsMoving).isTrue()
    }

    @Test
    fun `not being in the mood is a motivation problem, and the fix is different`() {
        val missed = missedOn(1, monday, monday.plusDays(1), monday.plusDays(2))
        val reasons = missed.map { ExecutionFixtures.skipReason(it.id, SkipChip.NOT_IN_MOOD) }

        val pattern = detector.detect(missed, reasons).single()

        assertThat(pattern.cause).isEqualTo(SkipCause.MOTIVATION)
        assertThat(pattern.cause.suggestsMoving).isFalse()
    }

    @Test
    fun `forgetting is a reminder problem`() {
        val missed = missedOn(1, monday, monday.plusDays(1), monday.plusDays(2))
        val reasons = missed.map { ExecutionFixtures.skipReason(it.id, SkipChip.FORGOT) }

        assertThat(detector.detect(missed, reasons).single().cause).isEqualTo(SkipCause.REMINDER)
    }

    @Test
    fun `with no reasons given the detector still works and says it does not know`() {
        val missed = missedOn(1, monday, monday.plusDays(1), monday.plusDays(2))

        val pattern = detector.detect(missed).single()

        assertThat(pattern.misses).isEqualTo(3)
        assertThat(pattern.cause).isEqualTo(SkipCause.UNKNOWN)
    }

    @Test
    fun `being unwell is not a plan problem and does not drive a suggestion`() {
        val missed = missedOn(1, monday, monday.plusDays(1), monday.plusDays(2))
        val reasons = missed.map { ExecutionFixtures.skipReason(it.id, SkipChip.UNWELL) }

        assertThat(detector.detect(missed, reasons).single().cause).isEqualTo(SkipCause.UNKNOWN)
    }

    // Weekday clustering ------------------------------------------------------

    @Test
    fun `misses that land on the same weekday every time are reported as that day`() {
        val occurrences = missedOn(1, monday, monday.plusWeeks(1), monday.plusWeeks(2)) +
            doneOn(1, monday.plusDays(1), monday.plusDays(2), monday.plusDays(3), monday.plusDays(4))

        val pattern = detector.detect(occurrences).single()

        assertThat(pattern.weekday).isEqualTo(DayOfWeek.MONDAY)
    }

    @Test
    fun `misses scattered across the week are not blamed on a weekday`() {
        val occurrences = missedOn(1, monday, monday.plusDays(2), monday.plusDays(4)) +
            doneOn(1, monday.plusDays(1), monday.plusDays(3))

        assertThat(detector.detect(occurrences).single().weekday).isNull()
    }

    @Test
    fun `the worst item comes first when several are struggling`() {
        val occurrences = missedOn(1, monday, monday.plusDays(1), monday.plusDays(2)) +
            missedOn(2, monday, monday.plusDays(1), monday.plusDays(2), monday.plusDays(3), monday.plusDays(4))

        assertThat(detector.detect(occurrences).map { it.itemId }).containsExactly(2L, 1L).inOrder()
    }
}
