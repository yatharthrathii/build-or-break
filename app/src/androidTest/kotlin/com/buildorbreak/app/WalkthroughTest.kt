package com.buildorbreak.app

import android.graphics.Bitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.buildorbreak.core.designsystem.component.navTag
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The whole app, driven on a real device.
 *
 * Robolectric answers whether a composable draws what its state says. It cannot
 * answer whether the app survives a real Hilt graph, a real database and a real
 * launcher, and that is where the failures have actually been.
 *
 * Every step waits for something it can name before it asserts, rather than for
 * a fixed number of milliseconds. `waitForIdle` is not enough on its own here:
 * the day arrives from a database flow, and a screen that is idle is not the
 * same as a screen that has its data. A screenshot is left behind at each step
 * so a run can be looked at as well as passed.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class WalkthroughTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val shots: File by lazy {
        File(compose.activity.getExternalFilesDir(null), "walkthrough").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    private var step = 0

    @Test
    fun everyScreenOpensAndDoesSomething() {
        firstRun()

        today()
        plan()
        insightsBeforeHistory()
        loadDemoHistory()
        insightsAfterHistory()
        runningLate()
        skipAsksWhy()
        clearDemoHistory()
    }

    /**
     * The four first run screens, when they are there.
     *
     * Tolerant on purpose: the phone may already have been through them, and a
     * walkthrough that only works on a clean install is one nobody runs twice.
     *
     * The third screen is the one worth having a test for. Permissions used to
     * be asked for before the app had shown anything at all, and the whole
     * point of the preview is that it now comes first.
     */
    private fun firstRun() {
        if (!exists("Show me")) return

        shot("onboarding-welcome")
        tap("Show me")

        await("WHAT ARE YOU")
        shot("onboarding-choose")
        tap("See the day")

        await("HERE IS")
        shot("onboarding-preview")

        // The day is drawn from the starter, not promised in the abstract.
        assertThat(exists("Wake + water")).isTrue()
        assertThat(exists("will ring")).isTrue()

        tap("Looks right")

        await("THREE PERMISSIONS")
        shot("onboarding-permissions")
        tap("START MY DAY")
    }

    private fun today() {
        tab(TODAY)
        await("STEPS KEPT TODAY")
        shot("today")

        // The day is drawn, not merely the frame around it.
        assertThat(exists("THE DAY")).isTrue()
        assertThat(exists("RUNNING LATE")).isTrue()
    }

    /**
     * The plan, and proof that it is the plan.
     *
     * The add control is asserted rather than the word "steps", which appears
     * on Today as well. This is the screen the tag fix was written for, so it
     * checks something that only exists here.
     */
    private fun plan() {
        tab(PLAN)
        await("Add a step")
        shot("plan")

        assertThat(exists("Weekday")).isTrue()
    }

    /** A brand new plan has no history, and the screen has to say that rather than draw an empty chart. */
    private fun insightsBeforeHistory() {
        tab(INSIGHTS)
        await("NOTHING TO SHOW YET")
        shot("insights-before")
    }

    private fun loadDemoHistory() {
        tab(SETTINGS)
        await("ALARMS")
        shot("settings-top")

        scrollTo("Load twelve weeks of history")
        shot("settings-demo")
        tap("Load twelve weeks of history")

        // Around nine hundred rows, one settle each. Waited on by the row's own
        // text rather than by a sleep, so a fast device is not punished for it.
        await("Wrote", timeout = SEED_TIMEOUT)
        shot("settings-demo-done")
    }

    private fun insightsAfterHistory() {
        tab(INSIGHTS)
        await("KEPT PER DAY")
        shot("insights-week")

        tap("MONTH")
        await("KEPT PER WEEK")
        shot("insights-month")

        // The whole reason the skip sheet asks. Checked on the month rather
        // than the week: a week that is one day old can honestly contain no
        // skips at all, and a test that fails on a Tuesday is not a test.
        assertThat(exists("Why steps were skipped")).isTrue()
    }

    private fun runningLate() {
        tab(TODAY)
        await("RUNNING LATE")
        tap("RUNNING LATE")

        await("HOW LATE?")
        shot("running-late-sheet")
        tap("+90")

        await("Day shifted")
        shot("today-shifted")
        assertThat(exists("UNDO")).isTrue()

        tap("UNDO")
        await("Everything on its planned time")
        shot("today-unshifted")
    }

    /**
     * The skip sheet has to come first, and it has to ask the right question.
     *
     * A step whose time has passed is asked what happened; one that has not
     * come round yet is asked whether it is being dropped, because asking what
     * happened about something that has not happened reads as a bug.
     */
    private fun skipAsksWhy() {
        val ahead = exists("Comes round at")

        scrollTo("SKIP TODAY")
        tap("SKIP TODAY")

        await(if (ahead) "NOT DOING THIS TODAY?" else "WHAT HAPPENED?")
        shot("skip-sheet")
        assertThat(exists("Skip without a reason")).isTrue()

        tap("Work came up")
        await("STEPS KEPT TODAY")
        shot("today-after-skip")

        // A settle that cannot be taken back is a history that quietly drifts
        // from the truth, so the offer has to actually appear.
        assertThat(exists("Skipped")).isTrue()
        shot("today-undo-offered")

        tap("Undo")
        compose.waitForIdle()
        shot("today-undone")
    }

    private fun clearDemoHistory() {
        tab(SETTINGS)
        scrollTo("Delete the demo history")
        tap("Delete the demo history")
        compose.waitForIdle()
        shot("settings-demo-cleared")
    }

    // Driving ----------------------------------------------------------------

    /**
     * The tab, by tag rather than by its word.
     *
     * `tap("PLAN")` used to match "MY ROUTINE PLAN" in the header of the screen
     * the test was already on. It tapped that, nothing navigated, and the test
     * went on to assert things about a screen it had never opened. It passed.
     * A tag cannot be hit by accident, which is the entire reason it is here.
     */
    private fun tab(name: String) {
        compose.waitUntilAtLeastOneExists(hasTestTag(navTag(name)), timeoutMillis = DEFAULT_TIMEOUT)
        compose.onNodeWithTag(navTag(name)).performClick()
        compose.waitForIdle()
    }

    /**
     * Waits for the control before it presses it.
     *
     * A navigation that has been asked for is not a navigation that has
     * happened, and every flake in this test came from the difference.
     */
    private fun tap(text: String) {
        await(text)
        node(text).performClick()
        compose.waitForIdle()
    }

    private fun node(text: String): SemanticsNodeInteraction =
        compose.onAllNodesWithText(text, substring = true, ignoreCase = true)[0]

    private fun exists(text: String): Boolean =
        compose.onAllNodesWithText(text, substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty()

    /** Waits for a screen to actually have its data, rather than merely to stop drawing. */
    private fun await(text: String, timeout: Long = DEFAULT_TIMEOUT) {
        compose.waitUntilAtLeastOneExists(hasText(text, substring = true, ignoreCase = true), timeoutMillis = timeout)
    }

    /** Brings a row into view before it is tapped. Harmless when nothing scrolls. */
    private fun scrollTo(text: String) {
        runCatching {
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = true, ignoreCase = true))
        }
        compose.waitForIdle()
    }

    /**
     * The whole screen, not the Compose tree.
     *
     * A bottom sheet is its own window, so capturing the root would either miss
     * the sheet or fail with two roots to choose from. The screen is what the
     * user sees and is the thing worth keeping.
     */
    private fun shot(name: String) {
        val file = File(shots, "%02d-%s.png".format(step++, name))
        val image = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()

        file.outputStream().use { out -> image.compress(Bitmap.CompressFormat.PNG, QUALITY, out) }
    }

    private companion object {
        // The natural case labels the bar is built from, not the upper case it
        // draws. The tag is made from the label, and the uppercasing is the
        // bar's own presentation.
        const val TODAY = "Today"
        const val PLAN = "Plan"
        const val INSIGHTS = "Insights"
        const val SETTINGS = "Settings"

        const val DEFAULT_TIMEOUT = 15_000L
        const val SEED_TIMEOUT = 180_000L
        const val QUALITY = 100
    }
}
