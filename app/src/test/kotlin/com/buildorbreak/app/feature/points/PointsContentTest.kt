package com.buildorbreak.app.feature.points

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.buildorbreak.core.designsystem.theme.BuildOrBreakTheme
import com.buildorbreak.core.model.enums.PointReason
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val ROBOLECTRIC_MAX_SDK = 35

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_MAX_SDK], qualifiers = "w411dp-h891dp-xxhdpi")
class PointsContentTest {

    @get:Rule
    val compose = createComposeRule()

    private fun render(state: PointsUiState = loaded(), onWatchAd: () -> Unit = {}, onDismissAd: () -> Unit = {}) {
        compose.setContent {
            BuildOrBreakTheme {
                PointsContent(state = state, onWatchAd = onWatchAd, onDismissAd = onDismissAd, onBack = {})
            }
        }
    }

    private fun loaded(balance: Int = 640, adAvailable: Boolean = true, adNotReady: Boolean = false) = PointsUiState(
        loaded = true,
        balance = balance,
        earned = 940,
        spent = 300,
        adAvailable = adAvailable,
        unlocks = persistentListOf(
            UnlockUi(UnlockKind.UNDO_STEP, 150, affordable = true),
            UnlockUi(UnlockKind.SECOND_GOAL, null, affordable = false),
        ),
        movements = persistentListOf(
            MovementUi(LocalDate.of(2026, 9, 19), -150, PointReason.UNDO_STEP),
            MovementUi(LocalDate.of(2026, 9, 18), 110, null),
        ),
        adNotReady = adNotReady,
    )

    @Test
    fun `the balance and what it is made of are all on screen`() {
        render()

        compose.onNodeWithText("640").assertIsDisplayed()
        compose.onNodeWithText("940").assertIsDisplayed()
        compose.onNodeWithText("300").assertIsDisplayed()
    }

    @Test
    fun `a spend reads as a minus and a day reads as a plus`() {
        render()

        compose.onNodeWithText("−150").assertIsDisplayed()
        compose.onNodeWithText("+110").assertIsDisplayed()
    }

    @Test
    fun `something not built yet is marked rather than priced`() {
        render()

        compose.onNodeWithText("PLANNED").assertIsDisplayed()
        compose.onNodeWithText("Not built yet", substring = true).assertIsDisplayed()
    }

    @Test
    fun `the ad button says plainly that ads are not switched on`() {
        var tapped = false
        render(onWatchAd = { tapped = true })

        compose.onNodeWithText("WATCH").performClick()

        assertThat(tapped).isTrue()
    }

    @Test
    fun `tapping it explains, rather than quietly doing nothing`() {
        render(state = loaded(adNotReady = true))

        compose.onNodeWithText("Not ready yet").assertIsDisplayed()
        compose.onNodeWithText("does not use the internet at all today", substring = true).assertIsDisplayed()
    }

    @Test
    fun `once today's ad is done the button is off and says why`() {
        render(state = loaded(adAvailable = false))

        compose.onNodeWithText("Another tomorrow", substring = true).assertIsDisplayed()
        compose.onNodeWithText("WATCH").assertIsNotEnabled()
    }
}
