package com.buildorbreak.core.domain.goal

import com.buildorbreak.core.model.goal.Reading
import com.buildorbreak.core.testing.fixtures.GoalFixtures
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

private const val TOLERANCE = 1e-9

class MovingAverageTest {

    private val average = MovingAverage()
    private val start = GoalFixtures.START

    @Test
    fun `no readings smooth to nothing`() {
        assertThat(average.smooth(emptyList())).isEmpty()
    }

    @Test
    fun `a single reading smooths to itself`() {
        assertThat(average.smooth(GoalFixtures.consecutive(60.0))).containsExactly(60.0)
    }

    @Test
    fun `early readings average over the days that exist rather than being dropped`() {
        val smoothed = average.smooth(GoalFixtures.consecutive(60.0, 62.0, 64.0))

        // A goal has to show something on day two. The average of two days is
        // the truthful answer to what two days say.
        assertThat(smoothed).containsExactly(60.0, 61.0, 62.0).inOrder()
    }

    @Test
    fun `the window slides once it is full so an old reading stops counting`() {
        // Seven days of 10, then one day of 80. The eighth window drops day one.
        val readings = GoalFixtures.consecutive(10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 80.0)

        val smoothed = average.smooth(readings)

        assertThat(smoothed[6]).isWithin(TOLERANCE).of(10.0)
        assertThat(smoothed[7]).isWithin(TOLERANCE).of(20.0)
    }

    @Test
    fun `the window is calendar days, not a count of readings`() {
        // Two readings a month apart are not each other's neighbours, however
        // few readings sit between them.
        val readings = listOf(Reading(start, 60.0), Reading(start.plusDays(30), 70.0))

        assertThat(average.smooth(readings)).containsExactly(60.0, 70.0).inOrder()
    }

    @Test
    fun `readings given out of order are smoothed in date order`() {
        val jumbled = listOf(
            Reading(start.plusDays(2), 64.0),
            Reading(start, 60.0),
            Reading(start.plusDays(1), 62.0),
        )

        assertThat(average.smooth(jumbled)).containsExactly(60.0, 61.0, 62.0).inOrder()
    }

    @Test
    fun `a window of one day is the raw series`() {
        val raw = GoalFixtures.consecutive(60.0, 65.0, 55.0)

        assertThat(MovingAverage(windowDays = 1).smooth(raw)).containsExactly(60.0, 65.0, 55.0).inOrder()
    }

    @Test
    fun `a window shorter than a day is rejected at construction`() {
        assertThrows<IllegalArgumentException> { MovingAverage(windowDays = 0) }
    }
}
