package com.buildorbreak.core.domain.goal

import com.buildorbreak.core.model.enums.GoalKind
import com.buildorbreak.core.model.enums.ValueKind
import com.buildorbreak.core.testing.fixtures.GoalFixtures
import com.buildorbreak.core.testing.fixtures.GoalFixtures.goal
import com.buildorbreak.core.testing.fixtures.GoalFixtures.progress
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

private const val TOLERANCE = 1e-9

class PaceProjectorTest {

    private val pace = PaceProjector()
    private val start = GoalFixtures.START

    // paceTarget: a straight line from start to target ------------------------

    @Test
    fun `on the first day the pace target is the starting value`() {
        assertThat(pace.paceTarget(goal(), start)).isWithin(TOLERANCE).of(0.0)
    }

    @Test
    fun `halfway through the goal the pace target is halfway to it`() {
        assertThat(pace.paceTarget(goal(), start.plusDays(5))).isWithin(TOLERANCE).of(5.0)
    }

    @Test
    fun `on the target date the pace target is the target`() {
        assertThat(pace.paceTarget(goal(), GoalFixtures.TARGET)).isWithin(TOLERANCE).of(10.0)
    }

    @Test
    fun `a goal that goes down slopes the other way without any special case`() {
        val losing = goal(startValue = 80.0, targetValue = 75.0)

        assertThat(pace.paceTarget(losing, start.plusDays(5))).isWithin(TOLERANCE).of(77.5)
    }

    @Test
    fun `a date past the target does not run the line beyond the target`() {
        assertThat(pace.paceTarget(goal(), GoalFixtures.TARGET.plusDays(30))).isWithin(TOLERANCE).of(10.0)
    }

    // project: the current rate, carried forward ------------------------------

    @Test
    fun `with nothing logged the projection is the starting value, not optimism`() {
        assertThat(pace.project(goal(), emptyList())).isWithin(TOLERANCE).of(0.0)
    }

    @Test
    fun `a measured goal projects from the smoothed value, never the raw one`() {
        // Five days in, smoothed says 6.0, so the rate is 1.2 a day over ten days.
        val progress = listOf(
            progress(date = start.plusDays(5), rawValue = 9.0, smoothedValue = 6.0),
        )

        assertThat(pace.project(goal(), progress)).isWithin(TOLERANCE).of(12.0)
    }

    @Test
    fun `a counting goal projects from what has been banked`() {
        // Six sessions in five days is 1.2 a day, so twelve over the ten days.
        val counting = goal(kind = GoalKind.COUNT, targetValue = 12.0)
        val progress = listOf(progress(date = start.plusDays(5), cumulative = 6.0))

        assertThat(pace.project(counting, progress)).isWithin(TOLERANCE).of(12.0)
    }

    @Test
    fun `a week left out changes the rate, not where the goal stands`() {
        // Three sessions in three days, then three days of flu with nothing done.
        val counting = goal(kind = GoalKind.COUNT, targetValue = 12.0)
        val progress = listOf(
            progress(date = start.plusDays(3), cumulative = 3.0),
            progress(date = start.plusDays(6), cumulative = 3.0, counted = false),
        )

        // One a day while well, carried over the four days left: three plus four.
        // Counting the flu it would have been three in six days, so five.
        assertThat(pace.project(counting, progress)).isWithin(TOLERANCE).of(7.0)
    }

    @Test
    fun `an older week left out still matters once newer days exist`() {
        val counting = goal(kind = GoalKind.COUNT, targetValue = 12.0)
        val progress = listOf(
            progress(date = start.plusDays(3), cumulative = 3.0),
            progress(date = start.plusDays(6), cumulative = 3.0, counted = false),
            progress(date = start.plusDays(8), cumulative = 5.0),
        )

        // Five sessions over the five days that count is one a day, with two days to go.
        assertThat(pace.project(counting, progress)).isWithin(TOLERANCE).of(7.0)
    }

