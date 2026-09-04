plugins {
    alias(libs.plugins.buildorbreak.jvm.library)

    // The export format is a published contract, not an internal type, so it
    // is written with a real serialiser rather than by hand. Pure JVM, which
    // keeps the no Android rule below intact.
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.core.model)
    api(projects.core.common)

    implementation(libs.kotlinx.serialization.json)

    // Annotations only, a couple of kilobytes, no runtime and no Android. It
    // lets Hilt construct the use cases from their own constructors instead of
    // a hundred lines of @Provides in :app that would have to be edited every
    // time a use case gains a dependency.
    //
    // The rule this module keeps is that it stays pure JVM and cannot import
    // Android. A jar containing four annotations does not break that; pulling
    // in Dagger or Hilt would, and neither is here.
    implementation(libs.javax.inject)

    testImplementation(projects.core.testing)
}

// The timeline engine lives here. See techspec.md section 5.
//
// This module must stay pure JVM. Verify it by trying to add
//   import android.util.Log
// to any file in this module. It must fail to compile. If it ever compiles,
// something has leaked and it needs to be reverted, not worked around.
