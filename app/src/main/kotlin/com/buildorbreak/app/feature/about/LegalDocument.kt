package com.buildorbreak.app.feature.about

import kotlinx.serialization.Serializable

/** The two documents the store asks for. Both short, both true. */
@Serializable
enum class LegalDocument {
    PRIVACY,
    TERMS,
}
