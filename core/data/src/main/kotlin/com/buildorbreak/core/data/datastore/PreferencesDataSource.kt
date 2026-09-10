package com.buildorbreak.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.buildorbreak.core.model.enums.ThemeMode
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The handful of settings that are not rows.
 *
 * Deliberately small. Almost everything the app knows belongs in the database,
 * where it can be queried, related and exported, and a preference is only the
 * right home for something with exactly one value and no history. Whether
 * onboarding has been seen is one of those; a plan is not.
 *
 * Nothing here is secret, which is why there is no encrypted store beside it.
 * The one thing that will need one is the billing key in M8, and building an
 * empty vault for it now would be guessing at what it has to hold.
 */
@Singleton
class PreferencesDataSource @Inject constructor(
    private val store: DataStore<Preferences>,
) {

    val onboardingComplete: Flow<Boolean> = store.data.map { it[ONBOARDING_COMPLETE] ?: false }

    /**
     * When the reschedule pass last ran to completion.
     *
     * Not derivable from the database. Occurrences record what was scheduled, not
     * when the pass that scheduled them finished, and the difference matters
     * after a crash: a pass that started and died left rows that look scheduled
     * and alarms that are not.
     */
    val lastRescheduleAt: Flow<Instant?> = store.data.map { prefs ->
        prefs[LAST_RESCHEDULE_AT]?.let(Instant::ofEpochMilli)
    }

    /** Whether the delivery tier explanation has been shown once. */
    val reliabilityExplained: Flow<Boolean> = store.data.map { it[RELIABILITY_EXPLAINED] ?: false }

    /** Stored by name, and an unknown name follows the system rather than crashing. */
    val themeMode: Flow<ThemeMode> = store.data.map { prefs ->
        prefs[THEME_MODE]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.SYSTEM
    }

    /** The Monday of a week whose suggestion was declined. Epoch day. */
    val dismissedReviewWeek: Flow<LocalDate?> = store.data.map { prefs ->
        prefs[DISMISSED_REVIEW_WEEK]?.let(LocalDate::ofEpochDay)
    }

    /** Minutes. Absent means the resolver's own default. */
    val lateToleranceMinutes: Flow<Int?> = store.data.map { it[LATE_TOLERANCE_MINUTES] }

    /**
     * Whether the user says they have dealt with the phone's own autostart list.
     *
     * Kept because there is nothing to read. No API exposes that list, so the
     * only source of truth for it is the person who went and looked, and a row
     * that can never turn green is a row that nags forever.
     */
    val autostartDone: Flow<Boolean> = store.data.map { it[AUTOSTART_DONE] ?: false }

    /** The same, for the vendor switch that lets an alarm show on the lock screen. */
    val lockScreenDone: Flow<Boolean> = store.data.map { it[LOCK_SCREEN_DONE] ?: false }

    suspend fun setOnboardingComplete(complete: Boolean) {
        store.edit { it[ONBOARDING_COMPLETE] = complete }
    }

    suspend fun setLastRescheduleAt(instant: Instant) {
        store.edit { it[LAST_RESCHEDULE_AT] = instant.toEpochMilli() }
    }

    suspend fun setReliabilityExplained(explained: Boolean) {
        store.edit { it[RELIABILITY_EXPLAINED] = explained }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setDismissedReviewWeek(week: LocalDate) {
        store.edit { it[DISMISSED_REVIEW_WEEK] = week.toEpochDay() }
    }

    suspend fun setAutostartDone(done: Boolean) {
        store.edit { it[AUTOSTART_DONE] = done }
    }

    suspend fun setLockScreenDone(done: Boolean) {
        store.edit { it[LOCK_SCREEN_DONE] = done }
    }

    suspend fun setLateToleranceMinutes(minutes: Int) {
        store.edit { it[LATE_TOLERANCE_MINUTES] = minutes }
    }

    /** Everything, including whether the first run was seen. Part of a full wipe. */
    suspend fun clear() {
        store.edit { it.clear() }
    }

    private companion object {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val LAST_RESCHEDULE_AT = longPreferencesKey("last_reschedule_at")
        val RELIABILITY_EXPLAINED = booleanPreferencesKey("reliability_explained")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DISMISSED_REVIEW_WEEK = longPreferencesKey("dismissed_review_week")
        val LATE_TOLERANCE_MINUTES = intPreferencesKey("late_tolerance_minutes")
        val AUTOSTART_DONE = booleanPreferencesKey("autostart_done")
        val LOCK_SCREEN_DONE = booleanPreferencesKey("lock_screen_done")
    }
}
