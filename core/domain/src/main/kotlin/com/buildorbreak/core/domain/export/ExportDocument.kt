package com.buildorbreak.core.domain.export

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The export format, versioned and deliberately separate from the internal
 * model.
 *
 * These types exist so that renaming a field on `Item` cannot silently break
 * every backup a user has ever taken. The internal model is free to change; this
 * is a contract with files that already exist on somebody's phone, and it only
 * changes on purpose, with [CURRENT_SCHEMA_VERSION] going up.
 *
 * **Why JSON and not a PDF.** The point of an export is that the data survives
 * the app. A PDF is a photograph of a routine: it cannot be read back, so a user
 * who changes phone has a document and no routine. JSON round trips, which is
 * what "everything stays on your phone" has to mean if it is going to mean
 * anything. A printable summary is a different feature for a different reason,
 * and it belongs in the app module where Android can render one.
 *
 * Times are ISO-8601 strings and durations are whole minutes, so a file can be
 * read by a person and by any other tool without a schema in hand.
 */
const val CURRENT_SCHEMA_VERSION = 1

@Serializable
data class ExportDocument(
    @SerialName("schema_version") val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    /** ISO-8601 instant. */
    @SerialName("exported_at") val exportedAt: String,
    val plan: ExportPlan,
    val templates: List<ExportTemplate> = emptyList(),
    val goals: List<ExportGoal> = emptyList(),
    val history: ExportHistory = ExportHistory(),
)

@Serializable
data class ExportPlan(
    val id: Long,
    val name: String,
    /** IANA zone id, so a restored plan keeps the clock it was written against. */
    val zone: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class ExportTemplate(
    val id: Long,
    val name: String,
    /** Seven bit weekday mask, Monday is bit zero. */
    val weekdays: Int,
    @SerialName("is_default") val isDefault: Boolean,
    val mode: String,
    @SerialName("sort_order") val sortOrder: Int,
    val blocks: List<ExportBlock> = emptyList(),
    val items: List<ExportItem> = emptyList(),
)

@Serializable
data class ExportBlock(
    val id: Long,
    val title: String,
    val anchor: ExportAnchor,
    val salience: String,
    @SerialName("sort_order") val sortOrder: Int,
)

@Serializable
data class ExportItem(
    val id: Long,
    val title: String,
    val detail: String? = null,
    val kind: String,
    @SerialName("block_id") val blockId: Long? = null,
    val anchor: ExportAnchor,
    @SerialName("duration_minutes") val durationMinutes: Long? = null,
    val salience: String,
    val weekdays: Int,
    val pinned: Boolean,
    val minimum: ExportMinimum? = null,
    @SerialName("value_kind") val valueKind: String,
    @SerialName("sort_order") val sortOrder: Int,
    @SerialName("archived_at") val archivedAt: String? = null,
)

@Serializable
data class ExportMinimum(
    val title: String,
    @SerialName("duration_minutes") val durationMinutes: Long? = null,
)

/**
 * One anchor, flattened.
 *
 * A tagged union rather than four shapes, because a hand edited file with a
 * missing field should fail to read loudly rather than resolve into a plan that
 * is subtly not the one that was exported.
 */
@Serializable
data class ExportAnchor(
    val type: String,
    /** FIXED. */
    val at: String? = null,
    /** RELATIVE. */
    @SerialName("parent_item_id") val parentItemId: Long? = null,
    @SerialName("offset_minutes") val offsetMinutes: Long? = null,
    /** WINDOW and INTERVAL. */
    val from: String? = null,
    val to: String? = null,
    /** INTERVAL. */
    @SerialName("every_minutes") val everyMinutes: Long? = null,
    /** WINDOW. */
    @SerialName("nag_ladder_minutes") val nagLadderMinutes: List<Long> = emptyList(),
)

@Serializable
data class ExportGoal(
    val id: Long,
    val kind: String,
    val title: String,
    @SerialName("item_id") val itemId: Long? = null,
    @SerialName("value_kind") val valueKind: String,
    @SerialName("start_value") val startValue: Double,
    @SerialName("target_value") val targetValue: Double,
    @SerialName("start_date") val startDate: String,
    @SerialName("target_date") val targetDate: String,
    @SerialName("is_active") val isActive: Boolean,
)

/**
 * What happened, as opposed to what was planned.
 *
 * Kept in its own object so a caller can offer an export of the plan alone. A
 * routine somebody wants to share is not a routine they want to hand over three
 * months of their own adherence with.
 */
@Serializable
data class ExportHistory(
    val occurrences: List<ExportOccurrence> = emptyList(),
    val measurements: List<ExportMeasurement> = emptyList(),
    @SerialName("day_closes") val dayCloses: List<ExportDayClose> = emptyList(),
)

@Serializable
data class ExportOccurrence(
    @SerialName("item_id") val itemId: Long,
    val date: String,
    @SerialName("planned_at") val plannedAt: String,
    @SerialName("settled_at") val settledAt: String? = null,
    val state: String,
    @SerialName("shift_minutes") val shiftMinutes: Int = 0,
    @SerialName("sequence_in_day") val sequenceInDay: Int = 0,
)

@Serializable
data class ExportMeasurement(
    @SerialName("item_id") val itemId: Long,
    val date: String,
    val value: Double,
    val kind: String,
    val note: String? = null,
)

@Serializable
data class ExportDayClose(
    val date: String,
    @SerialName("items_done") val itemsDone: Int,
    @SerialName("items_minimum") val itemsMinimum: Int,
    @SerialName("items_missed") val itemsMissed: Int,
    @SerialName("items_total") val itemsTotal: Int,
    val quality: String,
)
