plugins {
    alias(libs.plugins.buildorbreak.android.library)
    alias(libs.plugins.buildorbreak.android.hilt)
}

android {
    namespace = "com.buildorbreak.widget"
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.model)
    implementation(projects.core.common)

    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    testImplementation(projects.core.testing)
}

// Glance home screen widget. One size that works, not five that are mediocre.
// See design.md section 12. The paper grain is deliberately omitted here so the
// widget stays flat and cheap to redraw.
