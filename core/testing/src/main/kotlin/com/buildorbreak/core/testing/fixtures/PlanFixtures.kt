package com.buildorbreak.core.testing.fixtures

import com.buildorbreak.core.model.enums.ItemKind
import com.buildorbreak.core.model.enums.Salience
import com.buildorbreak.core.model.enums.ValueKind
import com.buildorbreak.core.model.plan.Anchor
import com.buildorbreak.core.model.plan.Item
import com.buildorbreak.core.model.plan.MinimumVersion
import com.buildorbreak.core.model.plan.Weekdays
import java.time.LocalTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Builders for plan types, so a test that cares about one field does not have to
 * spell out fifteen.
 *
 * Every default here is boring on purpose. A test that reads
 * `item(id = 2, anchor = relativeTo(1, 10.minutes))` says exactly what it is
 * about, and nothing else in it can accidentally matter.
 */
object PlanFixtures {

    const val TEMPLATE_ID = 1L

    fun item(
        id: Long,
        title: String = "Item $id",
        anchor: Anchor = fixedAt(8, 0),
        templateId: Long = TEMPLATE_ID,
        blockId: Long? = null,
        kind: ItemKind = ItemKind.DO,
        detail: String? = null,
        duration: Duration? = null,
        salience: Salience = Salience.NOTIFY,
        weekdays: Weekdays = Weekdays.EveryDay,
        pinned: Boolean = false,
        minimum: MinimumVersion? = null,
        valueKind: ValueKind = ValueKind.NONE,
        sortOrder: Int = id.toInt(),
    ): Item = Item(
        id = id,
        templateId = templateId,
        blockId = blockId,
        kind = kind,
        title = title,
        detail = detail,
        anchor = anchor,
        duration = duration,
        salience = salience,
        weekdays = weekdays,
        pinned = pinned,
        minimum = minimum,
        valueKind = valueKind,
        bundleUri = null,
        trackId = null,
        sortOrder = sortOrder,
        archivedAt = null,
    )

    fun fixedAt(hour: Int, minute: Int = 0): Anchor.Fixed =
        Anchor.Fixed(LocalTime.of(hour, minute))

    fun relativeTo(parentItemId: Long, offset: Duration = 10.minutes): Anchor.Relative =
        Anchor.Relative(parentItemId, offset)

    fun window(
        fromHour: Int,
        toHour: Int,
        nagLadder: List<Duration> = emptyList(),
    ): Anchor.Window = Anchor.Window(
        from = LocalTime.of(fromHour, 0),
        to = LocalTime.of(toHour, 0),
        nagLadder = nagLadder,
    )

    fun interval(everyMinutes: Long, fromHour: Int, toHour: Int): Anchor.Interval =
        Anchor.Interval(
            every = everyMinutes.minutes,
            from = LocalTime.of(fromHour, 0),
            to = LocalTime.of(toHour, 0),
        )

    fun minimum(title: String = "Minimum", duration: Duration? = null): MinimumVersion =
        MinimumVersion(title = title, duration = duration)
}
