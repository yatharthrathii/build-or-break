package com.buildorbreak.app.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Every screen, as a type.
 *
 * Navigation 3 takes typed keys rather than strings, which is the reason to use
 * it here: a route that does not exist is a compile error instead of a blank
 * screen somebody finds in production. They are serialisable so the back stack
 * survives process death, which on a phone with an aggressive battery manager is
 * not a rare event.
 */
@Serializable
data object TodayRoute : NavKey

@Serializable
data object ReliabilityRoute : NavKey
