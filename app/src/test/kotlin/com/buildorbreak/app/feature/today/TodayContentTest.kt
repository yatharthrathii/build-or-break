package com.buildorbreak.app.feature.today

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.buildorbreak.core.designsystem.theme.BuildOrBreakTheme
import com.buildorbreak.core.domain.goal.GoalStanding
import com.buildorbreak.core.model.enums.GoalKind
import com.buildorbreak.core.model.enums.Milestone
import com.buildorbreak.core.model.enums.SkipChip
import com.buildorbreak.core.model.enums.ValueKind
import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The newest Android image Robolectric 4.14 can run. */
private const val ROBOLECTRIC_MAX_SDK = 35

/**
 * The screen against a fixed state, with no database and no ViewModel.
 *
 * `TodayContent` takes its state as a parameter for exactly this reason. A test
 * that had to seed Room, resolve a day and wait for a flow would be testing the
 * whole app to find out whether a button is on screen.
 */
@RunWith(RobolectricTestRunner::class)
// Robolectric 4.14 ships images up to API 35 and the app targets 36. Pinned here
// rather than lowering targetSdk, which would be letting the test tail wag the
// release dog. Raise it when Robolectric ships 36.
// A phone sized window. Robolectric's default is small enough that the card
// under the ring sits below the fold of the lazy list and never composes.
@Config(sdk = [ROBOLECTRIC_MAX_SDK], qualifiers = "w411dp-h891dp-xxhdpi")
class TodayContentTest {

    @get:Rule
    val compose = createComposeRule()

    private fun render(state: TodayUiState, actions: TodayActions = TodayActions.None) {
        compose.setContent {
            BuildOrBreakTheme { TodayContent(state = state, actions = actions) }
        }
    }

