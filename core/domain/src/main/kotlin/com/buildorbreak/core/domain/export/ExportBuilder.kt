package com.buildorbreak.core.domain.export

import com.buildorbreak.core.model.execution.DayLog
import com.buildorbreak.core.model.execution.Measurement
import com.buildorbreak.core.model.execution.Occurrence
import com.buildorbreak.core.model.goal.DayClose
import com.buildorbreak.core.model.goal.Goal
import com.buildorbreak.core.model.plan.Anchor
import com.buildorbreak.core.model.plan.Block
import com.buildorbreak.core.model.plan.DayTemplate
import com.buildorbreak.core.model.plan.Item
import com.buildorbreak.core.model.plan.MinimumVersion
import com.buildorbreak.core.model.plan.Plan
import java.time.Instant
import kotlin.time.Duration
import kotlinx.serialization.json.Json

/**
 * Everything a full export can contain.
 *
 * [DayLog] is deliberately absent: which template ran on a given day is derivable
 * from the occurrences, and a restore that rebuilt it wrong would be worse than
 * one that rebuilds it from what actually happened.
 */
data class ExportInput(
    val plan: Plan,
    val templates: List<DayTemplate> = emptyList(),
    val blocks: List<Block> = emptyList(),
    val items: List<Item> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val occurrences: List<Occurrence> = emptyList(),
    val measurements: List<Measurement> = emptyList(),
    val closes: List<DayClose> = emptyList(),
)

/**
 * Turns the stored plan into a file the user owns.
 *
 * The app keeps everything on the phone and asks for no account, which is only
 * a promise worth making if the data can leave. This is that exit: a plain,
 * readable, complete file that can be put back.
 *
 * Two properties are load bearing:
 *
 * **It round trips.** Every field needed to rebuild the plan is present, so an
 * export taken today can restore a routine on a new phone in a year.
 *
 * **It is deterministic.** The same input always produces byte identical output,
 * with collections in a stable order. That is what lets a user diff two exports,
 * and what lets a test assert on the whole file rather than on a few fields.
 */
class ExportBuilder(
    private val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
    },
) {

    /** [includeHistory] off exports the routine alone, for sharing rather than backup. */
    fun build(input: ExportInput, exportedAt: Instant, includeHistory: Boolean = true): ExportDocument {
        val blocksByTemplate = input.blocks.groupBy { it.templateId }
        val itemsByTemplate = input.items.groupBy { it.templateId }

        return ExportDocument(
            exportedAt = exportedAt.toString(),
            plan = input.plan.toExport(),
            templates = input.templates.sortedBy { it.id }.map { template ->
                template.toExport(
                    blocks = blocksByTemplate[template.id].orEmpty(),
                    items = itemsByTemplate[template.id].orEmpty(),
                )
            },
            goals = input.goals.sortedBy { it.id }.map { it.toExport() },
            history = if (includeHistory) input.toHistory() else ExportHistory(),
        )
    }

    fun toJson(document: ExportDocument): String = json.encodeToString(ExportDocument.serializer(), document)

    /** Convenience for the common case: build and encode in one step. */
    fun buildJson(input: ExportInput, exportedAt: Instant, includeHistory: Boolean = true): String =
        toJson(build(input, exportedAt, includeHistory))
}

// Mapping ---------------------------------------------------------------------
//
// Written out rather than generated. Every line here is a decision about what
// somebody gets back when they restore, and a mapping that is easy to read is a
// mapping whose omissions are easy to spot.

private fun Plan.toExport() = ExportPlan(
    id = id,
    name = name,
    zone = zone.id,
    createdAt = createdAt.toString(),
)

private fun DayTemplate.toExport(blocks: List<Block>, items: List<Item>) = ExportTemplate(
    id = id,
    name = name,
    weekdays = weekdays.bits,
    isDefault = isDefault,
    mode = mode.name,
    sortOrder = sortOrder,
    blocks = blocks.sortedBy { it.id }.map { it.toExport() },
    items = items.sortedBy { it.id }.map { it.toExport() },
)

private fun Block.toExport() = ExportBlock(
    id = id,
    title = title,
    anchor = anchor.toExport(),
    salience = salience.name,
    sortOrder = sortOrder,
)

private fun Item.toExport() = ExportItem(
    id = id,
    title = title,
    detail = detail,
    kind = kind.name,
    blockId = blockId,
    anchor = anchor.toExport(),
    durationMinutes = duration?.wholeMinutes(),
    salience = salience.name,
    weekdays = weekdays.bits,
    pinned = pinned,
    minimum = minimum?.toExport(),
    valueKind = valueKind.name,
    sortOrder = sortOrder,
    archivedAt = archivedAt?.toString(),
)

private fun MinimumVersion.toExport() = ExportMinimum(title = title, durationMinutes = duration?.wholeMinutes())

private fun Anchor.toExport(): ExportAnchor = when (this) {
    is Anchor.Fixed -> ExportAnchor(type = type.name, at = at.toString())

    is Anchor.Relative -> ExportAnchor(
        type = type.name,
        parentItemId = parentItemId,
        offsetMinutes = offset.wholeMinutes(),
    )

    is Anchor.Window -> ExportAnchor(
        type = type.name,
        from = from.toString(),
        to = to.toString(),
        nagLadderMinutes = nagLadder.map { it.wholeMinutes() },
    )

    is Anchor.Interval -> ExportAnchor(
        type = type.name,
        from = from.toString(),
        to = to.toString(),
        everyMinutes = every.wholeMinutes(),
    )
}

private fun Goal.toExport() = ExportGoal(
    id = id,
    kind = kind.name,
    title = title,
    itemId = itemId,
    valueKind = valueKind.name,
    startValue = startValue,
    targetValue = targetValue,
    startDate = startDate.toString(),
    targetDate = targetDate.toString(),
    isActive = isActive,
)

private fun ExportInput.toHistory() = ExportHistory(
    occurrences = occurrences
        .sortedWith(compareBy({ it.date }, { it.itemId }, { it.sequenceInDay }))
        .map { it.toExport() },
    measurements = measurements.sortedWith(compareBy({ it.date }, { it.itemId })).map { it.toExport() },
    dayCloses = closes.sortedBy { it.date }.map { it.toExport() },
)

private fun Occurrence.toExport() = ExportOccurrence(
    itemId = itemId,
    date = date.toString(),
    plannedAt = plannedAt.toString(),
    settledAt = settledAt?.toString(),
    state = state.name,
    shiftMinutes = shiftMinutes,
    sequenceInDay = sequenceInDay,
)

private fun Measurement.toExport() = ExportMeasurement(
    itemId = itemId,
    date = date.toString(),
    value = value,
    kind = kind.name,
    note = note,
)

private fun DayClose.toExport() = ExportDayClose(
    date = date.toString(),
    itemsDone = itemsDone,
    itemsMinimum = itemsMinimum,
    itemsMissed = itemsMissed,
    itemsTotal = itemsTotal,
    quality = quality.name,
)

private fun Duration.wholeMinutes(): Long = inWholeMinutes
