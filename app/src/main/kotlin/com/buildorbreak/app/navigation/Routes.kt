package com.buildorbreak.app.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Every screen, as a type.
 *
 * Navigation 3 takes typed keys rather than strings, which is the reason to use
 * it here: a route that does not exist is a compile error instead of a blank
 * screen somebody finds in production, and an argument of the wrong type cannot
 * be passed at all. They are serialisable so the back stack survives process
 * death, which on a phone with an aggressive battery manager is not rare.
 */
@Serializable
data object TodayRoute : NavKey

@Serializable
data object ReliabilityRoute : NavKey

@Serializable
data object PlanRoute : NavKey

@Serializable
data object ImportRoute : NavKey

/** [itemId] of zero opens the editor on a new step rather than an existing one. */
@Serializable
data class ItemEditorRoute(val itemId: Long) : NavKey {
    companion object {
        const val NEW_ITEM = 0L
    }
}
