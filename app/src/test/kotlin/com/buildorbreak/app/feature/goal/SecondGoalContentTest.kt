package com.buildorbreak.app.feature.goal

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
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
 * Two goals at once: the tabs, the offer of a second, and what it costs.
 *
 * Apart from `GoalContentTest` because it is a different promise. That one is
 * about a goal being honest; this is about a second goal being a choice, with
 * its price said before it is charged.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_MAX_SDK], qualifiers = "w411dp-h891dp-xxhdpi")
class SecondGoalContentTest {

    @get:Rule
    val compose = createComposeRule()

    private fun render(state: GoalUiState, onNew: () -> Unit = {}, onSelect: (Long) -> Unit = {}) {
        compose.setContent {
            BuildOrBreakTheme {
                GoalContent(
                    state = state,
                    onNew = onNew,
                    onEdit = {},
                    onChange = {},
                    onSave = {},
                    onCancel = {},
                    onRetire = {},
                    onBack = {},
                    onSelect = onSelect,
                )
            }
        }
    }

    private fun scrollTo(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = true))
    }

    @Test
    fun `one goal has no tabs`() {
        val tabs = persistentListOf(GoalTabUi(1, "Twelve gym sessions"))
        render(GoalUiState.Empty.copy(loaded = true, goal = card(), tabs = tabs))

        // The title is the kicker over the number, once. A tab would make it twice.
        compose.onAllNodesWithText("TWELVE GYM SESSIONS").assertCountEquals(1)
    }

    @Test
    fun `two goals are two tabs, and tapping one asks for it`() {
        var asked: Long? = null
        val tabs = persistentListOf(GoalTabUi(1, "Twelve gym sessions"), GoalTabUi(2, "Read more"))
        render(GoalUiState.Empty.copy(loaded = true, goal = card(), tabs = tabs), onSelect = { asked = it })

        compose.onNodeWithText("READ MORE").performClick()

        assertThat(asked).isEqualTo(2)
    }

    @Test
    fun `a running goal offers a second one with its price on the button`() {
        var asked = false
        render(
            GoalUiState.Empty.copy(loaded = true, goal = card(), second = SecondGoalUi(cost = 300, balance = 790)),
            onNew = { asked = true },
        )

        scrollTo("ADD A SECOND GOAL")
        compose.onNodeWithText("ADD A SECOND GOAL · 300").performClick()

        assertThat(asked).isTrue()
    }

    @Test
    fun `too few points leaves the offer visible, turned off, with both numbers`() {
        render(GoalUiState.Empty.copy(loaded = true, goal = card(), second = SecondGoalUi(cost = 300, balance = 120)))

        scrollTo("ADD A SECOND GOAL")
        compose.onNodeWithText("ADD A SECOND GOAL · 300").assertIsNotEnabled()
        compose.onNodeWithText("You have 120 and this costs 300", substring = true).assertIsDisplayed()
    }

    @Test
    fun `a finished goal does not offer a second, it offers the next`() {
        render(
            GoalUiState.Empty.copy(
                loaded = true,
                goal = card().copy(isFinished = true, reached = true, standing = GoalStanding.REACHED),
                second = SecondGoalUi(cost = 300, balance = 790),
            ),
        )

        compose.onAllNodesWithText("ADD A SECOND GOAL · 300").assertCountEquals(0)
        compose.onNodeWithText("SET A NEW GOAL").assertIsDisplayed()
    }

    @Test
    fun `the form for a second goal says the price before anything else, and on the button`() {
        val draft = GoalDraft(
            title = "Read more",
            targetValue = "12",
            itemId = 3,
            startDate = TODAY,
            cost = 300,
            balance = 790,
        )
        render(GoalUiState.Empty.copy(loaded = true, draft = draft))

        compose.onNodeWithText("A second goal costs 300 points. You have 790.").assertIsDisplayed()
        compose.onNodeWithText("SAVE FOR 300").assertIsEnabled()
    }

    @Test
    fun `a first goal and an edit carry no price at all`() {
        val draft = GoalDraft(title = "Twelve gym sessions", targetValue = "12", itemId = 3, startDate = TODAY)
        render(GoalUiState.Empty.copy(loaded = true, draft = draft))

        compose.onAllNodesWithText("costs", substring = true).assertCountEquals(0)
        compose.onNodeWithText("SAVE").assertIsEnabled()
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
