plugins {
    alias(libs.plugins.buildorbreak.android.library)
    alias(libs.plugins.buildorbreak.android.hilt)
    alias(libs.plugins.buildorbreak.android.room)
}

android {
    namespace = "com.buildorbreak.core.data"
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.domain)
    implementation(projects.core.common)

    implementation(libs.androidx.datastore.preferences)

    testImplementation(projects.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
}

// Room, DataStore and every repository implementation. architecture.md section 3:
// this module may depend on :core:domain, and :core:domain may never depend on
// it. The domain declares what it needs as an interface and this satisfies it,
// never the other way round.
//
// Nothing in here leaves the module. Entities, DAOs and DataStore keys are
// implementation detail; callers see models and repository interfaces only.
