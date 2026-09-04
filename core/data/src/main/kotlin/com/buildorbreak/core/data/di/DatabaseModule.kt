package com.buildorbreak.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.buildorbreak.core.data.database.BuildOrBreakDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private const val PREFERENCES_NAME = "buildorbreak"

/** The database and the preference store. One instance of each, for the app. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * No destructive fallback, ever.
     *
     * The usual `fallbackToDestructiveMigration` turns a missing migration into
     * a wiped database. On an app that holds months of somebody's routine and
     * has no server copy, that is the single worst outcome available, and a
     * crash on launch is genuinely preferable: a crash can be fixed by shipping
     * the migration, a wipe cannot be undone.
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BuildOrBreakDatabase =
        Room.databaseBuilder(context, BuildOrBreakDatabase::class.java, BuildOrBreakDatabase.NAME).build()

    @Provides
    @Singleton
    fun providePreferences(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(PREFERENCES_NAME) }
}
