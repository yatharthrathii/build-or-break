plugins {
    alias(libs.plugins.buildorbreak.android.library)
    alias(libs.plugins.buildorbreak.android.hilt)
}

android {
    namespace = "com.buildorbreak.scheduler"
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.domain)
    implementation(projects.core.common)
    implementation(projects.core.data)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(projects.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
}

// Alarms, notifications, receivers and the foreground service. This is the
// module the product claim rests on: everything else can be correct and the app
// is still worthless if nothing fires at 06:30.
//
// The rule here is never to assume a capability. Exact alarms are denied by
// default from Android 14, full screen intents are auto granted only to calling
// and alarm apps since January 2025, and most phones sold in India ship a
// battery manager that will kill a background app regardless of either. So the
// scheduler detects what it is allowed to do at runtime, degrades in tiers, and
// says which setting would move it up.
