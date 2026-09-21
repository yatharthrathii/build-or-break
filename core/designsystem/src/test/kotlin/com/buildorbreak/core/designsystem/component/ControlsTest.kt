package com.buildorbreak.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.buildorbreak.core.designsystem.theme.BuildOrBreakTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val ROBOLECTRIC_MAX_SDK = 35

/**
 * The controls every screen is built from.
 *
 * Each one is tested through a screen somewhere, which is how a control gets
 * broken without anybody noticing: the screen test fails, and it reads as the
 * screen's fault. These say what each control promises on its own.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_MAX_SDK], qualifiers = "w411dp-h891dp-xxhdpi")
class ControlsTest {

    @get:Rule
    val compose = createComposeRule()

    private fun render(content: @Composable () -> Unit) {
        compose.setContent { BuildOrBreakTheme { content() } }
    }

    @Test
    fun `tabs shout their labels and report the index that was tapped`() {
        var picked = -1
        render { SegmentedTabs(options = listOf("Fixed", "Relative"), selectedIndex = 0, onSelect = { picked = it }) }

        compose.onNodeWithText("RELATIVE").performClick()

        assertThat(picked).isEqualTo(1)
    }

    @Test
    fun `a toggle reads as on or off, and asks for the opposite when tapped`() {
        var asked: Boolean? = null
        render { SquareToggle(checked = true, onCheckedChange = { asked = it }) }

        compose.onNode(isToggleable()).assertIsOn()
        compose.onNode(isToggleable()).performClick()

        assertThat(asked).isFalse()
    }

    @Test
    fun `a toggle that is off says so`() {
        render { SquareToggle(checked = false, onCheckedChange = {}) }

        compose.onNode(isToggleable()).assertIsOff()
    }

    @Test
    fun `a stepper shows its value and its two buttons each do one thing`() {
        var value = 8
        render {
            Stepper(
                decrementLabel = "Less",
                incrementLabel = "More",
                value = "8 weeks",
                onDecrement = { value-- },
                onIncrement = { value++ },
            )
        }

        compose.onNodeWithText("8 weeks").assertIsDisplayed()
        compose.onNodeWithText("+").performClick()
        compose.onNodeWithText("+").performClick()
        compose.onNodeWithText("−").performClick()

        assertThat(value).isEqualTo(9)
    }

    @Test
    fun `a button that is off cannot be pressed`() {
        var pressed = false
        render { OutlineButton(text = "Cover it", onClick = { pressed = true }, enabled = false) }

        compose.onNodeWithText("COVER IT").assertIsNotEnabled()
        compose.onNodeWithText("COVER IT").performClick()

        assertThat(pressed).isFalse()
    }

    @Test
    fun `the columns speak as the description they were given`() {
        render { TrailColumns(values = listOf(49.5, 50.0, 51.2), description = "49.5 to 51.2", height = 40.dp) }

        compose.onNodeWithContentDescription("49.5 to 51.2").assertExists()
    }

    @Test
    fun `no numbers draws nothing, not an empty chart`() {
        render { TrailColumns(values = emptyList(), description = "nothing", height = 40.dp) }

        compose.onNodeWithContentDescription("nothing").assertDoesNotExist()
    }

    @Test
    fun `identical numbers still draw, because flat is a shape too`() {
        render { TrailColumns(values = listOf(50.0, 50.0, 50.0), description = "50 to 50", height = 40.dp) }

        compose.onNodeWithContentDescription("50 to 50").assertExists()
    }

    @Test
    fun `a label is set in capitals without the caller having to shout`() {
        render { Label(text = "To spend") }

        compose.onNodeWithText("TO SPEND").assertIsDisplayed()
        compose.onAllNodesWithText("To spend").assertCountEquals(0)
    }
}
