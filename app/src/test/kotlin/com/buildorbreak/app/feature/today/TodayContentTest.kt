package com.buildorbreak.app.feature.today

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.buildorbreak.core.designsystem.theme.BuildOrBreakTheme
import com.buildorbreak.core.model.enums.DeliveryTier
import com.buildorbreak.core.model.enums.Salience
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
@Config(sdk = [ROBOLECTRIC_MAX_SDK])
class TodayContentTest {

    @get:Rule
    val compose = createComposeRule()

    private fun entry(
        id: Long,
        time: String,
        title: String,
        done: Boolean = false,
        pinned: Boolean = false,
    ) = TimelineEntry(
        occurrenceId = id,
        itemId = id,
        time = time,
        title = title,
        detail = null,
        salience = Salience.NOTIFY,
        isDone = done,
        isMissed = false,
        isPinned = pinned,
        isDegraded = false,
        hasMinimum = false,
    )

    private fun stateOf(
        entries: List<TimelineEntry>,
        nowIndex: Int = 0,
        degradedTier: DeliveryTier? = null,
        hasPlan: Boolean = true,
    ) = TodayUiState(
        header = DayHeader(date = "Monday 5 January", subtitle = "Weekday", doneCount = 0, total = entries.size),
        entries = persistentListOf(*entries.toTypedArray()),
        nowIndex = nowIndex,
        budget = null,
        degradedTier = degradedTier,
        hasPlan = hasPlan,
    )

    private fun render(state: TodayUiState, onDone: (Long) -> Unit = {}, onOpenReliability: () -> Unit = {}) {
        compose.setContent {
            BuildOrBreakTheme {
                TodayContent(
                    state = state,
                    onDone = onDone,
                    onSnooze = {},
                    onSkip = {},
                    onOpenReliability = onOpenReliability,
                )
            }
        }
    }

    @Test
    fun `the day is drawn as a list of times and titles`() {
        render(stateOf(listOf(entry(1, "06:30", "Wake up"), entry(2, "07:00", "Medicine"))))

        compose.onNodeWithText("06:30").assertIsDisplayed()
        compose.onNodeWithText("Wake up").assertIsDisplayed()
        compose.onNodeWithText("Medicine").assertIsDisplayed()
    }

    @Test
    fun `actions appear on the next thing only`() {
        render(stateOf(listOf(entry(1, "06:30", "Wake up"), entry(2, "07:00", "Medicine")), nowIndex = 0))

        // One set of buttons, not one per row. A list where every row carries
        // three buttons is a wall of buttons.
        assertThat(countOf("Done")).isEqualTo(1)
        assertThat(countOf("Snooze")).isEqualTo(1)
    }

    @Test
    fun `tapping done reports the occurrence that was completed`() {
        var completed: Long? = null
        render(stateOf(listOf(entry(7, "06:30", "Wake up"))), onDone = { completed = it })

        compose.onNodeWithText("Done").performClick()

        assertThat(completed).isEqualTo(7L)
    }

    @Test
    fun `a settled row offers nothing to press`() {
        render(stateOf(listOf(entry(1, "06:30", "Wake up", done = true)), nowIndex = 0))

        assertThat(countOf("Done")).isEqualTo(0)
    }

    @Test
    fun `a degraded tier is shown with a way to fix it`() {
        var opened = false
        render(
            stateOf(listOf(entry(1, "06:30", "Wake up")), degradedTier = DeliveryTier.INEXACT_NOTIFICATION),
            onOpenReliability = { opened = true },
        )

        compose.onNodeWithText("Fix").performClick()

        assertThat(opened).isTrue()
    }

    @Test
    fun `the top tier says nothing at all`() {
        render(stateOf(listOf(entry(1, "06:30", "Wake up")), degradedTier = null))

        // A banner that is always on screen is a banner nobody reads.
        assertThat(countOf("Fix")).isEqualTo(0)
    }

    @Test
    fun `a fresh install is told what to do rather than shown an empty list`() {
        render(stateOf(emptyList(), hasPlan = false))

        compose.onNodeWithText("No plan yet").assertIsDisplayed()
    }

    @Test
    fun `a weekday with no steps says so`() {
        render(stateOf(emptyList(), hasPlan = true))

        compose.onNodeWithText("Nothing today").assertIsDisplayed()
    }

    /** How many nodes carry this text. Zero is a real and useful answer. */
    private fun countOf(text: String): Int = compose.onAllNodesWithText(text).fetchSemanticsNodes().size
}
