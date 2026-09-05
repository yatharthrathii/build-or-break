plugins {
    alias(libs.plugins.buildorbreak.android.library)
    alias(libs.plugins.buildorbreak.android.compose)
}

android {
    namespace = "com.buildorbreak.core.designsystem"
}

dependencies {
    implementation(projects.core.model)

    implementation(libs.androidx.compose.material.icons)

    testImplementation(projects.core.testing)
}

// Tokens, theme and the handful of components every screen shares.
//
// architecture.md section 3: this module may depend on :core:model and nothing
// else. It cannot see the domain, the data layer or a use case, which is what
// stops a component from quietly reaching for a repository and turning a
// reusable piece of UI into something only one screen can use.
