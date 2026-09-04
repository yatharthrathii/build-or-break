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

    testImplementation(projects.core.testing)
}

// The timeline engine lives here. See techspec.md section 5.
//
// This module must stay pure JVM. Verify it by trying to add
//   import android.util.Log
// to any file in this module. It must fail to compile. If it ever compiles,
// something has leaked and it needs to be reverted, not worked around.
