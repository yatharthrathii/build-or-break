package com.buildorbreak.app.feature.plan

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.buildorbreak.core.designsystem.theme.BuildOrBreakTheme
import com.buildorbreak.core.model.enums.AnchorType
import com.buildorbreak.core.model.enums.ValueKind
import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val ROBOLECTRIC_MAX_SDK = 35

/**
 * The two things the editor has to say rather than leave to be guessed: what
 * each kind of timing means, and what a measured step has collected so far.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_MAX_SDK], qualifiers = "w411dp-h891dp-xxhdpi")
class ItemEditorContentTest {

    @get:Rule
    val compose = createComposeRule()

    private fun render(state: ItemEditorUiState, onChange: (ItemEditorUiState) -> Unit = {}) {
        compose.setContent {
            BuildOrBreakTheme {
                ItemEditorContent(state = state, onChange = onChange, onSave = {}, onArchive = {}, onCancel = {})
            }
        }
    }

    private fun scrollTo(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = true))
    }

    private fun step(kind: AnchorType = AnchorType.FIXED) =
        ItemEditorUiState.Empty.copy(title = "Shake", anchor = AnchorDraft(kind = kind))

    @Test
    fun `a fixed time says what fixed means`() {
        render(step())

        compose.onNodeWithText("Same time every day", substring = true).assertIsDisplayed()
    }

    @Test
    fun `relative says the thing nobody would guess, that it moves when its parent is late`() {
        render(step(AnchorType.RELATIVE))

        compose.onNodeWithText("If that step runs late, this one moves with it", substring = true).assertIsDisplayed()
    }

    @Test
    fun `a window and a repeat each say what they are for`() {
        render(step(AnchorType.WINDOW))
        compose.onNodeWithText("Any time between two times", substring = true).assertIsDisplayed()
    }

    @Test
    fun `picking another kind of timing is reported, so the hint can follow it`() {
        var changed: ItemEditorUiState? = null
        render(step()) { changed = it }

        compose.onNodeWithText("REPEAT").performClick()

        assertThat(changed?.anchor?.kind).isEqualTo(AnchorType.INTERVAL)
    }

    @Test
    fun `a step that has collected numbers shows them back`() {
        render(
            step().copy(
                isNew = false,
                valueKind = ValueKind.WEIGHT_KG,
                readings = persistentListOf(49.5, 50.0, 50.4, 51.2),
            ),
        )

        scrollTo("YOUR LAST 4 NUMBERS")
        compose.onNodeWithText("YOUR LAST 4 NUMBERS").assertIsDisplayed()
        // The columns speak as their range. The printed range under them is for eyes only.
        compose.onNodeWithContentDescription("49.5 to 51.2").assertExists()
    }

    @Test
    fun `one number is not a line, so nothing is drawn`() {
        render(step().copy(isNew = false, valueKind = ValueKind.WEIGHT_KG, readings = persistentListOf(49.5)))

        compose.onAllNodesWithText("number so far", substring = true).assertCountEquals(0)
    }

    @Test
    fun `a step that asks for nothing shows no chart even if old numbers exist`() {
        render(step().copy(isNew = false, valueKind = ValueKind.NONE, readings = persistentListOf(1.0, 2.0)))

        compose.onAllNodesWithText("YOUR LAST 2 NUMBERS").assertCountEquals(0)
    }
}
