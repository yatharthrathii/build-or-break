package com.buildorbreak.app.feature.plan

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.buildorbreak.core.designsystem.theme.BuildOrBreakTheme
import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val ROBOLECTRIC_MAX_SDK = 35

/**
 * Two plans on a list, and the one tap that swaps them.
 *
 * The screen is small and the rules are few: the running one is marked and
 * cannot be "used" again, the last one cannot be deleted, and a new one
 * needs a name before it can be saved.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_MAX_SDK], qualifiers = "w411dp-h891dp-xxhdpi")
class PlansContentTest {

    @get:Rule
    val compose = createComposeRule()

    private fun render(
        state: PlansUiState,
        onUse: (Long) -> Unit = {},
        onAdd: (String) -> Unit = {},
        onDelete: (Long) -> Unit = {},
    ) {
        compose.setContent {
            BuildOrBreakTheme {
                PlansContent(
                    state = state,
                    onUse = onUse,
                    onAdd = onAdd,
                    onRename = { _, _ -> },
                    onDelete = onDelete,
                    onBack = {},
                )
            }
        }
    }

    private fun two() = PlansUiState(
        plans = persistentListOf(
            PlanRowUi(1, "Normal days", isRunning = true, since = "Sep 2026"),
            PlanRowUi(2, "Exam season", isRunning = false, since = "Oct 2026"),
        ),
    )

    @Test
    fun `the running plan is marked and the other one offers to take over`() {
        render(two())

        compose.onNodeWithText("RUNNING").assertIsDisplayed()
        compose.onAllNodesWithText("USE").assertCountEquals(1)
    }

    @Test
    fun `use hands back the plan to switch to`() {
        var used: Long? = null
        render(two(), onUse = { used = it })

        compose.onNodeWithText("USE").performClick()

        assertThat(used).isEqualTo(2L)
    }

    @Test
    fun `the last plan has no delete, because Today has to run from something`() {
        render(PlansUiState(plans = persistentListOf(two().plans.first())))

        compose.onNodeWithContentDescription("Edit Normal days").performClick()

        compose.onNodeWithText("EDIT PLAN").assertIsDisplayed()
        compose.onAllNodesWithText("DELETE").assertCountEquals(0)
    }

    @Test
    fun `with another plan left, delete is offered and says what goes with it`() {
        var deleted: Long? = null
        render(two(), onDelete = { deleted = it })

        compose.onNodeWithContentDescription("Edit Exam season").performClick()
        compose.onNodeWithText("Deleting a plan deletes its days", substring = true).assertIsDisplayed()
        compose.onNodeWithText("DELETE").performClick()

        assertThat(deleted).isEqualTo(2L)
    }

    @Test
    fun `a new plan needs a name, and hands it back once it has one`() {
        var added: String? = null
        render(two(), onAdd = { added = it })

        compose.onNodeWithText("NEW PLAN").performClick()
        compose.onNodeWithText("SAVE").assertIsNotEnabled()

        compose.onNode(hasSetTextAction()).performTextInput("Travel week")
        compose.onNodeWithText("SAVE").performClick()

        assertThat(added).isEqualTo("Travel week")
    }
}
