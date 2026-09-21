package com.buildorbreak.app.feature.goal

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.buildorbreak.core.designsystem.theme.BuildOrBreakTheme
import com.buildorbreak.core.domain.goal.GoalStanding
import com.buildorbreak.core.model.enums.GoalKind
import com.buildorbreak.core.model.enums.ValueKind
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

private val TODAY: LocalDate = LocalDate.of(2026, 9, 11)

/**
 * One goal, and the two things it must never do: nag when there is none, and
 * draw a conclusion before there is anything to conclude from.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_MAX_SDK], qualifiers = "w411dp-h891dp-xxhdpi")
class GoalContentTest {

    @get:Rule
    val compose = createComposeRule()

    private fun render(
        state: GoalUiState,
        onNew: () -> Unit = {},
        onWeekCounted: (LocalDate, Boolean) -> Unit = { _, _ -> },
        onChange: (GoalDraft) -> Unit = {},
    ) {
        compose.setContent {
            BuildOrBreakTheme {
                GoalContent(
                    state = state,
                    onNew = onNew,
                    onEdit = {},
                    onChange = onChange,
                    onSave = {},
                    onCancel = {},
                    onRetire = {},
                    onBack = {},
                    onWeekCounted = onWeekCounted,
                )
            }
        }
    }

    private fun scrollTo(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = true))
    }

    @Test
    fun `no goal is an offer rather than a nag`() {
        render(GoalUiState.Empty.copy(loaded = true))

        compose.onNodeWithText("NO GOAL YET").assertIsDisplayed()
        compose.onNodeWithText("SET A GOAL").assertIsDisplayed()
    }

    @Test
    fun `before the first read nothing is claimed at all`() {
        render(GoalUiState.Empty)

        compose.onAllNodesWithText("NO GOAL YET").assertCountEquals(0)
    }

    @Test
    fun `the goal shows where it is and where the line says it should be`() {
        render(GoalUiState.Empty.copy(loaded = true, goal = card()))

        compose.onNodeWithText("58%").assertIsDisplayed()
        compose.onNodeWithText("7 of 12 times").assertIsDisplayed()
        compose.onNodeWithText("The mark shows where you should be today: 67%.").assertIsDisplayed()
    }

    @Test
    fun `a goal with nothing recorded is not told it will fall short`() {
        // A goal set this morning has not fallen short of anything. Saying it
        // has is the same lie as a progress bar with no pace line: confident,
        // early, and wrong in the direction that makes people give up.
        val fresh = card().copy(hasData = false, hasProjection = false, willReach = false)
        render(GoalUiState.Empty.copy(loaded = true, goal = fresh))

        compose.onNodeWithText("Nothing logged yet.").assertIsDisplayed()
        compose.onAllNodesWithText("You will fall short unless something changes.").assertCountEquals(0)
    }

    @Test
    fun `once there is data the projection is stated plainly`() {
        render(GoalUiState.Empty.copy(loaded = true, goal = card()))

        scrollTo("At this rate")
        compose.onNodeWithText("You will fall short unless something changes.").assertIsDisplayed()
    }

    @Test
    fun `the form refuses a goal with no name and says why`() {
        render(GoalUiState.Empty.copy(loaded = true, draft = GoalDraft(startDate = TODAY)))

        compose.onNodeWithText("Give the goal a name to save.").assertIsDisplayed()
        compose.onNodeWithText("SAVE").assertIsNotEnabled()
    }

    @Test
    fun `a counting goal has to say which step it counts`() {
        val draft = GoalDraft(title = "Twelve gym sessions", targetValue = "12", startDate = TODAY)
        render(GoalUiState.Empty.copy(loaded = true, draft = draft))

        compose.onNodeWithText("Pick which step this counts.").assertIsDisplayed()
    }

    @Test
    fun `a complete goal can be saved`() {
        val draft = GoalDraft(title = "Twelve gym sessions", targetValue = "12", itemId = 3, startDate = TODAY)
        render(GoalUiState.Empty.copy(loaded = true, draft = draft))

        compose.onNodeWithText("SAVE").assertIsEnabled()
    }

    @Test
    fun `a goal that goes nowhere is refused`() {
        val draft = GoalDraft(
            title = "Stay exactly here",
            kind = GoalKind.NUMBER,
            startValue = "80",
            targetValue = "80",
            startDate = TODAY,
        )
        render(GoalUiState.Empty.copy(loaded = true, draft = draft))

        compose.onNodeWithText("The start and the target are the same", substring = true).assertIsDisplayed()
    }

    @Test
    fun `the length is chosen in weeks, because that is how people think`() {
        var changed: GoalDraft? = null
        val draft = GoalDraft(title = "Twelve gym sessions", targetValue = "12", itemId = 3, startDate = TODAY)
        render(GoalUiState.Empty.copy(loaded = true, draft = draft)) { changed = it }

        scrollTo("HOW LONG")
        compose.onNodeWithText("8 weeks").assertIsDisplayed()
        compose.onNodeWithText("+").performClick()

        assertThat(changed?.weeks).isEqualTo(9)
    }

    @Test
    fun `in weeks the form still says which day that lands on`() {
        val draft = GoalDraft(title = "Twelve gym sessions", targetValue = "12", itemId = 3, startDate = TODAY)
        render(GoalUiState.Empty.copy(loaded = true, draft = draft))

        scrollTo("HOW LONG")
        // Eight weeks on from Friday the 11th of September 2026.
        compose.onNodeWithText("Ends on Fri 6 Nov 2026.").assertIsDisplayed()
    }

    @Test
    fun `a goal can end on a date instead`() {
        var changed: GoalDraft? = null
        val draft = GoalDraft(title = "Twelve gym sessions", targetValue = "12", itemId = 3, startDate = TODAY)
        render(GoalUiState.Empty.copy(loaded = true, draft = draft)) { changed = it }

        scrollTo("HOW LONG")
        compose.onNodeWithText("BY A DATE").performClick()

        assertThat(changed?.byDate).isTrue()
    }

    @Test
    fun `by date the form shows the day and how far off it is`() {
        val draft = GoalDraft(
            title = "Twelve gym sessions",
            targetValue = "12",
            itemId = 3,
            startDate = TODAY,
            targetDate = TODAY.plusDays(18),
            byDate = true,
        )
        render(GoalUiState.Empty.copy(loaded = true, draft = draft))

        scrollTo("HOW LONG")
        compose.onNodeWithText("Tue 29 Sep 2026").assertIsDisplayed()
        compose.onNodeWithText("18 days from the start.").assertIsDisplayed()
    }

    @Test
    fun `going back to weeks lands the end on a whole week`() {
        var changed: GoalDraft? = null
        val draft = GoalDraft(
            title = "Twelve gym sessions",
            targetValue = "12",
            itemId = 3,
            startDate = TODAY,
            targetDate = TODAY.plusDays(18),
            byDate = true,
        )
        render(GoalUiState.Empty.copy(loaded = true, draft = draft)) { changed = it }

        scrollTo("HOW LONG")
        compose.onNodeWithText("IN WEEKS").performClick()

        // Eighteen days rounds up to three weeks, never down to two.
        assertThat(changed?.targetDate).isEqualTo(TODAY.plusDays(21))
        assertThat(changed?.byDate).isFalse()
    }

    @Test
    fun `the weeks cannot be stepped to an end that has already passed`() {
        var changed: GoalDraft? = null
        val draft = GoalDraft(
            title = "Twelve gym sessions",
            targetValue = "12",
            itemId = 3,
            startDate = TODAY.minusDays(20),
            targetDate = TODAY.plusDays(1),
            earliestEnd = TODAY.plusDays(1),
        )
        render(GoalUiState.Empty.copy(loaded = true, draft = draft)) { changed = it }

        scrollTo("HOW LONG")
        compose.onNodeWithText("−").performClick()

        assertThat(changed).isNull()
    }

    @Test
    fun `a week can be left out of the goal`() {
        var left: Pair<LocalDate, Boolean>? = null
        val monday = LocalDate.of(2026, 9, 7)
        val weeks = persistentListOf(GoalWeekUi(monday, monday.plusDays(6), counted = true, weeksAgo = 0))
        render(
            GoalUiState.Empty.copy(loaded = true, goal = card().copy(weeks = weeks)),
            onWeekCounted = { week, counted -> left = week to counted },
        )

        scrollTo("This week")
        compose.onNodeWithText("7 Sep – 13 Sep · Counts").assertIsDisplayed()
        compose.onNode(isToggleable()).performClick()

        assertThat(left).isEqualTo(monday to false)
    }

    @Test
    fun `a week that was left out says so, and last week is called last week`() {
        val monday = LocalDate.of(2026, 8, 31)
        val weeks = persistentListOf(GoalWeekUi(monday, monday.plusDays(6), counted = false, weeksAgo = 1))
        render(GoalUiState.Empty.copy(loaded = true, goal = card().copy(weeks = weeks)))

        scrollTo("Last week")
        compose.onNodeWithText("31 Aug – 6 Sep · Left out").assertIsDisplayed()
    }

    @Test
    fun `a finished goal has no weeks to leave out`() {
        val monday = LocalDate.of(2026, 9, 7)
        val weeks = persistentListOf(GoalWeekUi(monday, monday.plusDays(6), counted = true, weeksAgo = 0))
        render(GoalUiState.Empty.copy(loaded = true, goal = card().copy(weeks = weeks, isFinished = true)))

        compose.onAllNodesWithText("This week").assertCountEquals(0)
    }

    @Test
    fun `a goal that reached its target says so and offers the next one`() {
        var asked = false
        render(
            GoalUiState.Empty.copy(
                loaded = true,
                goal = card().copy(
                    current = 12.0,
                    percent = 100,
                    standing = GoalStanding.REACHED,
                    isFinished = true,
                    reached = true,
                ),
            ),
            onNew = { asked = true },
        )

        compose.onNodeWithText("GOAL FINISHED").assertIsDisplayed()
        compose.onNodeWithText("Reached").assertIsDisplayed()
        compose.onNodeWithText("You did 12 of 12 times.").assertIsDisplayed()

        compose.onNodeWithText("SET A NEW GOAL").performClick()
        assertThat(asked).isTrue()
    }

    @Test
    fun `a goal that ran out of days says that instead of congratulating`() {
        render(
            GoalUiState.Empty.copy(
                loaded = true,
                goal = card().copy(standing = GoalStanding.OVER, daysLeft = 0, isFinished = true, reached = false),
            ),
        )

        compose.onNodeWithText("Not reached").assertIsDisplayed()
        compose.onAllNodesWithText("Reached").assertCountEquals(0)
    }

    @Test
    fun `a goal still running shows no verdict at all`() {
        render(GoalUiState.Empty.copy(loaded = true, goal = card()))

        compose.onAllNodesWithText("GOAL FINISHED").assertCountEquals(0)
    }

    @Suppress("MagicNumber")
    private fun card() = GoalCardUi(
        id = 1,
        title = "Twelve gym sessions",
        kind = GoalKind.COUNT,
        valueKind = ValueKind.NONE,
        itemId = 3,
        current = 7.0,
        target = 12.0,
        paceTarget = 8.0,
        projected = 10.5,
        percent = 58,
        pacePercent = 67,
        standing = GoalStanding.BEHIND,
        daysLeft = 19,
        daysElapsed = 37,
        totalDays = 56,
        willReach = false,
        hasData = true,
        hasProjection = true,
        trail = persistentListOf(1.0, 2.0, 3.0, 5.0, 7.0),
    )
}
