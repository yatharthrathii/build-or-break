plugins {
    alias(libs.plugins.buildorbreak.android.library)
    alias(libs.plugins.buildorbreak.android.compose)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.buildorbreak.core.designsystem"
}

dependencies {
    api(libs.androidx.compose.material.icons)
    implementation(libs.kotlinx.collections.immutable)

    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.rule)
    testImplementation(libs.androidx.compose.ui.test.junit4)
}

// Every colour token, type style, shape, glyph and shared composable.
// design.md is the specification. Nothing in this module invents a value.
