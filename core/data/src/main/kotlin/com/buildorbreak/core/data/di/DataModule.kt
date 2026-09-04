package com.buildorbreak.core.data.di

import com.buildorbreak.core.data.repository.DayCloseRepositoryImpl
import com.buildorbreak.core.data.repository.DayLogRepositoryImpl
import com.buildorbreak.core.data.repository.DeliveryAuditRepositoryImpl
import com.buildorbreak.core.data.repository.GoalRepositoryImpl
import com.buildorbreak.core.data.repository.ItemRepositoryImpl
import com.buildorbreak.core.data.repository.MeasurementRepositoryImpl
import com.buildorbreak.core.data.repository.MilestoneRepositoryImpl
import com.buildorbreak.core.data.repository.OccurrenceRepositoryImpl
import com.buildorbreak.core.data.repository.PlanRepositoryImpl
import com.buildorbreak.core.data.repository.TemplateRepositoryImpl
import com.buildorbreak.core.data.repository.TrackRepositoryImpl
import com.buildorbreak.core.domain.repository.DayCloseRepository
import com.buildorbreak.core.domain.repository.DayLogRepository
import com.buildorbreak.core.domain.repository.DeliveryAuditRepository
import com.buildorbreak.core.domain.repository.GoalRepository
import com.buildorbreak.core.domain.repository.ItemRepository
import com.buildorbreak.core.domain.repository.MeasurementRepository
import com.buildorbreak.core.domain.repository.MilestoneRepository
import com.buildorbreak.core.domain.repository.OccurrenceRepository
import com.buildorbreak.core.domain.repository.PlanRepository
import com.buildorbreak.core.domain.repository.TemplateRepository
import com.buildorbreak.core.domain.repository.TrackRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The one place the domain's interfaces meet their implementations.
 *
 * architecture.md section 3, the single most important cell in the matrix:
 * domain to data is no. Every binding below points the same way, from an
 * interface the domain owns to a class the data layer provides, and nothing in
 * `:core:domain` ever learns that Room exists.
 *
 * `@Binds` rather than `@Provides` throughout, so a repository gaining a
 * dependency is a change in one constructor rather than in two files.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindPlanRepository(impl: PlanRepositoryImpl): PlanRepository

    @Binds
    @Singleton
    abstract fun bindTemplateRepository(impl: TemplateRepositoryImpl): TemplateRepository

    @Binds
    @Singleton
    abstract fun bindItemRepository(impl: ItemRepositoryImpl): ItemRepository

    @Binds
    @Singleton
    abstract fun bindOccurrenceRepository(impl: OccurrenceRepositoryImpl): OccurrenceRepository

    @Binds
    @Singleton
    abstract fun bindMeasurementRepository(impl: MeasurementRepositoryImpl): MeasurementRepository

    @Binds
    @Singleton
    abstract fun bindDayLogRepository(impl: DayLogRepositoryImpl): DayLogRepository

    @Binds
    @Singleton
    abstract fun bindGoalRepository(impl: GoalRepositoryImpl): GoalRepository

    @Binds
    @Singleton
    abstract fun bindDayCloseRepository(impl: DayCloseRepositoryImpl): DayCloseRepository

    @Binds
    @Singleton
    abstract fun bindMilestoneRepository(impl: MilestoneRepositoryImpl): MilestoneRepository

    @Binds
    @Singleton
    abstract fun bindTrackRepository(impl: TrackRepositoryImpl): TrackRepository

    @Binds
    @Singleton
    abstract fun bindDeliveryAuditRepository(impl: DeliveryAuditRepositoryImpl): DeliveryAuditRepository
}
