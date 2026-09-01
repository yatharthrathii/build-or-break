plugins {
    alias(libs.plugins.buildorbreak.jvm.library)
}

dependencies {
    api(projects.core.model)
    api(projects.core.common)

    testImplementation(projects.core.testing)
}

// The timeline engine lives here. See techspec.md section 5.
//
// This module must stay pure JVM. Verify it by trying to add
//   import android.util.Log
// to any file in this module. It must fail to compile. If it ever compiles,
// something has leaked and it needs to be reverted, not worked around.
