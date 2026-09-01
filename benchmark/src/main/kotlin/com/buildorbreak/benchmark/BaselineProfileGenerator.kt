package com.buildorbreak.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import org.junit.Rule
import org.junit.Test

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
 */
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        rule.collect(packageName = TARGET_PACKAGE) {
            pressHome()
            startActivityAndWait()

            // M5: extend this to walk the Today screen, scroll the timeline, and
            // open the block runner. A profile is only as good as the paths it
            // actually exercises, and cold start alone is not the whole story.
            device.waitForIdle()
        }
    }
}

internal const val TARGET_PACKAGE = "com.buildorbreak.app"
