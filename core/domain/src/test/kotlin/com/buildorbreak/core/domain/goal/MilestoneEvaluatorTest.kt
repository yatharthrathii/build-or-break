package com.buildorbreak.core.domain.goal

import com.buildorbreak.core.model.enums.DayQuality
import com.buildorbreak.core.model.enums.Milestone
import com.buildorbreak.core.model.goal.DayClose
import com.buildorbreak.core.model.goal.MilestoneAward
import com.buildorbreak.core.testing.fixtures.GoalFixtures
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.jupiter.api.Test

class MilestoneEvaluatorTest {

    private val evaluator = DefaultMilestoneEvaluator()
    private val start = GoalFixtures.START

    private fun contextOf(
        today: DayClose,
        history: List<DayClose> = emptyList(),
        goalPercent: Float? = null,
        longestRun: ItemRun? = null,
        awarded: List<MilestoneAward> = emptyList(),
    ) = MilestoneContext(
        date = today.date,
        today = today,
        history = history,
        goalPercent = goalPercent,
        longestRun = longestRun,
        awarded = awarded,
    )

    private fun award(milestone: Milestone, on: LocalDate) =
        MilestoneAward(milestone = milestone, goalId = null, itemId = null, awardedOn = on, seenAt = null)

    // Rule 1: a bad day earns nothing -----------------------------------------

    @Test
    fun `a poor day earns nothing, however much was technically achieved`() {
        val context = contextOf(
            today = GoalFixtures.close(start, itemsDone = 2, itemsMissed = 8),
            goalPercent = 1f,
        )

        assertThat(context.today.quality).isEqualTo(DayQuality.POOR)
        assertThat(evaluator.evaluate(context)).isNull()
    }

    // Rule 2: once in the lifetime of an install ------------------------------

    @Test
    fun `the first thing ever completed is a milestone`() {
        val context = contextOf(today = GoalFixtures.close(start, itemsDone = 1, itemsTotal = 1))

        assertThat(evaluator.evaluate(context)).isEqualTo(Milestone.FIRST_COMPLETION)
    }

    @Test
    fun `a milestone that has already fired never fires again`() {
        val context = contextOf(
            today = GoalFixtures.close(start.plusDays(1), itemsDone = 1, itemsTotal = 1),
            awarded = listOf(award(Milestone.FIRST_COMPLETION, start)),
        )

        assertThat(evaluator.evaluate(context)).isNull()
    }

    @Test
    fun `a first full day is only first once`() {
        val context = contextOf(
            today = GoalFixtures.close(start.plusDays(1), itemsDone = 5, itemsTotal = 5),
            history = listOf(GoalFixtures.close(start, itemsDone = 5, itemsTotal = 5)),
        )

        assertThat(evaluator.evaluate(context)).isNotEqualTo(Milestone.FIRST_FULL_DAY)
    }

    // Rule 3: not the same kind of praise two days running --------------------

    @Test
    fun `two goal milestones cannot land on consecutive days`() {
        val context = contextOf(
            today = GoalFixtures.close(start.plusDays(9), itemsDone = 10),
            history = GoalFixtures.closes(start, days = 9),
            goalPercent = 0.55f,
            awarded = listOf(award(Milestone.GOAL_QUARTER, start.plusDays(8))),
        )

        // GOAL_HALF qualifies, but a goal milestone fired yesterday.
        assertThat(evaluator.evaluate(context)).isNull()
    }

    @Test
    fun `the same category is allowed again once a day has passed`() {
        val context = contextOf(
            today = GoalFixtures.close(start.plusDays(9), itemsDone = 10),
            history = GoalFixtures.closes(start, days = 9),
            goalPercent = 0.55f,
            awarded = listOf(award(Milestone.GOAL_QUARTER, start.plusDays(7))),
        )

        assertThat(evaluator.evaluate(context)).isEqualTo(Milestone.GOAL_HALF)
    }

    // Rule 4: one a day, the rarest of them -----------------------------------

    @Test
    fun `when several qualify the rarest one is chosen`() {
        // A first completion and a goal boundary on the same day. The first
        // time happens once in the life of an install, so it wins.
        val context = contextOf(
            today = GoalFixtures.close(start, itemsDone = 1, itemsTotal = 1),
            goalPercent = 0.3f,
        )

        assertThat(evaluator.evaluate(context)).isEqualTo(Milestone.FIRST_COMPLETION)
    }

    @Test
    fun `a goal boundary fires on a good day with nothing rarer available`() {
        val context = contextOf(
            today = GoalFixtures.close(start.plusDays(3), itemsDone = 10),
            history = GoalFixtures.closes(start, days = 3),
            goalPercent = 0.8f,
        )

        assertThat(evaluator.evaluate(context)).isEqualTo(Milestone.GOAL_THREE_QUARTERS)
    }

    @Test
    fun `an item on a thirty day run is worth saying`() {
        val context = contextOf(
            today = GoalFixtures.close(start.plusDays(40), itemsDone = 10),
            history = GoalFixtures.closes(start, days = 40),
            longestRun = ItemRun(itemId = 3, days = 30),
        )

        assertThat(evaluator.evaluate(context)).isEqualTo(Milestone.ITEM_THIRTY_DAY_RUN)
    }

    // The number shown instead of a streak ------------------------------------

    @Test
    fun `consistency counts the good days in the last thirty`() {
        val history = GoalFixtures.closes(start, days = 30) +
            GoalFixtures.close(start.plusDays(30), itemsDone = 1, itemsMissed = 9)

        val score = evaluator.consistency(history, on = start.plusDays(30))

        assertThat(score.goodDays).isEqualTo(29)
        assertThat(score.consideredDays).isEqualTo(30)
    }

    @Test
    fun `one bad day costs one point rather than wiping everything`() {
        // The whole reason this is not a streak. A chain would read zero here.
        val history = GoalFixtures.closes(start, days = 20) +
            GoalFixtures.close(start.plusDays(20), itemsDone = 0, itemsMissed = 10) +
            GoalFixtures.closes(start.plusDays(21), days = 5)

        val score = evaluator.consistency(history, on = start.plusDays(25))

        assertThat(score.goodDays).isEqualTo(25)
        assertThat(score.consideredDays).isEqualTo(26)
    }

    @Test
    fun `nothing closed yet is zero out of zero rather than a divide by zero`() {
        val score = evaluator.consistency(emptyList(), on = start)

        assertThat(score.consideredDays).isEqualTo(0)
        assertThat(score.fraction).isEqualTo(0f)
    }
}
