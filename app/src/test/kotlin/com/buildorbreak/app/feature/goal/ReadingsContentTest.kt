package com.buildorbreak.app.feature.goal

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.buildorbreak.core.designsystem.theme.BuildOrBreakTheme
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

private val TODAY: LocalDate = LocalDate.of(2026, 9, 12)

/**
 * The working behind the average.
 *
 * The screen's whole job is to be checkable, so the tests are about what it
 * shows rather than about what it computes: the date first, the number
 * beside it, and a warning before a correction changes history.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_MAX_SDK], qualifiers = "w411dp-h891dp-xxhdpi")
class ReadingsContentTest {

    @get:Rule
    val compose = createComposeRule()

    private fun render(
        state: ReadingsUiState,
        onEdit: (Long) -> Unit = {},
        onTyped: (String) -> Unit = {},
        onSave: () -> Unit = {},
        onDelete: () -> Unit = {},
    ) {
        compose.setContent {
            BuildOrBreakTheme {
                ReadingsContent(
                    state = state,
                    onAdd = {},
                    onEdit = onEdit,
                    onShiftDay = {},
                    onTyped = onTyped,
                    onSave = onSave,
                    onDelete = onDelete,
                    onCancel = {},
                    onBack = {},
                )
            }
        }
    }

    private fun loaded(editing: ReadingDraft? = null) = ReadingsUiState(
        loaded = true,
        title = "Gain three kilos",
        valueKind = ValueKind.WEIGHT_KG,
        rows = persistentListOf(
            ReadingRow(3, TODAY, 50.5, isToday = true),
            ReadingRow(2, TODAY.minusDays(1), 49.8, isToday = false),
        ),
        editing = editing,
    )

    @Test
    fun `every reading is listed with its day and its number`() {
        render(loaded())

        // The kicker sets its text in capitals; the resource is written in a
        // sentence so a translator can reuse it.
        compose.onNodeWithText("2 READINGS").assertIsDisplayed()
        compose.onNodeWithText("Sat 12 Sep").assertIsDisplayed()
        compose.onNodeWithText("50.5 kg").assertIsDisplayed()
        compose.onNodeWithText("Fri 11 Sep").assertIsDisplayed()
        compose.onNodeWithText("49.8 kg").assertIsDisplayed()
    }

    @Test
    fun `today is marked, because it is the one most likely to be wrong`() {
        render(loaded())

        compose.onNodeWithText("TODAY").assertIsDisplayed()
    }

    @Test
    fun `tapping a row asks to edit that row`() {
        var edited: Long? = null
        render(loaded(), onEdit = { edited = it })

        compose.onNodeWithText("Fri 11 Sep").performClick()

        assertThat(edited).isEqualTo(2L)
    }

    // The editor is rendered without its dialog. A Dialog opens a window of
    // its own and that window never reaches idle under Robolectric, so a test
    // through it times out on the frame clock rather than on the editor.
    private fun editor(
        typed: String,
        onSave: () -> Unit = {},
        onDelete: () -> Unit = {},
        onTyped: (String) -> Unit = {},
    ) {
        compose.setContent {
            BuildOrBreakTheme {
                ReadingEditor(
                    draft = ReadingDraft(id = 2, date = TODAY.minusDays(1), typed = typed),
                    unit = ValueKind.WEIGHT_KG,
                    failed = false,
                    onShiftDay = {},
                    onTyped = onTyped,
                    onSave = onSave,
                    onDelete = onDelete,
                    onCancel = {},
                )
            }
        }
    }

    @Test
    fun `the editor says plainly that a correction rewrites the average`() {
        editor(typed = "49.8")

        compose.onNodeWithText("Changing this rebuilds the average from that day on.").assertIsDisplayed()
    }

    @Test
    fun `save is off until the box holds a number`() {
        editor(typed = "")

        compose.onNodeWithText("SAVE").assertIsNotEnabled()
    }

    @Test
    fun `a typed number turns save on and removing is always offered`() {
        var saved = false
        var removed = false
        editor(typed = "72", onSave = { saved = true }, onDelete = { removed = true })

        compose.onNodeWithText("SAVE").assertIsEnabled().performClick()
        compose.onNodeWithText("REMOVE").performClick()

        assertThat(saved).isTrue()
        assertThat(removed).isTrue()
    }

    @Test
    fun `nothing logged yet says so rather than showing an empty list`() {
        render(ReadingsUiState.Empty.copy(loaded = true))

        compose.onNodeWithText("NO READINGS YET").assertIsDisplayed()
    }

    @Test
    fun `before the first read nothing is claimed at all`() {
        render(ReadingsUiState.Empty)

        compose.onAllNodesWithText("NO READINGS YET").assertCountEquals(0)
    }
}
