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
        earned = 890,
        spent = 250,
        adAvailable = adAvailable,
        unlocks = persistentListOf(
            UnlockUi(UnlockKind.UNDO_STEP, 150, affordable = true),
            UnlockUi(UnlockKind.SECOND_GOAL, 300, affordable = true),
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
        compose.onNodeWithText("890").assertIsDisplayed()
        compose.onNodeWithText("250").assertIsDisplayed()
    }

    @Test
    fun `a spend reads as a minus and a day reads as a plus`() {
        render()

        compose.onNodeWithText("−150").assertIsDisplayed()
        compose.onNodeWithText("+110").assertIsDisplayed()
    }

    @Test
    fun `a second goal is priced, and says where it is bought`() {
        render()

        compose.onNodeWithText("A second goal").assertIsDisplayed()
        compose.onNodeWithText("Add it from the Goal screen", substring = true).assertIsDisplayed()
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

    private fun withFreeze(freeze: FreezeUi?) = loaded().copy(
        unlocks = persistentListOf(UnlockUi(UnlockKind.STREAK_FREEZE, 200, affordable = true)),
        freeze = freeze,
    )

    private val offer = FreezeUi(
        date = LocalDate.of(2026, 9, 19),
        runNow = 1,
        runAfter = 12,
        cost = 200,
        affordable = true,
    )

    @Test
    fun `a broken run names the day and offers to cover it`() {
        var asked = false
        compose.setContent {
            BuildOrBreakTheme {
                PointsContent(
                    state = withFreeze(offer),
                    onWatchAd = {},
                    onDismissAd = {},
                    onBack = {},
                    freezeActions = FreezeActions(onAsk = { asked = true }),
                )
            }
        }

        compose.onNodeWithText("Sat 19 Sep", substring = true).assertIsDisplayed()
        compose.onNodeWithText("COVER IT").performClick()

        assertThat(asked).isTrue()
    }

    @Test
    fun `with no broken run the row says so instead of showing a dead price`() {
        render(withFreeze(null))

        compose.onNodeWithText("Nothing to cover right now", substring = true).assertIsDisplayed()
    }

    @Test
    fun `too few points turns the button off and says how many are needed`() {
        render(withFreeze(offer.copy(affordable = false)))

        compose.onNodeWithText("COVER IT").assertIsNotEnabled()
        compose.onNodeWithText("You need 200 points", substring = true).assertIsDisplayed()
    }

    @Test
    fun `points are only taken after the second tap`() {
        var confirmed = false
        compose.setContent {
            BuildOrBreakTheme {
                PointsContent(
                    state = withFreeze(offer.copy(asking = true)),
                    onWatchAd = {},
                    onDismissAd = {},
                    onBack = {},
                    freezeActions = FreezeActions(onConfirm = { confirmed = true }),
                )
            }
        }

        compose.onNodeWithText("This costs 200 points", substring = true).assertIsDisplayed()
        assertThat(confirmed).isFalse()

        compose.onNodeWithText("COVER FOR 200").performClick()
        assertThat(confirmed).isTrue()
    }
}
