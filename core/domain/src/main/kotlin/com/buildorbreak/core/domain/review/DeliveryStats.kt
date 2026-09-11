package com.buildorbreak.core.domain.review

import com.buildorbreak.core.model.audit.DeliveryAudit
import java.time.Instant

/**
 * What the alarms actually did, measured rather than claimed.
 *
 * Android alarm reliability is the whole product, and the only way to know
 * whether it works on a mid range phone at six in the morning is to count. This
 * is the counting. Every field is a plain integer a person can check against
 * their own week; nothing here is a score out of ten.
 *
 * [late] is deliberately separate from [missed]. An alarm that fired four
 * minutes late is a different problem from one that never fired at all, they
 * have different causes on Android, and collapsing them into one number would
 * hide the one the user can actually do something about.
 */
data class DeliveryStats(
    val scheduled: Int,
    val fired: Int,
    val onTime: Int,
    val late: Int,
    /** The middle lateness of the ones that fired late. Null when none did. */
    val medianLateSeconds: Long?,
    val worstLateSeconds: Long?,
    val days: Int,
) {
    /** Scheduled alarms whose moment has passed with nothing recorded. */
    val missed: Int get() = (scheduled - fired).coerceAtLeast(0)

    val hasData: Boolean get() = scheduled > 0

    /** Zero to one. Only counts the ones that arrived within the tolerance. */
    val accuracy: Float get() = if (scheduled == 0) 0f else onTime.toFloat() / scheduled

    companion object {
        val None = DeliveryStats(
            scheduled = 0,
            fired = 0,
            onTime = 0,
            late = 0,
            medianLateSeconds = null,
            worstLateSeconds = null,
            days = 0,
        )

        /**
         * Counts a window of audit rows.
         *
         * Rows still in the future are excluded. An alarm scheduled for this
         * evening has not been missed, and counting it as one would make the
         * figure worse every time somebody opened the screen early in the day,
         * which is the opposite of what a reliability number is for.
         */
        fun of(rows: List<DeliveryAudit>, days: Int, now: Instant): DeliveryStats {
            val due = rows.filter { it.scheduledFor <= now }
            if (due.isEmpty()) return None.copy(days = days)

            val fired = due.filter { it.fired }
            val onTime = fired.filter { it.wasOnTime() }
            val late = fired.filterNot { it.wasOnTime() }.mapNotNull { it.latencySeconds }.sorted()

            return DeliveryStats(
                scheduled = due.size,
                fired = fired.size,
                onTime = onTime.size,
                late = late.size,
                medianLateSeconds = late.getOrNull(late.size / 2),
                worstLateSeconds = late.lastOrNull(),
                days = days,
            )
        }
    }
}
