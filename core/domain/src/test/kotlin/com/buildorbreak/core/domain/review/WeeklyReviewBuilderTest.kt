package com.buildorbreak.core.domain.review

import com.buildorbreak.core.model.enums.ReviewStory
import com.buildorbreak.core.model.enums.SkipChip
import com.buildorbreak.core.model.execution.Occurrence
import com.buildorbreak.core.model.goal.DayClose
import com.buildorbreak.core.model.plan.Item
import com.buildorbreak.core.model.review.ReviewAnswer
import com.buildorbreak.core.testing.fixtures.ExecutionFixtures
import com.buildorbreak.core.testing.fixtures.GoalFixtures
import com.buildorbreak.core.testing.fixtures.PlanFixtures
import com.buildorbreak.core.testing.fixtures.PlanFixtures.item
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.time.Duration.Companion.minutes
import org.junit.jupiter.api.Test

class WeeklyReviewBuilderTest {

    private val builder = DefaultWeeklyReviewBuilder()

    /** A Monday, so the week runs Monday to Sunday. */
    private val weekStart: LocalDate = LocalDate.of(2026, 1, 5)
    private val lastWeek: LocalDate = weekStart.minusWeeks(1)

    private fun inputOf(
        closes: List<DayClose> = GoalFixtures.closes(weekStart, days = 7, itemsDone = 9, itemsTotal = 10),
        previousCloses: List<DayClose> = GoalFixtures.closes(lastWeek, days = 7, itemsDone = 9, itemsTotal = 10),
        occurrences: List<Occurrence> = emptyList(),
        recentOccurrences: List<Occurrence> = occurrences,
        items: List<Item> = listOf(item(id = 1, title = "Evening walk")),
        reasons: List<com.buildorbreak.core.model.execution.SkipReason> = emptyList(),
        goalPercent: Float? = null,
        goalPaceFraction: Float? = null,
    ) = ReviewInput(
        weekStart = weekStart,
        closes = closes,
        occurrences = occurrences,
        items = items,
        previousCloses = previousCloses,
        recentOccurrences = recentOccurrences,
        reasons = reasons,
        goalPercent = goalPercent,
        goalPaceFraction = goalPaceFraction,
    )

    /** Misses on the given dates for item 1, with unique occurrence ids. */
    private fun missed(vararg dates: LocalDate): List<Occurrence> =
        dates.mapIndexed { index, date -> ExecutionFixtures.missed(itemId = 1, date = date, id = 100L + index) }

    private fun done(vararg dates: LocalDate): List<Occurrence> =
        dates.mapIndexed { index, date -> ExecutionFixtures.done(itemId = 1, date = date, id = 500L + index) }

    // The story ---------------------------------------------------------------

    @Test
    fun `a first week is settling in, whatever the numbers happen to say`() {
        val review = builder.build(inputOf(previousCloses = emptyList()))

        assertThat(review.story).isEqualTo(ReviewStory.SETTLING_IN)
    }

    @Test
    fun `a week that went well is reported as going well`() {
        assertThat(builder.build(inputOf()).story).isEqualTo(ReviewStory.ON_TRACK)
    }

    @Test
    fun `keeping the plan while the goal stalls blames the plan, not the person`() {
        val review = builder.build(inputOf(goalPercent = 0.3f, goalPaceFraction = 0.6f))

        assertThat(review.story).isEqualTo(ReviewStory.PLAN_TOO_SMALL)
    }

    @Test
    fun `a goal that is keeping up is not called too small`() {
        val review = builder.build(inputOf(goalPercent = 0.58f, goalPaceFraction = 0.6f))

        assertThat(review.story).isEqualTo(ReviewStory.ON_TRACK)
    }

    @Test
    fun `a sharp drop from last week is losing grip`() {
        val review = builder.build(
            inputOf(closes = GoalFixtures.closes(weekStart, days = 7, itemsDone = 5, itemsTotal = 10)),
        )

        assertThat(review.story).isEqualTo(ReviewStory.LOSING_GRIP)
    }

    @Test
    fun `misses that keep landing on one weekday are a timing problem`() {
        val threeMondays = missed(weekStart, weekStart.plusWeeks(1), weekStart.plusWeeks(2))

        val review = builder.build(
            inputOf(
                closes = GoalFixtures.closes(weekStart, days = 7, itemsDone = 6, itemsTotal = 10),
                previousCloses = GoalFixtures.closes(lastWeek, days = 7, itemsDone = 7, itemsTotal = 10),
                recentOccurrences = threeMondays + done(weekStart.plusDays(1), weekStart.plusDays(2)),
            ),
        )

        assertThat(review.story).isEqualTo(ReviewStory.TIMING_PROBLEM)
        assertThat(review.problem?.weekday).isEqualTo(DayOfWeek.MONDAY)
    }

