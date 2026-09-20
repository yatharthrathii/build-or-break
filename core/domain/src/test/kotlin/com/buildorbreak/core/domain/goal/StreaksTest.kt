package com.buildorbreak.core.domain.goal

import com.buildorbreak.core.testing.fixtures.GoalFixtures
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.jupiter.api.Test

class StreaksTest {

    private val today: LocalDate = LocalDate.of(2026, 9, 7)

    @Test
    fun `counts consecutive kept days ending yesterday`() {
        val closes = GoalFixtures.closes(from = today.minusDays(3), days = 3, itemsDone = 9, itemsTotal = 10)

        assertThat(Streaks.currentRun(closes, today)).isEqualTo(3)
    }

    @Test
    fun `today does not count, because it is still open`() {
        val closes = GoalFixtures.closes(from = today, days = 1)

        assertThat(Streaks.currentRun(closes, today)).isEqualTo(0)
    }

    @Test
    fun `a poor day breaks the run`() {
        val closes = GoalFixtures.closes(from = today.minusDays(5), days = 2, itemsDone = 10, itemsTotal = 10) +
            GoalFixtures.close(date = today.minusDays(3), itemsDone = 2, itemsMissed = 8) +
            GoalFixtures.closes(from = today.minusDays(2), days = 2, itemsDone = 10, itemsTotal = 10)

        assertThat(Streaks.currentRun(closes, today)).isEqualTo(2)
    }

    @Test
    fun `a missing day breaks the run rather than being assumed kept`() {
        val closes = GoalFixtures.closes(from = today.minusDays(4), days = 2) +
            GoalFixtures.closes(from = today.minusDays(1), days = 1)

        // Two days ago was never closed. The run is yesterday alone.
        assertThat(Streaks.currentRun(closes, today)).isEqualTo(1)
    }

    @Test
    fun `an ok day counts, only a poor one breaks`() {
        val closes = listOf(GoalFixtures.close(date = today.minusDays(1), itemsDone = 6, itemsMissed = 4))

        assertThat(Streaks.currentRun(closes, today)).isEqualTo(1)
    }

    @Test
    fun `nothing closed is a run of zero`() {
        assertThat(Streaks.currentRun(emptyList(), today)).isEqualTo(0)
    }

    @Test
    fun `a frozen day carries the run across the gap`() {
        val before = GoalFixtures.closes(from = today.minusDays(4), days = 2, itemsDone = 9, itemsTotal = 10)
        val after = GoalFixtures.closes(from = today.minusDays(1), days = 1, itemsDone = 9, itemsTotal = 10)

        val run = Streaks.currentRun(before + after, today, frozen = setOf(today.minusDays(2)))

        assertThat(run).isEqualTo(3)
    }

    @Test
    fun `a freeze holds the run open but is not itself a kept day`() {
        val kept = GoalFixtures.closes(from = today.minusDays(1), days = 1, itemsDone = 9, itemsTotal = 10)

        val run = Streaks.currentRun(kept, today, frozen = setOf(today.minusDays(2)))

        assertThat(run).isEqualTo(1)
    }
}
