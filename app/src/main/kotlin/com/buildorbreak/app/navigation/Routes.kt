package com.buildorbreak.app.navigation

import androidx.navigation3.runtime.NavKey
import com.buildorbreak.app.feature.about.LegalDocument
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
data object OnboardingRoute : NavKey

@Serializable
data object TodayRoute : NavKey

@Serializable
data object PlanRoute : NavKey

@Serializable
data object InsightsRoute : NavKey

@Serializable
data object SettingsRoute : NavKey

@Serializable
data object ReliabilityRoute : NavKey

/** The one goal a plan is allowed, and the form for writing it. */
@Serializable
data object GoalRoute : NavKey

/** Every number behind a measured goal, and the one place they can be corrected. */
@Serializable
data class ReadingsRoute(
    /** Straight into the editor, for "add a reading" on the goal screen. */
    val addNow: Boolean = false,
    /** Which goal's numbers. Null is the measured one, for callers that cannot say. */
    val goalId: Long? = null,
) : NavKey

@Serializable
data object ImportRoute : NavKey

/** What the app is, and the two documents the store asks for. */
@Serializable
data object AboutRoute : NavKey

@Serializable
data object ContactRoute : NavKey

@Serializable
data object PointsRoute : NavKey

@Serializable
data class LegalRoute(val document: LegalDocument) : NavKey

/**
 * [itemId] of zero opens the editor on a new step rather than an existing one.
 *
 * [templateId] says which day a new step belongs to. An existing step knows
 * its own; without this a step added from the Weekend tab was written to the
 * weekday routine, and a step edited there was moved to it.
 */
@Serializable
data class ItemEditorRoute(val itemId: Long, val templateId: Long = 0) : NavKey {
    companion object {
        const val NEW_ITEM = 0L
    }
}

/**
 * The four screens on the bottom bar, in bar order.
 *
 * Today is the root. Every other tab sits on top of it in the back stack, so
 * the back gesture from Plan lands on Today and the back gesture from Today
 * leaves the app, which is what Android users expect a home tab to do.
 */
val TopLevelRoutes: List<NavKey> = listOf(TodayRoute, PlanRoute, InsightsRoute, SettingsRoute)
