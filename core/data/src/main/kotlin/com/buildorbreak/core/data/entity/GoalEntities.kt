package com.buildorbreak.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "goal",
    foreignKeys = [
        ForeignKey(
            entity = PlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["plan_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("plan_id"), Index("is_active")],
)
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "plan_id") val planId: Long,
    val kind: String,
    val title: String,
    @ColumnInfo(name = "item_id") val itemId: Long?,
    @ColumnInfo(name = "value_kind") val valueKind: String,
    @ColumnInfo(name = "start_value") val startValue: Double,
    @ColumnInfo(name = "target_value") val targetValue: Double,
    @ColumnInfo(name = "start_date") val startDate: LocalDate,
    @ColumnInfo(name = "target_date") val targetDate: LocalDate,
    @ColumnInfo(name = "is_active") val isActive: Boolean,
)

/**
 * One row per day per goal, written by the daily close.
 *
 * Precomputed rather than derived on read. A month view would otherwise
 * recompute a month of smoothing and projection on every scroll, and the
 * arithmetic is the same every time.
 *
 * The primary key is the goal and the date together: a close that runs twice
 * replaces the row rather than adding a second one for the same day.
 */
@Entity(
    tableName = "goal_progress",
    primaryKeys = ["goal_id", "date"],
    foreignKeys = [
        ForeignKey(
            entity = GoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goal_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("date")],
)
data class GoalProgressEntity(
    @ColumnInfo(name = "goal_id") val goalId: Long,
    val date: LocalDate,
    @ColumnInfo(name = "raw_value") val rawValue: Double?,
    /** Seven day moving average. The honest number. */
    @ColumnInfo(name = "smoothed_value") val smoothedValue: Double?,
    val cumulative: Double,
    @ColumnInfo(name = "pace_target") val paceTarget: Double,
    @ColumnInfo(name = "projected_final") val projectedFinal: Double,
    /** False when the user marked the week as not counting. */
    val counted: Boolean,
)

/** How a finished day went. Written once, at the daily close. */
@Entity(tableName = "day_close", indices = [Index("plan_id"), Index("quality")])
data class DayCloseEntity(
    @PrimaryKey val date: LocalDate,
    @ColumnInfo(name = "plan_id") val planId: Long,
    @ColumnInfo(name = "items_done") val itemsDone: Int,
    @ColumnInfo(name = "items_minimum") val itemsMinimum: Int,
    @ColumnInfo(name = "items_missed") val itemsMissed: Int,
    @ColumnInfo(name = "items_total") val itemsTotal: Int,
    val quality: String,
    @ColumnInfo(name = "closed_at") val closedAt: Instant,
)

/**
 * Proof that a milestone has already fired.
 *
 * The milestone is the primary key, which is the entire anti repeat mechanism:
 * the row cannot exist twice, so there is no counter and no date arithmetic to
 * get wrong. Each fires once in the lifetime of an install.
 */
@Entity(tableName = "milestone_award", indices = [Index("awarded_on"), Index("seen_at")])
data class MilestoneAwardEntity(
    @PrimaryKey val milestone: String,
    @ColumnInfo(name = "goal_id") val goalId: Long?,
    @ColumnInfo(name = "item_id") val itemId: Long?,
    @ColumnInfo(name = "awarded_on") val awardedOn: LocalDate,
    @ColumnInfo(name = "seen_at") val seenAt: Instant?,
)
