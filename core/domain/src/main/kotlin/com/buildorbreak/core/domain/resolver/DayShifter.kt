package com.buildorbreak.core.domain.resolver

import com.buildorbreak.core.model.plan.Anchor
import com.buildorbreak.core.model.plan.Item
import java.time.LocalTime
import kotlin.time.Duration

/** The last minute of a day. A shift never rolls an item onto another date. */
private val LAST_MINUTE = LocalTime.of(23, 59)

private const val SECONDS_PER_MINUTE = 60

/**
 * The plan after the whole day has been moved, and what had to be clamped.
 *
 * [clamped] is carried out rather than logged because the day that comes back
 * has to be able to say which items ran off the end of it.
 */
data class ShiftedPlan(val items: List<Item>, val clamped: Set<Long>)

/**
 * Woke up ninety minutes late. Move the day, keep the gym slot where it is.
 *
 * The shift is applied to the anchors rather than to the resolved times. A
 * `RELATIVE` item is already an offset from something that moved, so shifting
 * the anchor and then resolving lets a chain follow its parent exactly once.
 * Shifting the resolved result would move every child twice: once because its
 * parent moved, and again because it was shifted itself.
 *
 * Pinned items are the whole reason this is a separate decision rather than
 * arithmetic on a list. A booked class at six does not move because the morning
 * ran late, and the items hanging off it should not either.
 */
class DayShifter {

    fun apply(items: List<Item>, shift: Duration): ShiftedPlan {
        if (shift == Duration.ZERO) return ShiftedPlan(items, emptySet())

        val minutes = shift.inWholeMinutes
        val clamped = mutableSetOf<Long>()

        val moved = items.map { item ->
            if (item.pinned) return@map item
            val anchor = shiftAnchor(item.anchor, minutes) { clamped += item.id }
            if (anchor == item.anchor) item else item.copy(anchor = anchor)
        }

        return ShiftedPlan(moved, clamped)
    }

    private fun shiftAnchor(anchor: Anchor, minutes: Long, onClamp: () -> Unit): Anchor = when (anchor) {
        is Anchor.Fixed -> anchor.copy(at = shiftTime(anchor.at, minutes, onClamp))

        is Anchor.Window -> anchor.copy(
            from = shiftTime(anchor.from, minutes, onClamp),
            to = shiftTime(anchor.to, minutes, onClamp),
        )

        is Anchor.Interval -> anchor.copy(
            from = shiftTime(anchor.from, minutes, onClamp),
            to = shiftTime(anchor.to, minutes, onClamp),
        )

        // Already an offset from something that moved. Leaving it alone is what
        // keeps "twenty minutes after the shake" true across a shift.
        is Anchor.Relative -> anchor
    }

    /**
     * Clamps rather than wraps.
     *
     * [LocalTime.plusMinutes] rolls 23:30 plus an hour round to 00:30, which
     * would silently move a late item to the start of the same morning. A day
     * that runs off its own end is a plan problem the user has to see.
     */
    private fun shiftTime(time: LocalTime, minutes: Long, onClamp: () -> Unit): LocalTime {
        val moved = time.toSecondOfDay() + minutes * SECONDS_PER_MINUTE

        return when {
            moved < 0 -> LocalTime.MIN.also { onClamp() }
            moved > LAST_MINUTE.toSecondOfDay() -> LAST_MINUTE.also { onClamp() }
            else -> LocalTime.ofSecondOfDay(moved)
        }
    }
}
