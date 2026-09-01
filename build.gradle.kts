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

allprojects {
    apply(plugin = rootProject.libs.plugins.spotless.get().pluginId)

    extensions.configure<SpotlessExtension> {
        kotlin {
            target("src/**/*.kt")
            targetExclude("**/build/**/*.kt")
            ktlint(rootProject.libs.versions.ktlint.get())
                .editorConfigOverride(
                    mapOf(
                        "ktlint_standard_function-naming" to "disabled",
                        "ktlint_standard_filename" to "disabled",
                        "ktlint_function_signature_rule_force_multiline_when_parameter_count_greater_or_equal_than" to "4",
                    ),
                )
            trimTrailingWhitespace()
            endWithNewline()
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint(rootProject.libs.versions.ktlint.get())
        }
    }
}

subprojects {
    apply(plugin = rootProject.libs.plugins.detekt.get().pluginId)

    extensions.configure<DetektExtension> {
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        buildUponDefaultConfig = true
        parallel = true
        autoCorrect = false
    }

    tasks.withType<Detekt>().configureEach {
        jvmTarget = rootProject.libs.versions.javaTarget.get()
        reports {
            html.required.set(true)
            xml.required.set(false)
            txt.required.set(false)
            sarif.required.set(false)
            md.required.set(false)
        }
    }
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
                    module.plugins.hasPlugin("com.android.base") -> "${module.path}:testDebugUnitTest"
                    module.plugins.hasPlugin("org.jetbrains.kotlin.jvm") -> "${module.path}:test"
                    else -> null
                }
            }
        },
    )
}
