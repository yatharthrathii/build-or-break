plugins {
    alias(libs.plugins.buildorbreak.android.library)
    alias(libs.plugins.buildorbreak.android.hilt)
}

android {
    namespace = "com.buildorbreak.scheduler"
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.designsystem)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(projects.core.testing)
    testImplementation(libs.robolectric)
}

// AlarmManager, notifications, the foreground service, every broadcast
// receiver, and the Android 16 Live Update. See techspec.md section 7.
//
// This is the hardest module in the project and the one the product lives or
// dies on. Budget accordingly.
