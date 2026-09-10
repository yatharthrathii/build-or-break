package com.buildorbreak.app.feature.today

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
import com.buildorbreak.core.model.enums.SkipChip
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

        scrollTo("Nothing left on the rails.")
        compose.onNodeWithText("Nothing left on the rails.").assertIsDisplayed()
    }
}
