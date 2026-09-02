package com.buildorbreak.core.model.plan

import com.buildorbreak.core.model.enums.AnchorType
import java.time.LocalTime
import kotlin.time.Duration

/**
 * How an item's time is decided.
 *
 * This is the single design decision that makes the day adaptive. Competing apps
 * store a clock time on every step, so a day that starts late is a day that is
 * entirely wrong. Here a step knows what it is attached to, and moving one thing
 * moves the right subset of everything else.
 */
sealed interface Anchor {

    val type: AnchorType

    /** Absolute clock time. */
    data class Fixed(val at: LocalTime) : Anchor {
        override val type: AnchorType get() = AnchorType.FIXED
    }

    /**
     * An offset from another item.
     *
     * Resolves from the parent's actual completion time when the parent is done,
     * and from the parent's planned time when it was skipped or missed. A chain
     * of these is resolved in dependency order, and a cycle is detected and
     * broken rather than allowed to hang.
     */
    data class Relative(val parentItemId: Long, val offset: Duration) : Anchor {
        override val type: AnchorType get() = AnchorType.RELATIVE
    }

    /**
     * Any time inside a range.
     *
     * [nagLadder] holds offsets from [from] at which escalating reminders fire.
     * The last one is the final call. At [to] the occurrence settles to MISSED
     * with no further noise.
     */
    data class Window(val from: LocalTime, val to: LocalTime, val nagLadder: List<Duration> = emptyList()) : Anchor {
        override val type: AnchorType get() = AnchorType.WINDOW
    }

    /**
     * Repeats every [every] inside a window.
     *
     * Stand up and stretch every forty five minutes between 11:00 and 13:30.
     * A window shorter than the interval yields exactly one occurrence at
     * [from], never zero.
     */
    data class Interval(val every: Duration, val from: LocalTime, val to: LocalTime) : Anchor {
        override val type: AnchorType get() = AnchorType.INTERVAL
    }
}
