package com.buildorbreak.core.domain.goal

import com.buildorbreak.core.model.enums.GoalKind
import com.buildorbreak.core.model.enums.ValueKind
import com.buildorbreak.core.model.execution.Measurement
import com.buildorbreak.core.model.execution.Occurrence
import com.buildorbreak.core.model.goal.DayClose
import com.buildorbreak.core.model.goal.GoalProgress
import com.buildorbreak.core.model.goal.Reading
import com.buildorbreak.core.testing.fixtures.ExecutionFixtures.done
import com.buildorbreak.core.testing.fixtures.ExecutionFixtures.missed
import com.buildorbreak.core.testing.fixtures.GoalFixtures
import com.buildorbreak.core.testing.fixtures.GoalFixtures.close
import com.buildorbreak.core.testing.fixtures.GoalFixtures.goal
import com.buildorbreak.core.testing.fixtures.GoalFixtures.progress
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.jupiter.api.Test

private const val TOLERANCE = 1e-9

/** Percentages come out of an average, so they are compared a little loosely. */
private const val PERCENT_TOLERANCE = 1e-4

private const val ITEM_ID = 7L

class GoalProgressWriterTest {

    private val writer = GoalProgressWriter()
    private val start = GoalFixtures.START

    private fun day(
        kind: GoalKind,
        date: LocalDate,
        readings: List<Reading> = emptyList(),
        closes: List<DayClose> = emptyList(),
        occurrences: List<Occurrence> = emptyList(),
        measurements: List<Measurement> = emptyList(),
        plannedMinutes: Int = 0,
        previous: GoalProgress? = null,
        itemId: Long? = ITEM_ID,
    ) = GoalDay(
        goal = goal(kind = kind, itemId = itemId),
        date = date,
        readings = readings,
        closes = closes,
        occurrences = occurrences,
        measurements = measurements,
        plannedMinutes = plannedMinutes,
        previous = previous,
    )

    private fun reading(dayOffset: Long, value: Double) = Reading(start.plusDays(dayOffset), value)

    private fun measurement(dayOffset: Long, value: Double) = Measurement(
        id = 0,
        itemId = ITEM_ID,
        occurrenceId = null,
        date = start.plusDays(dayOffset),
        value = value,
        kind = ValueKind.MINUTES,
    )

    // NUMBER: a level that is measured ---------------------------------------

    @Test
    fun `a measured goal records the reading taken that day`() {
        val row = writer.rowFor(
            day(GoalKind.NUMBER, start.plusDays(1), readings = listOf(reading(0, 80.0), reading(1, 81.0))),
        )

        assertThat(row.rawValue).isWithin(TOLERANCE).of(81.0)
    }

    @Test
    fun `a day with no weigh in records nothing rather than a zero`() {
        val row = writer.rowFor(day(GoalKind.NUMBER, start.plusDays(3), readings = listOf(reading(0, 80.0))))

        assertThat(row.rawValue).isNull()
    }

    @Test
    fun `a measured goal is smoothed, because one reading is water and food`() {
        // A day that jumps two kilos must not move the honest number by two kilos.
        val readings = listOf(reading(0, 80.0), reading(1, 80.0), reading(2, 82.0))

        val smoothed = writer.rowFor(day(GoalKind.NUMBER, start.plusDays(2), readings = readings)).smoothedValue

        assertThat(smoothed).isNotNull()
        assertThat(smoothed!!).isLessThan(82.0)
        assertThat(smoothed).isGreaterThan(80.0)
    }

    @Test
    fun `a measured goal never accumulates, because a weight is not a sum`() {
        val previous = progress(date = start, cumulative = 80.0)

        val row = writer.rowFor(
            day(GoalKind.NUMBER, start.plusDays(1), readings = listOf(reading(1, 81.0)), previous = previous),
        )

        assertThat(row.cumulative).isWithin(TOLERANCE).of(80.0)
    }

    // COUNT: a tally ----------------------------------------------------------

    @Test
    fun `a counting goal adds today to the total it was handed`() {
        val previous = progress(date = start, cumulative = 4.0)

        val row = writer.rowFor(
            day(
                GoalKind.COUNT,
                start.plusDays(1),
                occurrences = listOf(done(ITEM_ID, start.plusDays(1))),
                previous = previous,
            ),
        )

        assertThat(row.cumulative).isWithin(TOLERANCE).of(5.0)
    }

