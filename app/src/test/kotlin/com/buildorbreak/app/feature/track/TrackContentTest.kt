package com.buildorbreak.app.feature.track

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.buildorbreak.core.designsystem.theme.BuildOrBreakTheme
import com.buildorbreak.core.model.enums.TrackUnitState
import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val ROBOLECTRIC_MAX_SDK = 35

/**
 * A syllabus on screen: where it stands, the part that is next, and the
 * three answers a tap on a part opens.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_MAX_SDK], qualifiers = "w411dp-h891dp-xxhdpi")
class TrackContentTest {

    @get:Rule
    val compose = createComposeRule()

    private fun render(
        state: TrackUiState,
        onSetState: (Long, TrackUnitState) -> Unit = { _, _ -> },
        onSave: (String, String) -> Unit = { _, _ -> },
        countParts: (String) -> Int = { text -> text.lines().count { it.isNotBlank() } },
    ) {
        compose.setContent {
            BuildOrBreakTheme {
                TrackContent(
                    state = state,
                    countParts = countParts,
                    onSave = onSave,
                    onSetState = onSetState,
                    onDelete = {},
                    onBack = {},
                )
            }
        }
    }

    private fun course() = TrackUiState(
        id = 1,
        name = "Backend course",
        text = "HTTP basics\nREST and JSON\nAuthentication",
        units = persistentListOf(
            UnitUi(1, 1, "HTTP basics", 30, TrackUnitState.DONE, isNext = false),
            UnitUi(2, 2, "REST and JSON", null, TrackUnitState.IN_PROGRESS, isNext = true),
            UnitUi(3, 3, "Authentication", 45, TrackUnitState.PENDING, isNext = false),
        ),
        position = 2,
        total = 3,
        finished = 1,
        minutesSpent = 40,
        leftOff = "Page 12, the JSON example",
    )

    @Test
    fun `the heading says which part is next and how much is done`() {
        render(course())

        compose.onNodeWithText("Part 2 of 3").assertIsDisplayed()
        compose.onNodeWithText("1 DONE · 40 MIN SPENT").assertIsDisplayed()
    }

    @Test
    fun `the next part carries the note on where the last sitting stopped`() {
        render(course())

        compose.onNodeWithText("You stopped at: Page 12, the JSON example").assertIsDisplayed()
    }

    @Test
    fun `every part is listed in order, with its estimate`() {
        render(course())

        compose.onNodeWithText("HTTP basics").assertIsDisplayed()
        compose.onNodeWithText("REST and JSON").assertIsDisplayed()
        compose.onNodeWithText("Authentication").assertIsDisplayed()
        compose.onNodeWithText("45 min").assertIsDisplayed()
    }

    @Test
    fun `a tap on a part opens the three answers, and skip hands back skipped`() {
        var marked: Pair<Long, TrackUnitState>? = null
        render(course(), onSetState = { id, state -> marked = id to state })

        compose.onNodeWithText("Authentication").performClick()
        compose.onNodeWithText("NOT YET").assertIsDisplayed()
        compose.onNodeWithText("SKIP THIS PART").performClick()

        assertThat(marked).isEqualTo(3L to TrackUnitState.SKIPPED)
    }

    @Test
    fun `every part done says so instead of pointing at a part that is not there`() {
        render(course().copy(position = 0, finished = 3, leftOff = null))

        compose.onNodeWithText("All 3 parts done").assertIsDisplayed()
        compose.onAllNodesWithText("You stopped at", substring = true).assertCountEquals(0)
    }

    @Test
    fun `the editor counts the parts and refuses an empty list`() {
        var saved: Pair<String, String>? = null
        render(course(), onSave = { name, text -> saved = name to text })

        compose.onNodeWithText("EDIT THE LIST").performClick()
        compose.onNodeWithText("3 parts found").assertIsDisplayed()
        compose.onNodeWithText("SAVE").assertIsEnabled()

        compose.onNodeWithText("HTTP basics\nREST and JSON\nAuthentication").performTextInput("\nDeployment\n")
        compose.onNodeWithText("4 parts found").assertIsDisplayed()
        compose.onNodeWithText("SAVE").performClick()

        assertThat(saved?.first).isEqualTo("Backend course")
        assertThat(saved?.second).contains("Deployment")
    }

    @Test
    fun `an empty list cannot be saved, and says why`() {
        render(course().copy(text = ""), countParts = { 0 })

        compose.onNodeWithText("EDIT THE LIST").performClick()

        compose.onNodeWithText("No parts found yet. One part per line.").assertIsDisplayed()
        compose.onNodeWithText("SAVE").assertIsNotEnabled()
    }

    @Test
    fun `a deleted syllabus says so rather than drawing an empty one`() {
        render(TrackUiState.Loading.copy(isMissing = true))

        compose.onNodeWithText("GONE").assertIsDisplayed()
    }
}
