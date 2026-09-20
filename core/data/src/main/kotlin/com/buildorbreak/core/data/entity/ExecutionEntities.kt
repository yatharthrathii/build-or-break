package com.buildorbreak.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * One item on one day. What actually happened.
 *
 * The unique index across item, date and sequence is the whole reason
 * `materialise` can be called twice without producing a second set of rows. The
 * reschedule pass runs on app open, on boot, on timezone change and on every
 * completion, sometimes twice in a second, so idempotence has to be enforced by
 * the schema rather than remembered by the caller.
 */
@Entity(
    tableName = "occurrence",
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["item_id", "date", "sequence_in_day"], unique = true),
        Index("date"),
        Index("scheduled_at"),
        Index("state"),
    ],
)
data class OccurrenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "item_id") val itemId: Long,
    val date: LocalDate,
    /** What the resolver said at scheduling time. */
    @ColumnInfo(name = "planned_at") val plannedAt: LocalDateTime,
    /** What was handed to AlarmManager. Null for TIMELINE items. */
    @ColumnInfo(name = "scheduled_at") val scheduledAt: Instant?,
    @ColumnInfo(name = "fired_at") val firedAt: Instant?,
    @ColumnInfo(name = "settled_at") val settledAt: Instant?,
    val state: String,
    @ColumnInfo(name = "shift_minutes") val shiftMinutes: Int,
    @ColumnInfo(name = "snooze_count") val snoozeCount: Int,
    @ColumnInfo(name = "sequence_in_day") val sequenceInDay: Int,
)

/** Why something did not happen. Always optional, always after the fact. */
@Entity(
    tableName = "skip_reason",
    foreignKeys = [
        ForeignKey(
            entity = OccurrenceEntity::class,
            parentColumns = ["id"],
            childColumns = ["occurrence_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["occurrence_id"], unique = true)],
)
data class SkipReasonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "occurrence_id") val occurrenceId: Long,
    val chip: String?,
    val text: String?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)

/**
 * A number the user logged.
 *
 * No foreign key to the occurrence. A weight can be recorded without the day
 * having been scheduled at all, and a measurement that outlives its occurrence is
 * still true.
 */
@Entity(
    tableName = "measurement",
    indices = [Index("item_id"), Index("date"), Index("kind")],
)
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "item_id") val itemId: Long,
    @ColumnInfo(name = "occurrence_id") val occurrenceId: Long?,
    val date: LocalDate,
    val value: Double,
    val kind: String,
    val note: String?,
)

/** Which template ran on a date, and how far the whole day was moved. */
@Entity(tableName = "day_log", indices = [Index("plan_id")])
data class DayLogEntity(
    @PrimaryKey val date: LocalDate,
    @ColumnInfo(name = "plan_id") val planId: Long,
    @ColumnInfo(name = "template_id") val templateId: Long,
    @ColumnInfo(name = "day_shift_minutes") val dayShiftMinutes: Int,
    val mode: String,
    @ColumnInfo(name = "chosen_at") val chosenAt: Instant,
)
