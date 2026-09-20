package com.buildorbreak.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

/** Long enough for a cold screen to draw, short enough not to hang a run. */
private const val WAIT_MILLIS = 5_000L

/**
 * Generates the baseline profile that ships with the release build.
 *
 * techspec.md section 9 requires the profile to be regenerated and committed
 * before every release. Google measures 20 to 30 percent faster cold start from
 * this alone, which is most of the budget in rules.md section 5.
 *
 * Run on a rooted emulator or a physical device:
 *
 *   ./gradlew :app:generateBaselineProfile
 *
 * The walk matters as much as the fact of running it. A profile only warms
 * the code it actually saw, so cold start alone would leave every tab the
 * user opens next interpreting its Compose layout from scratch. This opens
 * each of the four tabs and scrolls the two that hold a list, which is what
 * the first thirty seconds of a real session look like.
 */
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        rule.collect(packageName = TARGET_PACKAGE) {
            pressHome()
            startActivityAndWait()

            device.waitForIdle()

            // The first run lands on onboarding, a later one on Today. Both
            // are worth warming, and neither is worth failing the run over,
            // so every step here is best effort.
            device.tapText("SHOW ME")
            device.tapText("SEE THE DAY")
            device.tapText("LOOKS RIGHT")
            device.tapText("START MY DAY")

            device.scrollDown()

            listOf("PLAN", "INSIGHTS", "SETTINGS", "TODAY").forEach { tab ->
                device.tapText(tab)
                device.scrollDown()
            }

            device.waitForIdle()
        }
    }
}

/** Taps a label if it is on screen, and does nothing if it is not. */
private fun UiDevice.tapText(text: String) {
    val found = wait(Until.findObject(By.text(text)), WAIT_MILLIS) ?: return

    found.click()
    waitForIdle()
}

/** Scrolls whatever on this screen scrolls. A screen with no list is left alone. */
private fun UiDevice.scrollDown() {
    findObject(By.scrollable(true))?.scroll(Direction.DOWN, 1f)
    waitForIdle()
}

internal const val TARGET_PACKAGE = "com.buildorbreak.app"