    @Test
    fun `what was done in a week left out is kept, only its pace is dropped`() {
        val counting = goal(kind = GoalKind.COUNT, targetValue = 12.0)
        val progress = listOf(
            progress(date = start.plusDays(3), cumulative = 3.0),
            progress(date = start.plusDays(6), cumulative = 4.0, counted = false),
        )

        // The one session done while ill is banked: four, plus one a day for four days.
        assertThat(pace.project(counting, progress)).isWithin(TOLERANCE).of(8.0)
    }

    @Test
    fun `a measured goal carries its healthy rate on from where it really is`() {
        val progress = listOf(
            progress(date = start.plusDays(5), smoothedValue = 6.0),
            progress(date = start.plusDays(8), smoothedValue = 3.0, counted = false),
        )

        // The drop happened, so the line starts from three, not from six. The
        // rate is the 1.2 a day from before it, over the two days that remain.
        assertThat(pace.project(goal(), progress)).isWithin(TOLERANCE).of(5.4)
    }

    @Test
    fun `with every day left out there is no rate, so the line stays flat`() {
        val counting = goal(kind = GoalKind.COUNT, targetValue = 12.0)
        val progress = listOf(progress(date = start.plusDays(4), cumulative = 2.0, counted = false))

        assertThat(pace.project(counting, progress)).isWithin(TOLERANCE).of(2.0)
    }

    @Test
    fun `progress logged on the start day cannot produce a rate and returns the start`() {
        val progress = listOf(progress(date = start, smoothedValue = 3.0))

        assertThat(pace.project(goal(), progress)).isWithin(TOLERANCE).of(0.0)
    }

    // percentComplete ---------------------------------------------------------

    @Test
    fun `percent complete reads zero at the start and one at the target`() {
        assertThat(pace.percentComplete(goal(), 0.0, start)).isEqualTo(0f)
        assertThat(pace.percentComplete(goal(), 10.0, start)).isEqualTo(1f)
    }

    @Test
    fun `percent complete is the fraction of the span covered`() {
        assertThat(pace.percentComplete(goal(), 2.5, start)).isEqualTo(0.25f)
    }

    @Test
    fun `overshooting the target is still one, not a bar past the end of itself`() {
        assertThat(pace.percentComplete(goal(), 14.0, start)).isEqualTo(1f)
    }

    @Test
    fun `sliding back past the start reads as nothing done rather than a negative`() {
        assertThat(pace.percentComplete(goal(), -3.0, start)).isEqualTo(0f)
    }

    @Test
    fun `a goal that goes down fills up as the number comes down`() {
        val losing = goal(startValue = 80.0, targetValue = 75.0)

        assertThat(pace.percentComplete(losing, 77.5, start)).isEqualTo(0.5f)
    }

    // CONSISTENCY: a rate, not a total ----------------------------------------

    private fun rate() = goal(kind = GoalKind.CONSISTENCY, valueKind = ValueKind.NONE, targetValue = 90.0)

    @Test
    fun `a rate has no line to climb, so its pace target is the target all the way through`() {
        assertThat(pace.paceTarget(rate(), start)).isWithin(TOLERANCE).of(90.0)
        assertThat(pace.paceTarget(rate(), start.plusDays(5))).isWithin(TOLERANCE).of(90.0)
    }

    @Test
    fun `a rate carried forward is the rate, not eight times the rate`() {
        val rows = listOf(progress(date = start.plusDays(4), rawValue = 100.0, smoothedValue = 80.0))

        assertThat(pace.project(rate(), rows)).isWithin(TOLERANCE).of(80.0)
    }

    @Test
    fun `being at the rate on day one is a good first day, not a goal nearly reached`() {
        assertThat(pace.percentComplete(rate(), 95.0, start.plusDays(1))).isWithin(1e-6f).of(0.1f)
    }

    @Test
    fun `a rate held for the whole period is reached on the last day and not before`() {
        assertThat(pace.percentComplete(rate(), 95.0, start.plusDays(9))).isLessThan(1f)
        assertThat(pace.percentComplete(rate(), 95.0, GoalFixtures.TARGET)).isWithin(1e-6f).of(1f)
    }

    @Test
    fun `a rate below the target banks only the share of it that was kept`() {
        assertThat(pace.percentComplete(rate(), 45.0, GoalFixtures.TARGET)).isWithin(1e-6f).of(0.5f)
    }
}
