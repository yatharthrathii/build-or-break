pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "BuildOrBreak"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// See techspec.md section 3. Eleven modules. This is a ceiling, not a target
// to grow past. Promote a feature package out of :app only when it exceeds
// roughly fifteen files or when a clean :app build passes forty five seconds.

include(":app")

// Pure JVM modules. These physically cannot import Android, which is the point.
include(":core:model")
include(":core:domain")
include(":core:common")
include(":core:testing")

// Android modules
include(":core:data")
include(":core:designsystem")
include(":scheduler")
include(":billing")
include(":widget")
include(":benchmark")
