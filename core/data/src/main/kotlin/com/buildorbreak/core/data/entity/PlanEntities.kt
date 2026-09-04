package com.buildorbreak.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * The stored plan.
 *
 * Two rules run through every entity in this module:
 *
 * **Enums are text.** A column holding "ALARM" is readable in a database browser
 * and in the committed schema JSON, and an unknown value can be handled by the
 * mapper instead of crashing inside Room.
 *
 * **Anchors are flattened.** `Anchor` is a sealed type with four shapes and a
 * relational table cannot hold one. The columns below are the union of what the
 * four shapes need, with the type column saying which of them are meaningful.
 * Storing it as JSON would be shorter and would make "every RELATIVE item
 * pointing at this parent" impossible to query.
 */
@Entity(tableName = "plan")
data class PlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "is_active") val isActive: Boolean,
    val zone: ZoneId,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)

@Entity(
    tableName = "day_template",
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
data class DayTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "plan_id") val planId: Long,
    val name: String,
    /** Seven bit weekday mask. Monday is bit zero. */
    val weekdays: Int,
    @ColumnInfo(name = "is_default") val isDefault: Boolean,
    val mode: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)

@Entity(
    tableName = "block",
    foreignKeys = [
        ForeignKey(
            entity = DayTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["template_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("template_id")],
)
data class BlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "template_id") val templateId: Long,
    val title: String,
    val salience: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @Embedded val anchor: AnchorColumns,
)

/**
 * One thing to do.
 *
 * [blockId] has no foreign key on purpose. A block and its items are edited
 * together and a cascade delete would take the items with the block, which is
 * almost never what somebody reorganising their morning means. The mapper treats
 * a missing block as no block, which is exactly how a resolved entry renders it.
 */
@Entity(
    tableName = "item",
    foreignKeys = [
        ForeignKey(
            entity = DayTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["template_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("template_id"), Index("block_id"), Index("archived_at")],
)
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "template_id") val templateId: Long,
    @ColumnInfo(name = "block_id") val blockId: Long?,
    val kind: String,
    val title: String,
    val detail: String?,
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Long?,
    val salience: String,
    val weekdays: Int,
    val pinned: Boolean,
    @ColumnInfo(name = "minimum_title") val minimumTitle: String?,
    @ColumnInfo(name = "minimum_duration_minutes") val minimumDurationMinutes: Long?,
    @ColumnInfo(name = "value_kind") val valueKind: String,
    @ColumnInfo(name = "bundle_uri") val bundleUri: String?,
    @ColumnInfo(name = "track_id") val trackId: Long?,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    /** Archived rather than deleted, so past occurrences keep their meaning. */
    @ColumnInfo(name = "archived_at") val archivedAt: Instant?,
    @Embedded val anchor: AnchorColumns,
)

/**
 * The four anchor shapes, flattened into one set of columns.
 *
 * [type] says which of the rest apply. Everything else is null for the shapes
 * that do not use it, which is what keeps a `RELATIVE` item queryable by its
 * parent and an `INTERVAL` item queryable by its window.
 */
data class AnchorColumns(
    @ColumnInfo(name = "anchor_type") val type: String,
    /** FIXED. */
    @ColumnInfo(name = "anchor_at") val at: LocalTime? = null,
    /** RELATIVE. */
    @ColumnInfo(name = "anchor_parent_item_id") val parentItemId: Long? = null,
    @ColumnInfo(name = "anchor_offset_minutes") val offsetMinutes: Long? = null,
    /** WINDOW and INTERVAL. */
    @ColumnInfo(name = "anchor_from") val from: LocalTime? = null,
    @ColumnInfo(name = "anchor_to") val to: LocalTime? = null,
    /** INTERVAL. */
    @ColumnInfo(name = "anchor_every_minutes") val everyMinutes: Long? = null,
    /** WINDOW. Comma separated minutes, empty when there is no ladder. */
    @ColumnInfo(name = "anchor_nag_ladder") val nagLadder: String? = null,
)
