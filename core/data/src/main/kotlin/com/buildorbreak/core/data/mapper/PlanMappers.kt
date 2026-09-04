package com.buildorbreak.core.data.mapper

import com.buildorbreak.core.data.entity.AnchorColumns
import com.buildorbreak.core.data.entity.BlockEntity
import com.buildorbreak.core.data.entity.DayTemplateEntity
import com.buildorbreak.core.data.entity.ItemEntity
import com.buildorbreak.core.data.entity.PlanEntity
import com.buildorbreak.core.model.enums.AnchorType
import com.buildorbreak.core.model.enums.DayMode
import com.buildorbreak.core.model.enums.ItemKind
import com.buildorbreak.core.model.enums.Salience
import com.buildorbreak.core.model.enums.ValueKind
import com.buildorbreak.core.model.plan.Anchor
import com.buildorbreak.core.model.plan.Block
import com.buildorbreak.core.model.plan.DayTemplate
import com.buildorbreak.core.model.plan.Item
import com.buildorbreak.core.model.plan.MinimumVersion
import com.buildorbreak.core.model.plan.Plan
import com.buildorbreak.core.model.plan.Weekdays
import java.time.LocalTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** Seven bits. A wider value in the column is a corrupt row, not a wider week. */
private const val WEEKDAY_MASK = 0b111_1111

private const val NAG_SEPARATOR = ","

/**
 * Entity to model and back.
 *
 * This is the only place the two shapes meet, and it is written out by hand
 * rather than generated. Every line is a decision about what happens to a row
 * that is not quite what the current build expects, and a generated mapper makes
 * those decisions invisibly.
 *
 * **Nothing here throws.** A corrupt row produces a visibly odd item that the
 * user can see and fix. It never produces a crash on launch, which on a routine
 * app means every alarm for the day is gone as well.
 */
internal fun PlanEntity.toModel(): Plan = Plan(
    id = id,
    name = name,
    isActive = isActive,
    zone = zone,
    createdAt = createdAt,
)

internal fun Plan.toEntity(): PlanEntity = PlanEntity(
    id = id,
    name = name,
    isActive = isActive,
    zone = zone,
    createdAt = createdAt,
)

internal fun DayTemplateEntity.toModel(): DayTemplate = DayTemplate(
    id = id,
    planId = planId,
    name = name,
    weekdays = weekdays.toWeekdays(),
    isDefault = isDefault,
    mode = mode.toEnum(DayMode.NORMAL),
    sortOrder = sortOrder,
)

internal fun DayTemplate.toEntity(): DayTemplateEntity = DayTemplateEntity(
    id = id,
    planId = planId,
    name = name,
    weekdays = weekdays.bits,
    isDefault = isDefault,
    mode = mode.name,
    sortOrder = sortOrder,
)

internal fun BlockEntity.toModel(): Block = Block(
    id = id,
    templateId = templateId,
    title = title,
    anchor = anchor.toModel(),
    // A block that lost its salience should be quiet rather than loud. Waking
    // somebody at six because a column was unreadable is the worse failure.
    salience = salience.toEnum(Salience.SILENT),
    sortOrder = sortOrder,
)

internal fun Block.toEntity(): BlockEntity = BlockEntity(
    id = id,
    templateId = templateId,
    title = title,
    salience = salience.name,
    sortOrder = sortOrder,
    anchor = anchor.toColumns(),
)

internal fun ItemEntity.toModel(): Item = Item(
    id = id,
    templateId = templateId,
    blockId = blockId,
    kind = kind.toEnum(ItemKind.DO),
    title = title,
    detail = detail,
    anchor = anchor.toModel(),
    duration = durationMinutes?.minutes,
    salience = salience.toEnum(Salience.SILENT),
    weekdays = weekdays.toWeekdays(),
    pinned = pinned,
    minimum = minimumTitle?.let { MinimumVersion(it, minimumDurationMinutes?.minutes) },
    valueKind = valueKind.toEnum(ValueKind.NONE),
    bundleUri = bundleUri,
    trackId = trackId,
    sortOrder = sortOrder,
    archivedAt = archivedAt,
)

internal fun Item.toEntity(): ItemEntity = ItemEntity(
    id = id,
    templateId = templateId,
    blockId = blockId,
    kind = kind.name,
    title = title,
    detail = detail,
    durationMinutes = duration?.inWholeMinutes,
    salience = salience.name,
    weekdays = weekdays.bits,
    pinned = pinned,
    minimumTitle = minimum?.title,
    minimumDurationMinutes = minimum?.duration?.inWholeMinutes,
    valueKind = valueKind.name,
    bundleUri = bundleUri,
    trackId = trackId,
    sortOrder = sortOrder,
    archivedAt = archivedAt,
    anchor = anchor.toColumns(),
)

// Anchors ---------------------------------------------------------------------

/**
 * Rebuilds one of four shapes from the union of their columns.
 *
 * A missing column falls back to midnight or to zero rather than refusing to
 * build. The domain already treats both as degraded input: a zero interval
 * yields one occurrence rather than looping, and a window that ends before it
 * starts yields one too. That means a half written row shows up as a visibly
 * wrong entry the user can correct, which is the outcome worth designing for.
 */
internal fun AnchorColumns.toModel(): Anchor = when (type.toEnum(AnchorType.FIXED)) {
    AnchorType.FIXED -> Anchor.Fixed(at ?: LocalTime.MIDNIGHT)

    AnchorType.RELATIVE -> Anchor.Relative(
        parentItemId = parentItemId ?: NO_PARENT,
        offset = (offsetMinutes ?: 0L).minutes,
    )

    AnchorType.WINDOW -> Anchor.Window(
        from = from ?: LocalTime.MIDNIGHT,
        to = to ?: LocalTime.MIDNIGHT,
        nagLadder = nagLadder.toLadder(),
    )

    AnchorType.INTERVAL -> Anchor.Interval(
        every = (everyMinutes ?: 0L).minutes,
        from = from ?: LocalTime.MIDNIGHT,
        to = to ?: LocalTime.MIDNIGHT,
    )
}

internal fun Anchor.toColumns(): AnchorColumns = when (this) {
    is Anchor.Fixed -> AnchorColumns(type = type.name, at = at)

    is Anchor.Relative -> AnchorColumns(
        type = type.name,
        parentItemId = parentItemId,
        offsetMinutes = offset.inWholeMinutes,
    )

    is Anchor.Window -> AnchorColumns(
        type = type.name,
        from = from,
        to = to,
        nagLadder = nagLadder.toStoredLadder(),
    )

    is Anchor.Interval -> AnchorColumns(
        type = type.name,
        from = from,
        to = to,
        everyMinutes = every.inWholeMinutes,
    )
}

// ------------------------------------------------------------------------------

/**
 * A parent id that cannot match anything. The anchor graph reports it as a
 * missing parent and the day still resolves, which is what a dangling row should
 * look like rather than an exception.
 */
private const val NO_PARENT = -1L

private fun Int.toWeekdays(): Weekdays = Weekdays(this and WEEKDAY_MASK)

private fun String?.toLadder(): List<Duration> = this?.split(NAG_SEPARATOR)
    ?.mapNotNull { it.trim().toLongOrNull()?.minutes }
    .orEmpty()

private fun List<Duration>.toStoredLadder(): String? =
    takeIf { it.isNotEmpty() }?.joinToString(NAG_SEPARATOR) { it.inWholeMinutes.toString() }
