package com.buildorbreak.core.data.di

import com.buildorbreak.core.data.dao.DayCloseDao
import com.buildorbreak.core.data.dao.DayLogDao
import com.buildorbreak.core.data.dao.DeliveryAuditDao
import com.buildorbreak.core.data.dao.GoalDao
import com.buildorbreak.core.data.dao.ItemDao
import com.buildorbreak.core.data.dao.MeasurementDao
import com.buildorbreak.core.data.dao.MilestoneDao
import com.buildorbreak.core.data.dao.OccurrenceDao
import com.buildorbreak.core.data.dao.PlanDao
import com.buildorbreak.core.data.dao.TemplateDao
import com.buildorbreak.core.data.dao.TrackDao
import com.buildorbreak.core.data.database.BuildOrBreakDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Every DAO, one by one.
 *
 * Handing repositories the whole database would be four lines shorter and would
 * let any of them reach any table. The point of splitting the data layer up is
 * that each repository can only touch what it owns, and that is only true if the
 * wiring says so.
 *
 * The function count rule is suppressed rather than worked around. It exists to
 * catch a class doing too many things, and this one does a single thing eleven
 * times. Splitting the list across two modules to satisfy a counter would leave
 * the same eleven lines somewhere less obvious.
 */
@Suppress("TooManyFunctions")
@Module
@InstallIn(SingletonComponent::class)
object DaoModule {

    @Provides
    fun providePlanDao(database: BuildOrBreakDatabase): PlanDao = database.planDao()

    @Provides
    fun provideTemplateDao(database: BuildOrBreakDatabase): TemplateDao = database.templateDao()

    @Provides
    fun provideItemDao(database: BuildOrBreakDatabase): ItemDao = database.itemDao()

    @Provides
    fun provideOccurrenceDao(database: BuildOrBreakDatabase): OccurrenceDao = database.occurrenceDao()

    @Provides
    fun provideMeasurementDao(database: BuildOrBreakDatabase): MeasurementDao = database.measurementDao()

    @Provides
    fun provideDayLogDao(database: BuildOrBreakDatabase): DayLogDao = database.dayLogDao()

    @Provides
    fun provideGoalDao(database: BuildOrBreakDatabase): GoalDao = database.goalDao()

    @Provides
    fun provideDayCloseDao(database: BuildOrBreakDatabase): DayCloseDao = database.dayCloseDao()

    @Provides
    fun provideMilestoneDao(database: BuildOrBreakDatabase): MilestoneDao = database.milestoneDao()

    @Provides
    fun provideTrackDao(database: BuildOrBreakDatabase): TrackDao = database.trackDao()

    @Provides
    fun provideDeliveryAuditDao(database: BuildOrBreakDatabase): DeliveryAuditDao = database.deliveryAuditDao()
}
