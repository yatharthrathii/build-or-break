package com.buildorbreak.app.di

import com.buildorbreak.core.domain.export.ExportBuilder
import com.buildorbreak.core.domain.parse.PlanTextParser
import com.buildorbreak.core.domain.resolver.CascadeCalculator
import com.buildorbreak.core.domain.resolver.DefaultCascadeCalculator
import com.buildorbreak.core.domain.resolver.DefaultTimelineResolver
import com.buildorbreak.core.domain.resolver.TimelineResolver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The pure domain services that lay a day out, constructed once.
 *
 * They are provided here rather than annotated in `:core:domain` because none
 * of them takes an injected dependency: they are plain objects with sensible
 * defaults, and constructing one is a single call. Putting the wiring in an
 * Android module keeps the domain readable as ordinary Kotlin that can be
 * instantiated in a test with `DefaultTimelineResolver()` and nothing else.
 *
 * The services that judge a finished day live in `ReviewModule` next door.
 *
 * Every one is a singleton because every one is stateless. Two timeline
 * resolvers would be two identical stateless objects, and holding one costs
 * nothing.
 */
@Module
@InstallIn(SingletonComponent::class)
object DomainModule {

    @Provides
    @Singleton
    fun provideTimelineResolver(): TimelineResolver = DefaultTimelineResolver()

    /**
     * Given the resolver rather than building its own, so the preview and the
     * day it previews can never be produced by two different configurations of
     * the same engine.
     */
    @Provides
    @Singleton
    fun provideCascadeCalculator(resolver: TimelineResolver): CascadeCalculator = DefaultCascadeCalculator(resolver)

    @Provides
    @Singleton
    fun providePlanTextParser(): PlanTextParser = PlanTextParser()

    @Provides
    @Singleton
    fun provideExportBuilder(): ExportBuilder = ExportBuilder()
}
