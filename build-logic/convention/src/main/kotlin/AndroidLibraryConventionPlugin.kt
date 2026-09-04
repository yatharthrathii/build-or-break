import com.android.build.gradle.LibraryExtension
import com.buildorbreak.convention.configureKotlinAndroid
import com.buildorbreak.convention.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("org.jetbrains.kotlin.android")

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)

                defaultConfig {
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }

                testOptions {
                    unitTests {
                        isIncludeAndroidResources = true
                        isReturnDefaultValues = true
                    }
                }

                lint {
                    warningsAsErrors = true
                    abortOnError = true

                    // Version nags are not correctness. warningsAsErrors turns
                    // "a newer Gradle exists" into a failing build, which would
                    // mean an upgrade nobody asked for becomes the only way to
                    // ship. Versions here are pinned on purpose and upgraded
                    // deliberately, not because lint noticed a release.
                    disable += setOf("AndroidGradlePluginVersion", "GradleDependency", "NewerVersionAvailable")
                }
            }

            dependencies {
                add("implementation", libs.findLibrary("kotlinx-coroutines-android").get())
                add("testImplementation", libs.findLibrary("junit4").get())
                add("testImplementation", libs.findLibrary("truth").get())
                add("testImplementation", libs.findLibrary("turbine").get())
                add("testImplementation", libs.findLibrary("mockk").get())
                add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
            }
        }
    }
}
