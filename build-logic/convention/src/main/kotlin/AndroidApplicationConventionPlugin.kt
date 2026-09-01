import com.android.build.api.dsl.ApplicationExtension
import com.buildorbreak.convention.configureKotlinAndroid
import com.buildorbreak.convention.libs
import com.buildorbreak.convention.versionInt
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("org.jetbrains.kotlin.android")

            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)

                defaultConfig {
                    targetSdk = libs.versionInt("targetSdk")
                    versionCode = 1
                    versionName = "0.1.0"
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                    vectorDrawables.useSupportLibrary = true
                }

                buildTypes {
                    getByName("debug") {
                        applicationIdSuffix = ".debug"
                        isMinifyEnabled = false
                    }
                    getByName("release") {
                        // rules.md section 5. Non negotiable for release builds.
                        isMinifyEnabled = true
                        isShrinkResources = true
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro",
                        )
                    }
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
                    checkDependencies = true
                }
            }
        }
    }
}
