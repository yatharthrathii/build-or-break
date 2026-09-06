package com.buildorbreak.core.domain.repository

import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.domain.error.DomainError.DataError
import com.buildorbreak.core.model.enums.ThemeMode
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * The handful of settings that are not rows.
 *
 * Deliberately small. Almost everything the app knows belongs in the database,
 * and a preference is only the right home for something with exactly one value
 * and no history. Whether the first run has been seen is one of those; a plan
 * is not.
 *
 * An interface the domain owns, so a ViewModel can read a preference without
 * knowing DataStore exists. architecture.md hard rule two.
 */
interface SettingsRepository {

    /** Whether the three first run screens have been seen through to the end. */
    val onboardingComplete: Flow<Boolean>

    val themeMode: Flow<ThemeMode>

    /**
     * The Monday of a week whose suggestion the user chose not to act on.
     *
     * Remembered so the same suggestion is not offered again until next week.
     * A review that asks the same question every time it is opened is a review
     * that stops being opened.
     */
    val dismissedReviewWeek: Flow<LocalDate?>

    suspend fun setOnboardingComplete(complete: Boolean)

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setDismissedReviewWeek(week: LocalDate)
}

/**
 * The one destructive operation the app offers.
 *
 * Everything: plans, history, alarms, settings. There is no server copy, and
 * the settings screen says so next to the button. Behind an interface so the
 * use case that calls it can also cancel the alarms, which live outside the
 * database and would otherwise keep ringing for a plan that no longer exists.
 */
interface ResetRepository {
    suspend fun wipeEverything(): Outcome<Unit, DataError>
}
