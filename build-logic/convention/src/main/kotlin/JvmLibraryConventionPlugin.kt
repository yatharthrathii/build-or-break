import com.buildorbreak.convention.configureKotlinCompiler
import com.buildorbreak.convention.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/**
 * Pure JVM modules: `:core:model`, `:core:domain`, `:core:common`,
 * `:core:testing`.
 *
 * These modules have no Android dependency on the classpath at all, which is
 * what physically enforces rules.md section 4: the timeline engine cannot
 * import Android even by accident. That constraint is the single most valuable
 * architectural property in this project, so it is enforced by the build rather
 * than by discipline.
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")

            configureKotlinCompiler()

            dependencies {
                add("implementation", libs.findLibrary("kotlinx-coroutines-core").get())

                add("testImplementation", libs.findLibrary("junit5-api").get())
                add("testImplementation", libs.findLibrary("junit5-params").get())
                add("testRuntimeOnly", libs.findLibrary("junit5-engine").get())
                add("testImplementation", libs.findLibrary("truth").get())
                add("testImplementation", libs.findLibrary("turbine").get())
                add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
            }

            tasks.withType<Test>().configureEach {
                useJUnitPlatform()
                // implementationPlan.md M2 exit criteria: the domain suite runs
                // in under one second. Failing fast keeps that honest.
                testLogging {
                    events("failed")
                }
            }
        }
    }
}
