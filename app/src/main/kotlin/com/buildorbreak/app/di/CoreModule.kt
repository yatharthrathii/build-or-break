package com.buildorbreak.app.di

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.coroutines.DefaultAppDispatchers
import com.buildorbreak.core.common.time.SystemTimeProvider
import com.buildorbreak.core.common.time.TimeProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the pure Kotlin core abstractions to their production implementations.
 *
 * The Hilt wiring lives here, in an Android module, so that `:core:common`
 * stays free of any injection framework and remains a pure JVM module.
 */
@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    /**
     * rules.md section 4: this is the only place a real clock is constructed in
     * the entire application. Everything else receives [TimeProvider].
     */
    @Provides
    @Singleton
    fun provideTimeProvider(): TimeProvider = SystemTimeProvider()

    @Provides
    @Singleton
    fun provideAppDispatchers(): AppDispatchers = DefaultAppDispatchers
}
