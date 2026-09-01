plugins {
    alias(libs.plugins.buildorbreak.android.library)
    alias(libs.plugins.buildorbreak.android.hilt)
}

android {
    namespace = "com.buildorbreak.billing"
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.model)
    implementation(projects.core.common)

    implementation(libs.androidx.datastore.preferences)

    testImplementation(projects.core.testing)
}

// rules.md section 4 and section 6: the billing SDK is only ever referenced
// inside this module. Features depend on EntitlementRepository, which lives in
// :core:domain, and never on anything in here.
//
// The RevenueCat dependency is added in M8, not now. Wiring the interface early
// and the SDK late is what keeps the swap cost at one module.
