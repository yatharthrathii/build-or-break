package com.buildorbreak.core.domain.goal

import com.buildorbreak.core.model.enums.GoalKind
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
    fun `a week the user marked as not counting is left out of the rate`() {
        val progress = listOf(
            progress(date = start.plusDays(5), smoothedValue = 6.0),
            progress(date = start.plusDays(8), smoothedValue = 1.0, counted = false),
        )

        // The uncounted day is the latest, but the rate still comes from day five.
        assertThat(pace.project(goal(), progress)).isWithin(TOLERANCE).of(12.0)
    }

    @Test
    fun `progress logged on the start day cannot produce a rate and returns the start`() {
        val progress = listOf(progress(date = start, smoothedValue = 3.0))

        assertThat(pace.project(goal(), progress)).isWithin(TOLERANCE).of(0.0)
    }

    // percentComplete ---------------------------------------------------------

    @Test
    fun `percent complete reads zero at the start and one at the target`() {
        assertThat(pace.percentComplete(goal(), 0.0)).isEqualTo(0f)
        assertThat(pace.percentComplete(goal(), 10.0)).isEqualTo(1f)
    }

    @Test
    fun `percent complete is the fraction of the span covered`() {
        assertThat(pace.percentComplete(goal(), 2.5)).isEqualTo(0.25f)
    }

    @Test
    fun `overshooting the target is still one, not a bar past the end of itself`() {
        assertThat(pace.percentComplete(goal(), 14.0)).isEqualTo(1f)
    }

    @Test
    fun `sliding back past the start reads as nothing done rather than a negative`() {
        assertThat(pace.percentComplete(goal(), -3.0)).isEqualTo(0f)
    }

    @Test
    fun `a goal that goes down fills up as the number comes down`() {
        val losing = goal(startValue = 80.0, targetValue = 75.0)

        assertThat(pace.percentComplete(losing, 77.5)).isEqualTo(0.5f)
    }
}
