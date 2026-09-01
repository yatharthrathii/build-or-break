plugins {
    alias(libs.plugins.buildorbreak.jvm.library)
}

dependencies {
    api(projects.core.model)
    api(projects.core.common)

    api(libs.junit5.api)
    api(libs.truth)
    api(libs.turbine)
    api(libs.kotlinx.coroutines.test)
}

// Shared test fixtures. FakeTimeProvider lives here so every module tests
// against the same controllable clock.
