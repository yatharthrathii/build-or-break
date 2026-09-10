package com.buildorbreak.core.data.repository

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.data.database.BuildOrBreakDatabase
import com.buildorbreak.core.data.datastore.PreferencesDataSource
import com.buildorbreak.core.domain.error.DomainError.DataError
import com.buildorbreak.core.domain.repository.ResetRepository
import com.buildorbreak.core.domain.repository.SettingsRepository
import com.buildorbreak.core.domain.resolver.ResolveInput
import com.buildorbreak.core.model.enums.ThemeMode
import java.time.LocalDate
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The domain's view of the preference store.
 *
 * A thin adapter on purpose. `PreferencesDataSource` already knows the keys and
 * the defaults; this only exists so the domain interface has an implementation
 * and nothing above the data layer imports DataStore.
 */
class SettingsRepositoryImpl @Inject constructor(
    private val preferences: PreferencesDataSource,
) : SettingsRepository {

    override val onboardingComplete: Flow<Boolean> = preferences.onboardingComplete

    override val themeMode: Flow<ThemeMode> = preferences.themeMode

    override val dismissedReviewWeek: Flow<LocalDate?> = preferences.dismissedReviewWeek

    override val lateTolerance: Flow<Duration> = preferences.lateToleranceMinutes.map { minutes ->
        minutes?.minutes ?: ResolveInput.DEFAULT_LATE_TOLERANCE
    }

    override val autostartDone: Flow<Boolean> = preferences.autostartDone

    override val lockScreenDone: Flow<Boolean> = preferences.lockScreenDone

    override suspend fun setOnboardingComplete(complete: Boolean) = preferences.setOnboardingComplete(complete)

    override suspend fun setThemeMode(mode: ThemeMode) = preferences.setThemeMode(mode)

    override suspend fun setDismissedReviewWeek(week: LocalDate) = preferences.setDismissedReviewWeek(week)

    override suspend fun setLateTolerance(tolerance: Duration) =
        preferences.setLateToleranceMinutes(tolerance.inWholeMinutes.toInt())

    override suspend fun setAutostartDone(done: Boolean) = preferences.setAutostartDone(done)

    override suspend fun setLockScreenDone(done: Boolean) = preferences.setLockScreenDone(done)
}

/**
 * Empties every table and every preference.
 *
 * `clearAllTables` is synchronous and Room refuses it on the main thread, so
 * the whole thing runs on the io dispatcher. Preferences go too: a wipe that
 * left the first run flag set would reopen the app on an empty Today screen
 * with no way back to the three screens that explain it.
 */
class ResetRepositoryImpl @Inject constructor(
    private val database: BuildOrBreakDatabase,
    private val preferences: PreferencesDataSource,
    private val dispatchers: AppDispatchers,
) : ResetRepository {

    override suspend fun wipeEverything(): Outcome<Unit, DataError> = withContext(dispatchers.io) {
        val cleared = sqlOutcome(dispatchers.io) { database.clearAllTables() }

        preferences.clear()

        cleared
    }
}
