import com.android.build.api.dsl.ApplicationExtension
import com.android.build.gradle.LibraryExtension
import com.buildorbreak.convention.configureAndroidCompose
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            when {
                pluginManager.hasPlugin("com.android.application") ->
                    extensions.configure<ApplicationExtension> { configureAndroidCompose(this) }

                pluginManager.hasPlugin("com.android.library") ->
                    extensions.configure<LibraryExtension> { configureAndroidCompose(this) }

                else -> error(
                    "buildorbreak.android.compose needs the android application or library " +
                        "plugin to be applied first in ${target.path}",
                )
            }
        }
    }
}