    @Test
    fun `misses that are simply forgotten are a reminder problem`() {
        val misses = missed(weekStart, weekStart.plusDays(2), weekStart.plusDays(4))

        val review = builder.build(
            inputOf(
                closes = GoalFixtures.closes(weekStart, days = 7, itemsDone = 6, itemsTotal = 10),
                previousCloses = GoalFixtures.closes(lastWeek, days = 7, itemsDone = 7, itemsTotal = 10),
                recentOccurrences = misses + done(weekStart.plusDays(1), weekStart.plusDays(3)),
                reasons = misses.map { ExecutionFixtures.skipReason(it.id, SkipChip.FORGOT) },
            ),
        )

        assertThat(review.story).isEqualTo(ReviewStory.REMINDER_PROBLEM)
    }

    @Test
    fun `an ordinary week with no clear signal is mixed rather than invented`() {
        val review = builder.build(
            inputOf(
                closes = GoalFixtures.closes(weekStart, days = 7, itemsDone = 7, itemsTotal = 10),
                previousCloses = GoalFixtures.closes(lastWeek, days = 7, itemsDone = 7, itemsTotal = 10),
            ),
        )

        assertThat(review.story).isEqualTo(ReviewStory.MIXED)
    }

    // The win and the problem -------------------------------------------------

    @Test
    fun `the win names the item rather than quoting a percentage`() {
        val review = builder.build(
            inputOf(occurrences = done(weekStart, weekStart.plusDays(1), weekStart.plusDays(2))),
        )

        assertThat(review.win?.title).isEqualTo("Evening walk")
        assertThat(review.win?.done).isEqualTo(3)
        assertThat(review.win?.outOf).isEqualTo(3)
    }

    @Test
    fun `a week with nothing completed has no win to report`() {
        assertThat(builder.build(inputOf()).win).isNull()
    }

    @Test
    fun `only one problem is reported, however many are struggling`() {
        val bad = missed(weekStart, weekStart.plusDays(1), weekStart.plusDays(2))
        val worse = (0..4).map {
            ExecutionFixtures.missed(itemId = 2, date = weekStart.plusDays(it.toLong()), id = 200L + it)
        }

        val review = builder.build(
            inputOf(
                items = listOf(item(id = 1, title = "Evening walk"), item(id = 2, title = "Reading")),
                recentOccurrences = bad + worse,
            ),
        )

        assertThat(review.problem?.itemId).isEqualTo(2L)
        assertThat(review.problem?.title).isEqualTo("Reading")
    }

    // The question ------------------------------------------------------------

    @Test
    fun `every question offers a way out, so nobody has to answer dishonestly`() {
        val review = builder.build(
            inputOf(recentOccurrences = missed(weekStart, weekStart.plusDays(1), weekStart.plusDays(2))),
        )

        assertThat(review.question?.options).containsAtLeast(ReviewAnswer.LEAVE_IT, ReviewAnswer.REMOVE_ITEM)
    }

    @Test
    fun `a timing problem is offered a window before a new fixed time`() {
        val misses = missed(weekStart, weekStart.plusDays(1), weekStart.plusDays(2))

        val review = builder.build(
            inputOf(
                recentOccurrences = misses,
                reasons = misses.map { ExecutionFixtures.skipReason(it.id, SkipChip.WORK_CAME_UP) },
            ),
        )

        // Moving a fixed time relocates the problem. A range removes it.
        assertThat(review.question?.options?.first()).isEqualTo(ReviewAnswer.WIDEN_WINDOW)
    }

    @Test
    fun `something that is simply forgotten is offered a louder reminder`() {
        val misses = missed(weekStart, weekStart.plusDays(1), weekStart.plusDays(2))

        val review = builder.build(
            inputOf(
                recentOccurrences = misses,
                reasons = misses.map { ExecutionFixtures.skipReason(it.id, SkipChip.FORGOT) },
            ),
        )

        assertThat(review.question?.options?.first()).isEqualTo(ReviewAnswer.RAISE_SALIENCE)
    }

    @Test
    fun `something skipped for want of motivation is offered its smaller version`() {
        val misses = missed(weekStart, weekStart.plusDays(1), weekStart.plusDays(2))

        val review = builder.build(
            inputOf(
                items = listOf(
                    item(id = 1, title = "Evening walk", minimum = PlanFixtures.minimum(duration = 5.minutes)),
                ),
                recentOccurrences = misses,
                reasons = misses.map { ExecutionFixtures.skipReason(it.id, SkipChip.NOT_IN_MOOD) },
            ),
        )

        assertThat(review.question?.options?.first()).isEqualTo(ReviewAnswer.USE_MINIMUM)
    }

    @Test
    fun `a good week asks nothing at all`() {
        val review = builder.build(inputOf(occurrences = done(weekStart, weekStart.plusDays(1))))

        assertThat(review.problem).isNull()
        assertThat(review.question).isNull()
    }

    @Test
    fun `the review carries the week it covers and the trend against last week`() {
        val review = builder.build(inputOf())

        assertThat(review.weekStart).isEqualTo(weekStart)
        assertThat(review.weekEnd).isEqualTo(weekStart.plusDays(6))
        assertThat(review.adherence).isEqualTo(0.9f)
        assertThat(review.previousAdherence).isEqualTo(0.9f)
        assertThat(review.isImproving).isFalse()
    }
}
