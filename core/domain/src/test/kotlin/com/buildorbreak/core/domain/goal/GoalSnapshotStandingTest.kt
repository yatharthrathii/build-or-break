package com.buildorbreak.core.domain.goal

import com.buildorbreak.core.testing.fixtures.GoalFixtures
import com.buildorbreak.core.testing.fixtures.GoalFixtures.goal
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.jupiter.api.Test

/** The one word the goal screen says about where the goal stands. */
class GoalSnapshotStandingTest {

    private fun snapshot(percent: Float, on: LocalDate, hasData: Boolean = true) = GoalSnapshot(
        goal = goal(),
        current = percent * 10.0,
        paceTarget = 0.0,
        projected = 0.0,
        percent = percent,
        daysLeft = goal().daysLeft(on),
        trail = emptyList(),
        hasData = hasData,
        hasProjection = hasData,
        on = on,
    )

    @Test
    fun `a goal whose date has passed unreached is over, not merely behind`() {
        assertThat(snapshot(0.6f, GoalFixtures.TARGET).standing).isEqualTo(GoalStanding.OVER)
        assertThat(snapshot(0.6f, GoalFixtures.TARGET.plusDays(3)).standing).isEqualTo(GoalStanding.OVER)
    }

    @Test
    fun `a goal reached on its last day is reached`() {
        assertThat(snapshot(1f, GoalFixtures.TARGET).standing).isEqualTo(GoalStanding.REACHED)
    }

    @Test
    fun `a goal with time left is judged against the line`() {
        assertThat(snapshot(0.2f, GoalFixtures.START.plusDays(5)).standing).isEqualTo(GoalStanding.BEHIND)
        assertThat(snapshot(0.8f, GoalFixtures.START.plusDays(5)).standing).isEqualTo(GoalStanding.AHEAD)
    }

    @Test
    fun `nothing recorded is not a verdict, until the date has passed`() {
        assertThat(snapshot(0f, GoalFixtures.START.plusDays(2), hasData = false).standing)
            .isEqualTo(GoalStanding.UNKNOWN)
        assertThat(snapshot(0f, GoalFixtures.TARGET, hasData = false).standing).isEqualTo(GoalStanding.OVER)
    }
}
