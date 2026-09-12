package com.buildorbreak.app.feature.about

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
 * The three pages a store listing points at.
 *
 * Nothing clever happens on them, which is exactly why they need a test: a
 * page nobody opens during development is a page that ships broken.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_MAX_SDK], qualifiers = "w411dp-h891dp-xxhdpi")
class AboutContentTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `about says what the app is and shows the version`() {
        compose.setContent {
            BuildOrBreakTheme { AboutContent(version = "0.3.0", onOpenLegal = {}, onBack = {}) }
        }

        compose.onNodeWithText("0.3.0", substring = true).assertIsDisplayed()
        compose.onNodeWithText("runs the daily routine you already have", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Everything you enter stays on this phone", substring = true).assertIsDisplayed()
    }

    @Test
    fun `the two documents open from about`() {
        var opened: LegalDocument? = null
        compose.setContent {
            BuildOrBreakTheme { AboutContent(version = "0.3.0", onOpenLegal = { opened = it }, onBack = {}) }
        }

        compose.onNodeWithText("Privacy policy").performClick()
        assertThat(opened).isEqualTo(LegalDocument.PRIVACY)

        compose.onNodeWithText("Terms of use").performClick()
        assertThat(opened).isEqualTo(LegalDocument.TERMS)
    }

    @Test
    fun `the privacy policy says nothing leaves the phone`() {
        compose.setContent {
            BuildOrBreakTheme { LegalContent(document = LegalDocument.PRIVACY, onBack = {}) }
        }

        compose.onNodeWithText("PRIVACY POLICY").assertIsDisplayed()
        compose.onNodeWithText("This app collects nothing about you", substring = true).assertIsDisplayed()
    }

    @Test
    fun `the terms say plainly that alarms can fail`() {
        compose.setContent {
            BuildOrBreakTheme { LegalContent(document = LegalDocument.TERMS, onBack = {}) }
        }

        compose.onNodeWithText("TERMS OF USE").assertIsDisplayed()
        compose.onNodeWithText("ALARMS CAN FAIL").assertIsDisplayed()
    }

    @Test
    fun `back goes back`() {
        var back = false
        compose.setContent {
            BuildOrBreakTheme { LegalContent(document = LegalDocument.TERMS, onBack = { back = true }) }
        }

        compose.onNodeWithContentDescription("Back").performClick()

        assertThat(back).isTrue()
    }
}
