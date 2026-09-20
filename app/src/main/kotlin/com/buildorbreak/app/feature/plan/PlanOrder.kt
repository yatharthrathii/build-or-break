package com.buildorbreak.app.feature.plan

import com.buildorbreak.core.model.plan.Anchor
import com.buildorbreak.core.model.plan.Item
import java.time.LocalTime

/** A chain of "after" steps deeper than this is a loop, and a loop has no clock. */
private const val MAX_CHAIN = 32

/**
 * The plan in the order the day runs it.
 *
 * The rows used to come out in the order they were typed, so a step added at
 * 08:10 sat under one at 11:15 pm, and the list disagreed with Today about
 * what came first. The list is placed by clock now: a fixed step at its time,
 * a window or a repeat at its start, and a step that comes after another at
 * its parent's clock plus the offset. Two steps at the same minute keep the
 * order the user gave them, which is the one thing dragging is for.
 */
object PlanOrder {

    /** No clock. A step that comes after a missing or looping parent. */
    const val NO_SLOT = -1

    fun sorted(items: List<Item>): List<Item> {
        val byId = items.associateBy { it.id }

        return items.sortedWith(
            compareBy(
                { clockOf(it, byId) ?: LocalTime.MAX },
                { it.sortOrder },
                { it.id },
            ),
        )
    }

    /**
     * The minute of the day a step starts at, or [NO_SLOT].
     *
     * Two steps with the same slot are a tie, and a tie is the only place the
     * order can be changed by hand without also changing a time.
     */
    fun slotOf(item: Item, items: List<Item>): Int =
        clockOf(item, items.associateBy { it.id })?.let { it.toSecondOfDay() / SECONDS_PER_MINUTE } ?: NO_SLOT

    private fun clockOf(item: Item, byId: Map<Long, Item>, depth: Int = 0): LocalTime? =
        when (val anchor = item.anchor) {
            is Anchor.Fixed -> anchor.at
            is Anchor.Window -> anchor.from
            is Anchor.Interval -> anchor.from
            is Anchor.Relative -> {
                val parent = byId[anchor.parentItemId]?.takeIf { depth < MAX_CHAIN }
                val start = parent?.let { clockOf(it, byId, depth + 1) }

                // Held at the end of the day rather than wrapped past midnight:
                // a step twenty minutes after one at 23:50 is still an evening
                // step, and wrapping it to 00:10 would put it at the top.
                start?.let {
                    if (it.toSecondOfDay() + anchor.offset.inWholeSeconds >=
                        SECONDS_PER_DAY
                    ) {
                        LocalTime.MAX
                    } else {
                        it.plusSeconds(anchor.offset.inWholeSeconds)
                    }
                }
            }
        }

    private const val SECONDS_PER_MINUTE = 60
    private const val SECONDS_PER_DAY = 24 * 60 * 60
}
