package com.buildorbreak.convention

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/**
 * Gives convention plugins access to the root version catalog.
 *
 * rules.md section 4 forbids hardcoding a version anywhere but
 * `gradle/libs.versions.toml`, and that includes these plugins.
 */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.version(alias: String): String =
    findVersion(alias).get().requiredVersion

internal fun VersionCatalog.versionInt(alias: String): Int = version(alias).toInt()
