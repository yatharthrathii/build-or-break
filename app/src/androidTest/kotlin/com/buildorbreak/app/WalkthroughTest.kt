package com.buildorbreak.app

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
import androidx.test.uiautomator.UiDevice
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
 * Runs in English, and expects the phone to be. Every wait below names a
 * piece of English copy, and a phone left in Hindi after checking the
 * translation turns the whole walkthrough into a fifteen second wait for a
 * button that says something else. The app locale cannot be pinned from in
 * here: changing it restarts the app's process, and this test runs inside
 * that process. Set it from the host first:
 * `adb shell cmd locale set-app-locales com.buildorbreak.app.debug --user 0 --locales en-US`.
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

        val dayIsLive = today()
        plan()
        insightsBeforeHistory()

        // Run after the last step of the starter day, there is no day left to
        // drive: the screen says it starts tomorrow, and shifting or skipping
        // nothing is not a thing the app offers. The screens above are still
        // worth walking, so the run reports what it saw rather than failing on
        // the clock.
        if (dayIsLive) {
            runningLate()
            skipAsksWhy()
        }
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
        // A cold start has to read whether the phone has been through this
        // before, and a check taken during that read sees neither screen. So
        // the wait is for whichever of the two turns up, and only then does
        // the test decide which walkthrough it is on.
        compose.waitUntil(DEFAULT_TIMEOUT) { exists("Show me") || tabExists(TODAY) }
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

    /**
     * Today, and whether there is any of it left.
     *
     * Returns false on a day whose steps have all gone by, which is what a
     * new plan looks like if it is made at midnight. The screen still has to
     * say something true, and that is what is checked in that case.
     */
    private fun today(): Boolean {
        tab(TODAY)
        awaitEither("STEPS KEPT TODAY", "Starts tomorrow")
        shot("today")

        if (!exists("STEPS KEPT TODAY")) {
            assertThat(exists("From tomorrow it runs from the top")).isTrue()

            return false
        }

        // The day is drawn, not merely the frame around it.
        assertThat(exists("THE DAY")).isTrue()
        assertThat(exists("RUNNING LATE")).isTrue()

        return true
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

    // Driving ----------------------------------------------------------------

    /**
     * The tab, by tag rather than by its word.
     *
     * `tap("PLAN")` used to match "MY ROUTINE PLAN" in the header of the screen
     * the test was already on. It tapped that, nothing navigated, and the test
     * went on to assert things about a screen it had never opened. It passed.
     * A tag cannot be hit by accident, which is the entire reason it is here.
     */
    private fun tabExists(name: String): Boolean =
        compose.onAllNodes(hasTestTag(navTag(name))).fetchSemanticsNodes().isNotEmpty()

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

    /**
     * Waits for whichever of two honest answers the screen gives.
     *
     * Used where the data itself decides the wording, so the assertion is
     * that the screen said something true rather than that the world was in
     * a particular state on the morning the test ran.
     */
    private fun awaitEither(first: String, second: String, timeout: Long = DEFAULT_TIMEOUT) {
        compose.waitUntil(timeout) { exists(first) || exists(second) }
    }

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
     *
     * Through UiAutomator, which screenshots at the shell rather than through
     * the app's own window. The instrumentation's capture returns black on an
     * emulator, and a folder of black rectangles is worse than an empty one.
     */
    private fun shot(name: String) {
        val file = File(shots, "%02d-%s.png".format(step++, name))

        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(file)
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
    }
}
