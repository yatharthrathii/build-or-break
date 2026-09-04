package com.buildorbreak.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/** An ordered syllabus a timeline slot advances through. */
@Entity(
    tableName = "track",
    foreignKeys = [
        ForeignKey(
            entity = PlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["plan_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("plan_id")],
)
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "plan_id") val planId: Long,
    val name: String,
    /** The pasted text, kept verbatim so the original is always recoverable. */
    @ColumnInfo(name = "source_text") val sourceText: String?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)

@Entity(
    tableName = "track_unit",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["track_id", "ordinal"], unique = true), Index("state")],
)
data class TrackUnitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "track_id") val trackId: Long,
    val ordinal: Int,
    val title: String,
    @ColumnInfo(name = "estimate_minutes") val estimateMinutes: Int?,
    val state: String,
)

/**
 * One sitting.
 *
 * The left off note is the point of the table. Where you stopped is the biggest
 * friction in coming back to something the next evening, and one line written at
 * the end saves five minutes at the start of the next one.
 */
@Entity(
    tableName = "track_session",
    foreignKeys = [
        ForeignKey(
            entity = TrackUnitEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_unit_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("track_unit_id"), Index("occurrence_id")],
)
data class TrackSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "occurrence_id") val occurrenceId: Long,
    @ColumnInfo(name = "track_unit_id") val trackUnitId: Long,
    @ColumnInfo(name = "minutes_spent") val minutesSpent: Int,
    @ColumnInfo(name = "completed_unit") val completedUnit: Boolean,
    @ColumnInfo(name = "left_off_note") val leftOffNote: String?,
)

/**
 * What time an alarm was supposed to fire, and what time it did.
 *
 * The one table that exists to produce a number rather than to run the app.
 * Alarm reliability is the whole product claim, and the only way to know whether
 * it holds on a particular phone at six in the morning is to measure it.
 *
 * No foreign key to the occurrence. The audit has to outlive the row it
 * describes, or a pruned month would quietly improve the reliability figure.
 */
@Entity(
    tableName = "delivery_audit",
    indices = [Index("occurrence_id"), Index("scheduled_for"), Index("tier")],
)
data class DeliveryAuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "occurrence_id") val occurrenceId: Long,
    @ColumnInfo(name = "scheduled_for") val scheduledFor: Instant,
    @ColumnInfo(name = "fired_at") val firedAt: Instant?,
    val tier: String,
    @ColumnInfo(name = "device_model") val deviceModel: String,
    val manufacturer: String,
    @ColumnInfo(name = "sdk_int") val sdkInt: Int,
    @ColumnInfo(name = "was_device_idle") val wasDeviceIdle: Boolean,
    /** Denormalised so a reliability figure is one aggregate query. */
    @ColumnInfo(name = "latency_seconds") val latencySeconds: Long?,
)
