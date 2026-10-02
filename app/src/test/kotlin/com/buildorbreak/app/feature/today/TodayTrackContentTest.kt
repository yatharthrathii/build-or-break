package com.buildorbreak.app.feature.today

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.buildorbreak.core.designsystem.theme.BuildOrBreakTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The newest Android image Robolectric 4.14 can run. */
private const val ROBOLECTRIC_MAX_SDK = 35

/**
 * A step that follows a syllabus, or carries a link, on the card.
 *
 * Apart from `TodayContentTest`, which is about the day. This is about the
 * two things a step can bring with it to the moment it arrives, and the
 * question the sitting is asked afterwards.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_MAX_SDK], qualifiers = "w411dp-h891dp-xxhdpi")
class TodayTrackContentTest {

    @get:Rule
    val compose = createComposeRule()

    private fun render(state: TodayUiState, actions: TodayActions = TodayActions.None) {
        compose.setContent {
            BuildOrBreakTheme { TodayContent(state = state, actions = actions) }
        }
    }

    // A step that follows a syllabus, or carries a link ------------------------

    private fun trackLine(leftOff: String? = "Page 12, the JSON example") = TrackLineUi(
        trackId = 9,
        unitId = 2,
        name = "Backend course",
        unitTitle = "REST and JSON",
        position = 2,
        total = 30,
        leftOff = leftOff,
        estimateMinutes = 45,
    )

    @Test
    fun `a step on a syllabus says which part this sitting is, and where the last one stopped`() {
        val card = previewState().next!!.copy(track = trackLine())
        render(previewState().copy(next = card))

        compose.onNodeWithText("PART 2 OF 30 · BACKEND COURSE").assertIsDisplayed()
        compose.onNodeWithText("REST and JSON").assertIsDisplayed()
        compose.onNodeWithText("You stopped at: Page 12, the JSON example").assertIsDisplayed()
    }

    @Test
    fun `tapping the syllabus line opens the syllabus`() {
        var opened: Long? = null
        val card = previewState().next!!.copy(track = trackLine())
        render(previewState().copy(next = card), TodayActions.None.copy(onOpenTrack = { opened = it }))

        compose.onNodeWithText("REST and JSON").performClick()

        assertThat(opened).isEqualTo(9L)
    }

    @Test
    fun `a step with a link gets one button, named for the site`() {
        var opened: String? = null
        val card = previewState().next!!.copy(link = "https://www.youtube.com/watch?v=abc")
        render(previewState().copy(next = card), TodayActions.None.copy(onOpenLink = { opened = it }))

        compose.onNodeWithText("youtube.com").assertIsDisplayed()
        compose.onNodeWithText("OPEN LINK").performClick()

        assertThat(opened).isEqualTo("https://www.youtube.com/watch?v=abc")
    }

    @Test
    fun `the sitting question is asked after the settle, and hands back what was said`() {
        var logged: Triple<Boolean, Int, String>? = null
        render(
            state = previewState().copy(
                askSession = SessionPrompt(3, 2, "REST and JSON", position = 2, total = 30, minutes = 45),
            ),
            actions = TodayActions.None.copy(onLogSession = { finished, minutes, note ->
                logged =
                    Triple(finished, minutes, note)
            }),
        )

        compose.onNodeWithText("PART 2 OF 30").assertIsDisplayed()
        compose.onNodeWithText("The step is already marked done", substring = true).assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("Halfway through")
        compose.onNodeWithText("SAVE").performClick()

        assertThat(logged).isEqualTo(Triple(true, 45, "Halfway through"))
    }

    @Test
    fun `the sitting question can be waved away and the step stays done`() {
        var dismissed = false
        render(
            state = previewState().copy(
                askSession = SessionPrompt(3, 2, "REST and JSON", position = 2, total = 30, minutes = 45),
            ),
            actions = TodayActions.None.copy(onDismissSession = { dismissed = true }),
        )

        compose.onNodeWithText("NOT NOW").performClick()

        assertThat(dismissed).isTrue()
    }
}
