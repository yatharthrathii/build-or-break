package com.buildorbreak.core.domain.goal

import com.buildorbreak.core.testing.fixtures.ExecutionFixtures
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.jupiter.api.Test

class ItemRunsTest {

    private val today: LocalDate = LocalDate.of(2026, 9, 7)

    @Test
    fun `counts consecutive done days for one item, ending today`() {
        val rows = (0L until 5L).map { ExecutionFixtures.done(itemId = 1, date = today.minusDays(it), id = 10 + it) }

        assertThat(ItemRuns.longest(rows, today)).isEqualTo(ItemRun(itemId = 1, days = 5))
    }

    @Test
    fun `a missed day breaks the run`() {
        val rows = listOf(
            ExecutionFixtures.done(itemId = 1, date = today, id = 1),
            ExecutionFixtures.missed(itemId = 1, date = today.minusDays(1), id = 2),
            ExecutionFixtures.done(itemId = 1, date = today.minusDays(2), id = 3),
        )

        assertThat(ItemRuns.longest(rows, today)?.days).isEqualTo(1)
    }

    @Test
    fun `a day the item did not run on does not break the run`() {
        // Weekday only step: nothing on the weekend, done on Friday and Monday.
        val rows = listOf(
            ExecutionFixtures.done(itemId = 1, date = today, id = 1),
            ExecutionFixtures.done(itemId = 1, date = today.minusDays(3), id = 2),
        )

        assertThat(ItemRuns.longest(rows, today)?.days).isEqualTo(2)
    }

    @Test
    fun `the longest run wins, whichever item holds it`() {
        val rows = listOf(
            ExecutionFixtures.done(itemId = 1, date = today, id = 1),
            ExecutionFixtures.done(itemId = 2, date = today, id = 2),
            ExecutionFixtures.done(itemId = 2, date = today.minusDays(1), id = 3),
        )

        assertThat(ItemRuns.longest(rows, today)).isEqualTo(ItemRun(itemId = 2, days = 2))
    }

    @Test
    fun `nothing done is no run at all`() {
        val rows = listOf(ExecutionFixtures.missed(itemId = 1, date = today, id = 1))

        assertThat(ItemRuns.longest(rows, today)).isNull()
    }
}
