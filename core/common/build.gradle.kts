plugins {
    alias(libs.plugins.buildorbreak.jvm.library)
}

// Pure JVM on purpose. TimeProvider lives here and :core:domain depends on it,
// so this module can never become Android aware. Android specific helpers go
// in the module that needs them, not here.