    @Test
    fun `a missed day leaves a counting total exactly where it was`() {
        val previous = progress(date = start, cumulative = 4.0)

        val row = writer.rowFor(
            day(
                GoalKind.COUNT,
                start.plusDays(1),
                occurrences = listOf(missed(ITEM_ID, start.plusDays(1))),
                previous = previous,
            ),
        )

        assertThat(row.cumulative).isWithin(TOLERANCE).of(4.0)
    }

    // DURATION: a sum of minutes ---------------------------------------------

    @Test
    fun `an accumulating goal prefers the minutes that were actually logged`() {
        val row = writer.rowFor(
            day(
                GoalKind.DURATION,
                start,
                occurrences = listOf(done(ITEM_ID, start)),
                measurements = listOf(measurement(0, 90.0)),
                plannedMinutes = 50,
            ),
        )

        assertThat(row.cumulative).isWithin(TOLERANCE).of(90.0)
    }

    @Test
    fun `a step done with no number still counts for what it was planned to take`() {
        // Otherwise a forty hour study goal only moves on the days somebody
        // remembered to type a number in, which is a silent zero.
        val row = writer.rowFor(
            day(GoalKind.DURATION, start, occurrences = listOf(done(ITEM_ID, start)), plannedMinutes = 50),
        )

        assertThat(row.cumulative).isWithin(TOLERANCE).of(50.0)
    }

    @Test
    fun `a step that was not done contributes no minutes`() {
        val row = writer.rowFor(
            day(GoalKind.DURATION, start, occurrences = listOf(missed(ITEM_ID, start)), plannedMinutes = 50),
        )

        assertThat(row.cumulative).isWithin(TOLERANCE).of(0.0)
    }

    // CONSISTENCY: a rate -----------------------------------------------------

    @Test
    fun `a consistency goal records the adherence of the day as a percentage`() {
        val row = writer.rowFor(
            day(
                GoalKind.CONSISTENCY,
                start,
                closes = listOf(close(date = start, itemsDone = 8, itemsMissed = 2, itemsTotal = 10)),
                itemId = null,
            ),
        )

        assertThat(row.rawValue).isWithin(PERCENT_TOLERANCE).of(80.0)
    }

    @Test
    fun `a consistency goal smooths over the whole period, not over one day`() {
        val closes = listOf(
            close(date = start, itemsDone = 10, itemsTotal = 10),
            close(date = start.plusDays(1), itemsDone = 0, itemsMissed = 10, itemsTotal = 10),
        )

        val row = writer.rowFor(day(GoalKind.CONSISTENCY, start.plusDays(1), closes = closes, itemId = null))

        assertThat(row.smoothedValue).isWithin(PERCENT_TOLERANCE).of(50.0)
    }

    // Shared ------------------------------------------------------------------

    @Test
    fun `every row carries the pace line, so no screen has to work it out`() {
        val row = writer.rowFor(day(GoalKind.COUNT, start.plusDays(5)))

        assertThat(row.paceTarget).isWithin(TOLERANCE).of(5.0)
    }

    @Test
    fun `a week marked as not counting stays not counting the next day`() {
        val previous = progress(date = start, counted = false)

        val row = writer.rowFor(day(GoalKind.COUNT, start.plusDays(1), previous = previous))

        assertThat(row.counted).isFalse()
    }

    @Test
    fun `a new week starts counting again`() {
        // 4 January 2026 is a Sunday; the fifth is the Monday after it.
        val sunday = LocalDate.of(2026, 1, 4)
        val previous = progress(date = sunday, counted = false)

        val row = writer.rowFor(day(GoalKind.COUNT, sunday.plusDays(1), previous = previous))

        assertThat(row.counted).isTrue()
    }

    @Test
    fun `a duration goal banks minutes and nothing else`() {
        // Thirty pages logged against a step that also carries a study goal
        // are not thirty minutes. With no minutes logged, the planned
        // duration stands in, as it does for a step done with no number.
        val pages = measurement(0, 30.0).copy(kind = ValueKind.PAGES)
        val row = writer.rowFor(
            day(
                GoalKind.DURATION,
                start,
                occurrences = listOf(done(ITEM_ID, start)),
                measurements = listOf(pages),
                plannedMinutes = 60,
            ),
        )

        assertThat(row.cumulative).isWithin(TOLERANCE).of(60.0)
    }
}
