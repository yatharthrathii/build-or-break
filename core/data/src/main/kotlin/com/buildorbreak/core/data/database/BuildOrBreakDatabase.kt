package com.buildorbreak.core.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
import com.buildorbreak.core.data.entity.BlockEntity
import com.buildorbreak.core.data.entity.DayCloseEntity
import com.buildorbreak.core.data.entity.DayLogEntity
import com.buildorbreak.core.data.entity.DayTemplateEntity
import com.buildorbreak.core.data.entity.DeliveryAuditEntity
import com.buildorbreak.core.data.entity.GoalEntity
import com.buildorbreak.core.data.entity.GoalProgressEntity
import com.buildorbreak.core.data.entity.ItemEntity
import com.buildorbreak.core.data.entity.MeasurementEntity
import com.buildorbreak.core.data.entity.MilestoneAwardEntity
import com.buildorbreak.core.data.entity.OccurrenceEntity
import com.buildorbreak.core.data.entity.PlanEntity
import com.buildorbreak.core.data.entity.SkipReasonEntity
import com.buildorbreak.core.data.entity.TrackEntity
import com.buildorbreak.core.data.entity.TrackSessionEntity
import com.buildorbreak.core.data.entity.TrackUnitEntity

/**
 * The only database.
 *
 * Everything the app knows lives here, on the phone, with no account and no
 * server behind it. That is a product promise rather than an implementation
 * detail, and it is the reason the export in `:core:domain` matters: this file
 * is the single point of failure for somebody's routine, so it has to be
 * possible to take a copy of it out.
 *
 * **The schema is exported and committed.** `AndroidRoomConventionPlugin` writes
 * every version into `schemas/`, so a migration can be tested against the real
 * shape of the previous release rather than against a reconstruction of it. A
 * migration that was never run against the schema it claims to migrate from is a
 * migration that has not been tested.
 *
 * There are no migrations yet because there has been no release. The first one
 * arrives with the first schema change after shipping, and `fallbackToDestructive`
 * is deliberately never called: losing somebody's history to a version bump is
 * the worst thing this app could do.
 */
@Database(
    entities = [
        PlanEntity::class,
        DayTemplateEntity::class,
        BlockEntity::class,
        ItemEntity::class,
        OccurrenceEntity::class,
        SkipReasonEntity::class,
        MeasurementEntity::class,
        DayLogEntity::class,
        GoalEntity::class,
        GoalProgressEntity::class,
        DayCloseEntity::class,
        MilestoneAwardEntity::class,
        TrackEntity::class,
        TrackUnitEntity::class,
        TrackSessionEntity::class,
        DeliveryAuditEntity::class,
    ],
    version = BuildOrBreakDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class BuildOrBreakDatabase : RoomDatabase() {

    abstract fun planDao(): PlanDao
    abstract fun templateDao(): TemplateDao
    abstract fun itemDao(): ItemDao
    abstract fun occurrenceDao(): OccurrenceDao
    abstract fun measurementDao(): MeasurementDao
    abstract fun dayLogDao(): DayLogDao
    abstract fun goalDao(): GoalDao
    abstract fun dayCloseDao(): DayCloseDao
    abstract fun milestoneDao(): MilestoneDao
    abstract fun trackDao(): TrackDao
    abstract fun deliveryAuditDao(): DeliveryAuditDao

    companion object {
        const val VERSION = 1

        const val NAME = "buildorbreak.db"
    }
}
