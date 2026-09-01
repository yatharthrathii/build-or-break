package com.buildorbreak.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures cold and warm start against the budget in rules.md section 5:
 * under 500 ms to first frame on a mid range device.
 *
 * Five iterations is the balance between a stable number and how long you are
 * willing to sit and watch a phone.
 *
 *   ./gradlew :benchmark:connectedBenchmarkAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartupNoCompilation() = measureStartup(StartupMode.COLD, CompilationMode.None())

    @Test
    fun coldStartupWithBaselineProfile() =
        measureStartup(StartupMode.COLD, CompilationMode.Partial())

    @Test
    fun warmStartup() = measureStartup(StartupMode.WARM, CompilationMode.Partial())

    private fun measureStartup(startupMode: StartupMode, compilationMode: CompilationMode) {
        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            iterations = ITERATIONS,
            startupMode = startupMode,
            compilationMode = compilationMode,
        ) {
            pressHome()
            startActivityAndWait()
        }
    }

    private companion object {
        const val ITERATIONS = 5
    }
}
