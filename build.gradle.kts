import com.diffplug.gradle.spotless.SpotlessExtension
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
}

// rules.md section 4: warnings are errors in CI. Detekt and ktlint gate the
// build. A suppression needs a one line reason attached to it.

// Where we deliberately differ from ktlint's defaults, and why. Anything not
// listed here is enforced as shipped.
val ktlintOverrides =
    mapOf(
        // Composables are PascalCase by convention.
        "ktlint_standard_function-naming" to "disabled",
        // A file holding several small related types is clearer than several
        // near empty files, so the name does not have to match one class.
        "ktlint_standard_filename" to "disabled",
        // Wrapping every `libs.versions.x.get()` across three lines makes build
        // files harder to read, not easier.
        "ktlint_standard_chain-method-continuation" to "disabled",
        // Model types carry KDoc on individual fields. Collapsing a constructor
        // onto one line throws that away.
        "ktlint_standard_class-signature" to "disabled",
        "ktlint_function_signature_rule_force_multiline_when_parameter_count_greater_or_equal_than" to "4",
    )

allprojects {
    apply(
        plugin =
        rootProject.libs.plugins.spotless
            .get()
            .pluginId,
    )

    extensions.configure<SpotlessExtension> {
        kotlin {
            target("src/**/*.kt")
            targetExclude("**/build/**/*.kt")
            ktlint(rootProject.libs.versions.ktlint.get())
                .editorConfigOverride(ktlintOverrides)
            trimTrailingWhitespace()
            endWithNewline()
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint(rootProject.libs.versions.ktlint.get())
                .editorConfigOverride(ktlintOverrides)
        }
    }
}

subprojects {
    apply(
        plugin =
        rootProject.libs.plugins.detekt
            .get()
            .pluginId,
    )

    extensions.configure<DetektExtension> {
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        buildUponDefaultConfig = true
        parallel = true
        autoCorrect = false
    }

    tasks.withType<Detekt>().configureEach {
        jvmTarget =
            rootProject.libs.versions.javaTarget
                .get()
        reports {
            html.required.set(true)
            xml.required.set(false)
            txt.required.set(false)
            sarif.required.set(false)
            md.required.set(false)
        }
    }
}

// Pins how the wrapper is regenerated, so `./gradlew wrapper` is reproducible
// and does not depend on whoever ran it last.
//
// validateDistributionUrl is off because the check is a network call, and the
// distribution is already resolved from the local Gradle cache. The URL itself
// is still written into gradle-wrapper.properties and used on a clean machine.
tasks.wrapper {
    gradleVersion = "8.14.3"
    distributionType = Wrapper.DistributionType.BIN
    validateDistributionUrl = false
}

// Convenience aggregate used by CI. Keeps the workflow file short.
// Android modules expose `testDebugUnitTest`, pure JVM modules expose `test`.
// Resolving them lazily avoids depending on plugin application order.
tasks.register("qualityCheck") {
    group = "verification"
    description = "Runs spotless, detekt and every module's unit tests."
    dependsOn("spotlessCheck")
    dependsOn("detekt")
    dependsOn(
        provider {
            subprojects.mapNotNull { module ->
                when {
                    // :benchmark is a com.android.test module. Its tests only run
                    // on a connected device, so there is no unit test task to
                    // depend on. It also applies com.android.base, so this branch
                    // has to come first.
                    module.plugins.hasPlugin("com.android.test") -> null
                    module.plugins.hasPlugin("com.android.base") -> "${module.path}:testDebugUnitTest"
                    module.plugins.hasPlugin("org.jetbrains.kotlin.jvm") -> "${module.path}:test"
                    else -> null
                }
            }
        },
    )
}