    /** Brings a row of the lazy list into view before asserting on it. */
    private fun scrollTo(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = true))
    }

    @Test
    fun `the score sits beside the ring, and says how much of it is today`() {
        render(previewState())

        compose.onNodeWithText("1,285").assertIsDisplayed()
        compose.onNodeWithText("POINTS").assertIsDisplayed()
        compose.onNodeWithText("+45 today").assertIsDisplayed()
    }

    @Test
    fun `nothing kept yet shows the bank alone, not plus zero`() {
        render(previewState().copy(points = PointsUi(banked = 1240, today = 0)))

        compose.onNodeWithText("1,240").assertIsDisplayed()
        compose.onAllNodesWithText("+0 today").assertCountEquals(0)
    }

    @Test
    fun `the ring is a progress bar to a screen reader, and a row says its state`() {
        render(previewState())

        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertExists()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Next")).assertExists()
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Done"))
            .assertCountEquals(2)
    }

    @Test
    fun `the next step is on the card with a done button`() {
        render(previewState())

        scrollTo("DONE")
        compose.onNodeWithText("DONE").assertIsDisplayed()
        // On the card and on the timeline, so two nodes carry the title.
        compose.onAllNodesWithText("Deep work block 1", substring = true)[0].assertIsDisplayed()
    }

    @Test
    fun `done hands back the occurrence on the card`() {
        var completed: Long? = null
        render(previewState(), TodayActions.None.copy(onDone = { completed = it }))

        scrollTo("DONE")
        compose.onNodeWithText("DONE").performClick()

        assertThat(completed).isEqualTo(3L)
    }

    @Test
    fun `a shifted day says so and offers undo`() {
        render(previewState())

        compose.onNodeWithText("UNDO").assertIsDisplayed()
        compose.onNodeWithText("Day shifted +20 min", substring = true).assertIsDisplayed()
    }

    @Test
    fun `a degraded tier gets a line and a fix`() {
        render(previewState())

        compose.onNodeWithText("FIX").assertIsDisplayed()
    }

    @Test
    fun `no plan offers the two ways in`() {
        render(TodayUiState.Empty)

        compose.onNodeWithText("PASTE A ROUTINE").assertIsDisplayed()
        compose.onNodeWithText("WRITE IT MYSELF").assertIsDisplayed()
    }

    @Test
    fun `a step that has not come round yet cannot be ticked off`() {
        val ahead = previewState().copy(next = previewState().next?.copy(hasArrived = false))
        render(ahead)

        scrollTo("DONE")
        compose.onNodeWithText("Comes round at", substring = true).assertIsDisplayed()
        compose.onNodeWithText("DONE").assertIsNotEnabled()
    }

    @Test
    fun `a step that has not come round yet can still be skipped in advance`() {
        val ahead = previewState().copy(next = previewState().next?.copy(hasArrived = false))
        render(ahead)

        scrollTo("SKIP TODAY")
        compose.onNodeWithText("SKIP TODAY").assertIsEnabled()
    }

    @Test
    fun `skipping asks what happened before it settles anything`() {
        var skipped: Pair<Long, SkipChip?>? = null
        render(previewState(), TodayActions.None.copy(onSkip = { id, chip -> skipped = id to chip }))

        scrollTo("SKIP TODAY")
        compose.onNodeWithText("SKIP TODAY").performClick()

        // The sheet, not the settle. Nothing has been recorded yet.
        compose.onNodeWithText("WHAT HAPPENED?").assertIsDisplayed()
        assertThat(skipped).isNull()

        compose.onNodeWithText("WORK CAME UP").performClick()
        assertThat(skipped).isEqualTo(3L to SkipChip.WORK_CAME_UP)
    }

    @Test
    fun `a skip with no reason still settles the step`() {
        var skipped: Pair<Long, SkipChip?>? = null
        render(previewState(), TodayActions.None.copy(onSkip = { id, chip -> skipped = id to chip }))

        scrollTo("SKIP TODAY")
        compose.onNodeWithText("SKIP TODAY").performClick()
        compose.onNodeWithText("SKIP WITHOUT A REASON").performClick()

        assertThat(skipped).isEqualTo(3L to null)
    }

    @Test
    fun `loading draws nothing rather than claiming there is no plan`() {
        render(TodayUiState.Loading)

        compose.onAllNodesWithText("PASTE A ROUTINE").assertCountEquals(0)
        compose.onAllNodesWithText("NO PLAN YET").assertCountEquals(0)
    }

    @Test
    fun `a sick day with no smaller versions says so instead of taking credit`() {
        render(previewState().copy(isReduced = true, reducedCount = 0))

        compose.onNodeWithText("No step here has a smaller version yet.", substring = true).assertIsDisplayed()
    }

    @Test
    fun `everything settled shows the day complete panel`() {
        val done = previewState().copy(
            next = null,
            nowIndex = -1,
            entries = persistentListOf(previewState().entries.first()),
        )
        render(done)

        scrollTo("All done for today.")
        compose.onNodeWithText("All done for today.").assertIsDisplayed()
    }

    // Phase 3: catch up, milestones, the number, the note ---------------------

    @Test
    fun `an ordinary day shows no catch up panel`() {
        render(previewState())

        compose.onAllNodesWithText("STILL POSSIBLE").assertCountEquals(0)
    }

    @Test
    fun `a slipped day names when each missed step could still happen`() {
        render(previewState().copy(catchUp = catchUp()))

        scrollTo("STILL POSSIBLE")
        compose.onNodeWithText("18:20").assertIsDisplayed()
        compose.onNodeWithText("Evening read").assertIsDisplayed()
    }

    @Test
    fun `what will not fit is named rather than counted`() {
        render(previewState().copy(catchUp = catchUp()))

        scrollTo("No room left today")
        compose.onNodeWithText("No room left today for Long walk.").assertIsDisplayed()
    }

    @Test
    fun `a fourth missed step is left over, not declared impossible`() {
        // The three step limit is about what an evening absorbs, not about
        // whether the time exists. Saying there is no room would be a lie
        // the user can check against their own clock.
        render(previewState().copy(catchUp = catchUp()))

        scrollTo("was missed too")
        compose.onNodeWithText("Piano was missed too", substring = true).assertIsDisplayed()
    }

    @Test
    fun `with nothing left that fits, the panel stops calling it possible`() {
        val gone = catchUp().copy(steps = persistentListOf(), alsoMissed = persistentListOf())
        render(previewState().copy(catchUp = gone))

        scrollTo("OUT OF TIME")
        compose.onNodeWithText("OUT OF TIME").assertIsDisplayed()
        compose.onAllNodesWithText("could still fit", substring = true).assertCountEquals(0)
    }

    @Test
    fun `move hands back the occurrence and how far it has to go`() {
        var moved: Pair<Long, Int>? = null
        render(
            state = previewState().copy(catchUp = catchUp()),
            actions = TodayActions.None.copy(onMoveToSlot = { id, minutes -> moved = id to minutes }),
        )

        scrollTo("MOVE HERE")
        compose.onAllNodesWithText("MOVE HERE").onFirst().performClick()

        assertThat(moved).isEqualTo(3L to 500)
    }

    @Test
    fun `a milestone is said once and can be closed`() {
        var seen = false
        render(
            state = previewState().copy(milestone = MilestoneNotice(Milestone.FIRST_FULL_DAY)),
            actions = TodayActions.None.copy(onMilestoneSeen = { seen = true }),
        )

        compose.onNodeWithText("A whole day kept.").assertIsDisplayed()
        compose.onNodeWithText("GOT IT").performClick()

        assertThat(seen).isTrue()
    }

    @Test
    fun `the thirty day figure is shown once there is enough of it`() {
        render(previewState().copy(consistency = Consistency(goodDays = 24, days = 30)))

        compose.onNodeWithText("24 of the last 30 days went well").assertIsDisplayed()
    }

    @Test
    fun `three days is not enough to draw a thirty day figure`() {
        render(previewState().copy(consistency = Consistency(goodDays = 3, days = 3)))

        compose.onAllNodesWithText("went well", substring = true).assertCountEquals(0)
    }

    @Test
    fun `the note the user wrote is on the card when the step arrives`() {
        val card = previewState().next!!.copy(detail = "Shelf by the door")
        render(previewState().copy(next = card))

        compose.onNodeWithText("Shelf by the door").assertIsDisplayed()
    }

    @Test
    fun `a completed step can be given its number afterwards`() {
        var logged: Double? = null
        render(
            state = previewState().copy(
                askNumber = MeasurePrompt(3, 3, "Evening read", ValueKind.MINUTES),
            ),
            actions = TodayActions.None.copy(onLogNumber = { logged = it }),
        )

        compose.onNodeWithText("Optional. The step is already marked done.").assertIsDisplayed()
        // Nothing typed yet, so there is nothing to log.
        compose.onNodeWithText("LOG IT").assertIsNotEnabled()
        assertThat(logged).isNull()
    }

    @Test
    fun `the number question can be waved away and the step stays done`() {
        var dismissed = false
        render(
            state = previewState().copy(
                askNumber = MeasurePrompt(3, 3, "Evening read", ValueKind.MINUTES),
            ),
            actions = TodayActions.None.copy(onDismissNumber = { dismissed = true }),
        )

        compose.onNodeWithText("NOT NOW").performClick()

        assertThat(dismissed).isTrue()
    }

    /**
     * Two steps that could still happen, one that will not fit, one left over.
     *
     * Literal on purpose. A fixture built from the planner would test the
     * planner, which has its own tests, rather than what the screen says.
     */
    private fun catchUp() = CatchUpPanel(
        steps = persistentListOf(
            CatchUpRow(3, 3, "Evening read", "18:20", 50, moveByMinutes = 500, useMinimum = false),
            CatchUpRow(5, 5, "Ten minutes", "19:15", 10, moveByMinutes = 75, useMinimum = true),
        ),
        outOfTime = persistentListOf("Long walk"),
        alsoMissed = persistentListOf("Piano"),
    )

    // The goal at the top ------------------------------------------------------

    private fun weightGoal(todayReading: Double? = 50.5, daysElapsed: Int = 12) = GoalHeroUi(
        title = "Gain weight",
        kind = GoalKind.NUMBER,
        valueKind = ValueKind.WEIGHT_KG,
        current = 49.8,
        target = 55.0,
        startValue = 48.0,
        startDate = "1 Sep",
        changeSinceStart = 1.8,
        todayReading = todayReading,
        percent = 26,
        pacePercent = 40,
        standing = GoalStanding.BEHIND,
        daysLeft = 9,
        daysElapsed = daysElapsed,
        hasData = true,
        trail = persistentListOf(48.0, 48.4, 49.1, 49.8),
    )

    @Test
    fun `the goal card shows the average, today's reading and the change since the start`() {
        render(previewState().copy(goal = weightGoal()))

        compose.onNodeWithText("GAIN WEIGHT").assertIsDisplayed()
        compose.onNodeWithText("49.8").assertIsDisplayed()
        compose.onNodeWithText("7-DAY AVG").assertIsDisplayed()
        compose.onNodeWithText("50.5").assertIsDisplayed()
        compose.onNodeWithText("48").assertIsDisplayed()
        compose.onNodeWithText("55").assertIsDisplayed()
        compose.onNodeWithText("+1.8 kg since 1 Sep").assertIsDisplayed()
        compose.onNodeWithText("KG · 9 DAYS LEFT").assertIsDisplayed()
    }

    @Test
    fun `in the first week the card says the average is still settling`() {
        render(previewState().copy(goal = weightGoal(daysElapsed = 3)))

        compose.onNodeWithText("still settling", substring = true).assertIsDisplayed()
    }

    @Test
    fun `without a goal nothing about a goal is drawn`() {
        render(previewState())

        compose.onAllNodesWithText("DAYS LEFT", substring = true).assertCountEquals(0)
    }

    @Test
    fun `tapping the card opens the goal`() {
        var opened = false
        render(previewState().copy(goal = weightGoal()), TodayActions.None.copy(onOpenGoal = { opened = true }))

        compose.onNodeWithText("GAIN WEIGHT").performClick()

        assertThat(opened).isTrue()
    }
}
