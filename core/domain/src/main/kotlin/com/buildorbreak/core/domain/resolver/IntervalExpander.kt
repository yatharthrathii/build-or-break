package com.buildorbreak.core.domain.resolver

import com.buildorbreak.core.model.plan.Anchor
import com.buildorbreak.core.model.plan.Item
import kotlin.time.Duration

/**
 * Turns one `INTERVAL` item into every repeat that fits inside its window.
 *
 * Stand up and stretch every forty five minutes between 11:00 and 13:30 is one
 * row in the plan and four entries in the day. This is the only place in the
 * resolver where one item becomes several, which is why it is a separate pass
 * rather than a branch inside [AnchorResolver]: that class answers "what time
 * is this item", and the answer here is a list.
 *
 * The first repeat is not recomputed. It arrives already placed, so any clamping
 * or degrading decided upstream is carried into every repeat rather than being
 * quietly re-derived with different rules.
 */
class IntervalExpander {

    fun expand(item: Item, base: Placement): List<Placement> {
        val anchor = item.anchor as? Anchor.Interval ?: return listOf(base)

        // A window that ends before it starts, or a non advancing step, is a
        // broken plan rather than an empty day. Both fall back to the single
        // occurrence the anchor documentation promises. A step of zero would
        // also loop forever, so this guard is load bearing, not defensive.
        if (anchor.every <= Duration.ZERO || anchor.to.isBefore(anchor.from)) return listOf(base)

        val date = base.at.toLocalDate()
        val lastMoment = date.atTime(anchor.to)
        val stepSeconds = anchor.every.inWholeSeconds

        val repeats = ArrayList<Placement>()
        var at = base.at

        // Inclusive of the window end: a repeat landing exactly on 13:30 is
        // inside "between 11:00 and 13:30", not one past it.
        while (!at.isAfter(lastMoment) && repeats.size < MAX_REPEATS_PER_DAY) {
            repeats += base.copy(at = at, sequenceInDay = repeats.size)
            at = at.plusSeconds(stepSeconds)
        }

        // Never zero. A window shorter than the interval, or a base already
        // pushed past the window by clamping, still owes the day one entry.
        return repeats.ifEmpty { listOf(base) }
    }

    companion object {
        /**
         * The ceiling on repeats of one item in one day.
         *
         * Every thirty minutes around the clock reaches exactly this. Anything
         * that wants more is a plan that needs editing, and expanding it would
         * hand the scheduler and the Today list hundreds of entries built from a
         * single mistyped number.
         */
        const val MAX_REPEATS_PER_DAY = 48
    }
}
