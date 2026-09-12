package com.buildorbreak.app.feature.insights

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.buildorbreak.core.designsystem.theme.BuildOrBreakTheme
import com.buildorbreak.core.domain.goal.GoalStanding
import com.buildorbreak.core.domain.review.InsightsPeriod
import com.buildorbreak.core.domain.review.SkipCause
import com.buildorbreak.core.model.enums.ReviewStory
import com.buildorbreak.core.model.review.ReviewAnswer
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The newest Android image Robolectric 4.14 can run. */
private const val ROBOLECTRIC_MAX_SDK = 35

/**
 * The week told in words, against a fixed state.
 *
 * The point of the screen is the reading, not the chart, so most of what is
 * asserted here is a sentence. A number that is right and a sentence that is
 * wrong is still a report nobody should trust.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_MAX_SDK], qualifiers = "w411dp-h891dp-xxhdpi")
class InsightsContentTest {

    @get:Rule
    val compose = createComposeRule()

    private fun render(state: InsightsUiState, onApply: (SuggestionUi, ReviewAnswer) -> Unit = { _, _ -> }) {
        compose.setContent {
            BuildOrBreakTheme {
                InsightsContent(state = state, onPeriod = {}, onApply = onApply, onDismiss = {}, onOpenGoal = {})
            }
        }
    }

    private fun scrollTo(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = true))
    }

    @Test
    fun `the week opens with a headline, not a table`() {
        render(state())

        scrollTo("THE READING")
        compose.onNodeWithText("Some steps are at the wrong time.").assertIsDisplayed()
    }

    @Test
    fun `the thing that went best is named`() {
        render(state())

        scrollTo("WENT BEST")
        compose.onNodeWithText("Wake + water: done every time, all 7 of them.").assertIsDisplayed()
    }

    @Test
    fun `a step kept every time says so rather than counting to itself`() {
        render(state())

        scrollTo("WENT BEST")
        compose.onNodeWithText("done every time", substring = true).assertIsDisplayed()
    }

    @Test
    fun `every repeated miss is listed, not only the one being acted on`() {
        render(state())

        scrollTo("WHAT KEEPS HAPPENING")
        compose.onNodeWithText("4/7").assertIsDisplayed()
        compose.onNodeWithText("3/7").assertIsDisplayed()
    }

    @Test
    fun `a weekday cluster is called a shape rather than bad luck`() {
        render(state())

        scrollTo("Almost always on a Saturday")
        compose.onNodeWithText("Almost always on a Saturday", substring = true).assertIsDisplayed()
    }

    @Test
    fun `the other answers are one tap away, including the two ways out`() {
        render(state())

        scrollTo("TRY SOMETHING ELSE")
        compose.onNodeWithText("TRY SOMETHING ELSE").performClick()

        scrollTo("Take Deep work block 2 off the plan")
        compose.onNodeWithText("Take Deep work block 2 off the plan").assertIsDisplayed()
        compose.onNodeWithText("Leave Deep work block 2 alone").assertIsDisplayed()
    }

    @Test
    fun `picking a different answer applies that one, not the suggested one`() {
        var applied: ReviewAnswer? = null
        render(state()) { _, answer -> applied = answer }

        scrollTo("TRY SOMETHING ELSE")
        compose.onNodeWithText("TRY SOMETHING ELSE").performClick()
        scrollTo("Leave Deep work block 2 alone")
        compose.onNodeWithText("Leave Deep work block 2 alone").performClick()

        assertThat(applied).isEqualTo(ReviewAnswer.LEAVE_IT)
    }

    @Test
    fun `a single answer is not dressed up as a choice`() {
        render(state().copy(suggestion = state().suggestion?.copy(options = persistentListOf(ReviewAnswer.MOVE_TIME))))

        scrollTo("ONE CHANGE WORTH MAKING")
        compose.onAllNodesWithText("TRY SOMETHING ELSE").assertCountEquals(0)
    }

    @Test
    fun `the goal is a line on the review, when there is one`() {
        render(state())

        scrollTo("Twelve gym sessions")
        compose.onNodeWithText("Twelve gym sessions").assertIsDisplayed()
        compose.onNodeWithText("On track.").assertIsDisplayed()
    }

    @Test
    fun `no goal means no goal line, not an empty one`() {
        render(state().copy(goal = null))

        compose.onAllNodesWithText("THE GOAL").assertCountEquals(0)
    }

    @Test
    fun `nothing settled yet says so instead of drawing a zero`() {
        render(InsightsUiState.Empty.copy(hasPlan = true))

        compose.onNodeWithText("NOTHING TO SHOW YET").assertIsDisplayed()
    }

    @Test
    fun `a quiet Monday is not mistaken for a first week`() {
        // Six weeks of history and an empty current week. Telling somebody
        // they are settling in would be the app forgetting them.
        render(InsightsUiState.Empty.copy(hasPlan = true, hasHistory = true))

        compose.onNodeWithText("NOTHING THIS WEEK YET").assertIsDisplayed()
        compose.onAllNodesWithText("settling in", substring = true).assertCountEquals(0)
    }

    // Fixture data, literal on purpose so the assertions can be read against it.
    @Suppress("MagicNumber")
    private fun state() = InsightsUiState(
        period = InsightsPeriod.WEEK,
        hasPlan = true,
        range = "1 Sep – 7 Sep",
        weekNumber = 36,
        percent = 71,
        kept = 23,
        total = 32,
        changePoints = -3,
        averageSlipMinutes = 9,
        bars = persistentListOf(
            BarUi("M", 0.78f, false, false),
            BarUi("T", 1f, false, true),
        ),
        steps = persistentListOf(
            StepRowUi(1, "Wake + water", 7, 7, 2, false),
            StepRowUi(3, "Deep work block 2", 5, 7, 41, true),
        ),
        skipReasons = persistentListOf(),
        win = WinUi(1, "Wake + water", 7, 7, isPerfect = true),
        patterns = persistentListOf(
            PatternUi(3, "Deep work block 2", 4, 7, SkipCause.TIMING, weekday = null),
            PatternUi(4, "Language drill", 3, 7, SkipCause.REMINDER, weekday = "Saturday"),
        ),
        suggestion = suggestion(),
        goal = GoalStripUi("Twelve gym sessions", 58, GoalStanding.ON_PACE, 19, hasData = true),
        hasHistory = true,
        story = ReviewStory.TIMING_PROBLEM,
    )

    @Suppress("MagicNumber")
    private fun suggestion() = SuggestionUi(
        itemId = 3,
        title = "Deep work block 2",
        answer = ReviewAnswer.WIDEN_WINDOW,
        misses = 4,
        outOf = 7,
        slipMinutes = 41,
        weekStart = LocalDate.of(2026, 9, 7),
        options = persistentListOf(
            ReviewAnswer.WIDEN_WINDOW,
            ReviewAnswer.MOVE_TIME,
            ReviewAnswer.LEAVE_IT,
            ReviewAnswer.REMOVE_ITEM,
        ),
    )
}
