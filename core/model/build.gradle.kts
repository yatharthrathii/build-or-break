plugins {
    alias(libs.plugins.buildorbreak.jvm.library)
}

// Pure Kotlin data types only. See schema.md section 2.
// This module deliberately has no dependencies at all beyond the Kotlin
// standard library. If something here needs a dependency, it belongs in
// :core:domain instead.
