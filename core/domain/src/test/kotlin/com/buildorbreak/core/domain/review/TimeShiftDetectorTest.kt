package com.buildorbreak.core.domain.review

import com.buildorbreak.core.testing.fixtures.ExecutionFixtures
import com.buildorbreak.core.testing.fixtures.GoalFixtures
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.minutes
import org.junit.jupiter.api.Test

class TimeShiftDetectorTest {

    private val detector = TimeShiftDetector()
    private val zone = ExecutionFixtures.ZONE
    private val start = GoalFixtures.START

    /** Completions on consecutive days, each the given number of minutes late. */
    private fun lateBy(vararg minutesLate: Long) = minutesLate.mapIndexed { index, late ->
        ExecutionFixtures.doneLate(itemId = 1, date = start.plusDays(index.toLong()), minutesLate = late)
    }

    @Test
    fun `four completions is not enough to draw a conclusion from`() {
        assertThat(detector.detect(1, lateBy(60, 60, 60, 60), zone)).isNull()
    }

    @Test
    fun `an item that is habitually an hour late is reported as an hour late`() {
        val shift = detector.detect(1, lateBy(55, 60, 60, 65, 60), zone)

        assertThat(shift).isNotNull()
        assertThat(shift?.median).isEqualTo(60.minutes)
        assertThat(shift?.isLate).isTrue()
        assertThat(shift?.samples).isEqualTo(5)
    }

    @Test
    fun `one extreme day does not move the answer`() {
        // Four ordinary days and one that ran five hours over. A mean would
        // suggest moving the slot by an hour. The median does not.
        val shift = detector.detect(1, lateBy(5, 5, 300, 5, 5), zone)

        assertThat(shift).isNull()
    }

    @Test
    fun `a drift smaller than the threshold is ordinary life, not a signal`() {
        assertThat(detector.detect(1, lateBy(10, 12, 8, 15, 11), zone)).isNull()
    }

    @Test
    fun `an item habitually done early is reported as early`() {
        val shift = detector.detect(1, lateBy(-40, -35, -45, -40, -38), zone)

        assertThat(shift).isNotNull()
        assertThat(shift?.isLate).isFalse()
        assertThat(shift?.median).isEqualTo((-40).minutes)
    }

    @Test
    fun `only completed occurrences teach anything`() {
        val mixed = lateBy(60, 60, 60) + listOf(
            ExecutionFixtures.missed(itemId = 1, date = start.plusDays(3), id = 91),
            ExecutionFixtures.missed(itemId = 1, date = start.plusDays(4), id = 92),
        )

        // Three completions and two misses is still only three samples.
        assertThat(detector.detect(1, mixed, zone)).isNull()
    }

    @Test
    fun `another item on the same list is ignored`() {
        val other = (0..5).map {
            ExecutionFixtures.doneLate(itemId = 2, date = start.plusDays(it.toLong()), minutesLate = 90, id = 200L + it)
        }

        assertThat(detector.detect(1, lateBy(60, 60, 60) + other, zone)).isNull()
    }

    @Test
    fun `an even number of samples averages the middle pair`() {
        val shift = detector.detect(1, lateBy(50, 60, 70, 80, 40, 90), zone)

        // Sorted: 40 50 60 70 80 90. The middle pair is 60 and 70.
        assertThat(shift?.median).isEqualTo(65.minutes)
    }
}
