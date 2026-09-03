package com.buildorbreak.core.domain.resolver

import com.buildorbreak.core.model.execution.Occurrence
import com.buildorbreak.core.model.plan.Anchor
import com.buildorbreak.core.model.plan.Item
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** The last minute of a day. A resolved entry never spills onto the next date. */
private val LAST_MINUTE_OF_DAY = LocalTime.of(23, 59)

/**
 * Where one item landed, and what had to be given up to put it there.
 *
 * [degraded] and [clamped] are carried rather than logged because the Today
 * screen shows them. A day that quietly resolved something the wrong way is
 * worse than one that says so.
 */
data class Placement(
    val itemId: Long,
    val at: LocalDateTime,
    /** The `RELATIVE` anchor was unusable, so this came from the fallback. */
    val degraded: Boolean = false,
    /** The computed time fell outside the date and was pulled back to its edge. */
    val clamped: Boolean = false,
    /** Which repeat of an `INTERVAL` item this is. Always 0 for the other three. */
    val sequenceInDay: Int = 0,
)

/**
 * Everything one item needs in order to find its own time.
 *
 * [placed] grows as the resolver walks [AnchorGraph.order], which is why that
 * order has to be correct before this runs: a `RELATIVE` item reads its parent
 * out of this map and the parent must already be in it.
 */
data class AnchorContext(
    val date: LocalDate,
    val zone: ZoneId,
    /** The base a `RELATIVE` item falls back to when its parent is unusable. */
    val dayStart: LocalTime,
    val graph: AnchorGraph,
    /** Items already placed on this pass, keyed by item id. */
    val placed: Map<Long, Placement>,
    /** Today's occurrences, keyed by item id. */
    val occurrences: Map<Long, Occurrence> = emptyMap(),
)

/**
 * Turns one [Anchor] into one concrete moment on one date.
 *
 * This is the smallest unit of the timeline engine, and it is deliberately
 * ignorant of everything around it. It does not know about blocks, weekdays,
 * the salience budget or the day shift. It answers exactly one question: given
 * this item, this date, and the items already placed, what time is this?
 *
 * Keeping it that small is what makes the resolver testable. Every rule below
 * is a single assertion in `AnchorResolverTest` with no schedule around it. That
 * class is in the test source set, so this reference is deliberately plain text:
 * main code cannot see test code, and should not be able to.
 */
class AnchorResolver {

    fun resolve(item: Item, context: AnchorContext): Placement {
        val degraded = context.graph.isDegraded(item.id)

        val raw = when (val anchor = item.anchor) {
            is Anchor.Fixed -> context.date.atTime(anchor.at)

            // The entry sits at the start of its window. The nag ladder and the
            // settle to MISSED at `to` belong to the scheduler, not here.
            is Anchor.Window -> context.date.atTime(anchor.from)

            // The first repeat only. IntervalExpander turns this into the rest,
            // because how many repeats fit is a question about the window, not
            // about this item's anchor.
            is Anchor.Interval -> context.date.atTime(anchor.from)

            is Anchor.Relative -> resolveRelative(item.id, anchor, context)
        }

        return clampToDate(item.id, raw, context.date, degraded)
    }

    /**
     * An offset from whatever the parent actually turned out to be.
     *
     * The parent is read from [AnchorGraph.effectiveParentOf] rather than from
     * the anchor itself, because the graph has already cut the edges that would
     * cycle. Asking the anchor directly would walk straight back into the loop
     * the graph exists to break.
     */
    private fun resolveRelative(itemId: Long, anchor: Anchor.Relative, context: AnchorContext): LocalDateTime {
        val parentId = context.graph.effectiveParentOf(itemId)
        val base = parentId?.let { baseTimeOf(it, context) } ?: context.date.atTime(context.dayStart)

        return base.plusSeconds(anchor.offset.inWholeSeconds)
    }

    /**
     * What the parent counts as, for the purpose of hanging something off it.
     *
     * A completed parent contributes the moment it was actually completed. That
     * is the whole promise of a `RELATIVE` anchor: twenty minutes after the
     * shake, not twenty minutes after the shake was supposed to happen. If
     * breakfast ran forty minutes late, everything downstream moves with it.
     *
     * A parent that was skipped or missed contributes its planned time instead.
     * It never happened, so there is no real moment to use, and collapsing the
     * chain onto the start of the day would move a stack of unrelated items for
     * one skipped step.
     *
     * A snoozed parent contributes its shift. Without this line the snooze
     * consequence preview would have nothing to preview, because moving a parent
     * would leave every child exactly where it was.
     */
    private fun baseTimeOf(parentId: Long, context: AnchorContext): LocalDateTime? {
        val placed = context.placed[parentId] ?: return null
        val occurrence = context.occurrences[parentId]

        occurrence?.learnableInstant?.let { return LocalDateTime.ofInstant(it, context.zone) }

        return placed.at.plusMinutes(occurrence?.shiftMinutes?.toLong() ?: 0L)
    }

    /**
     * Keeps the entry on the date it belongs to.
     *
     * A long enough chain, or a late enough completion, will push a child past
     * midnight. Letting it land on tomorrow would put an entry inside a
     * `ResolvedDay` that does not belong to that day, and every count, every
     * `next()` and every alarm built from it would then be wrong. Clamping is
     * visibly odd on screen, which is the point: the plan is what needs fixing.
     */
    private fun clampToDate(
        itemId: Long,
        at: LocalDateTime,
        date: LocalDate,
        degraded: Boolean,
    ): Placement {
        val firstMoment = date.atStartOfDay()
        val lastMoment = date.atTime(LAST_MINUTE_OF_DAY)

        return when {
            at.isAfter(lastMoment) -> Placement(itemId, lastMoment, degraded, clamped = true)
            at.isBefore(firstMoment) -> Placement(itemId, firstMoment, degraded, clamped = true)
            else -> Placement(itemId, at, degraded, clamped = false)
        }
    }
}
