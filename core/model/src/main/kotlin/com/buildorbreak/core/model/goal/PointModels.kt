package com.buildorbreak.core.model.goal

import com.buildorbreak.core.model.enums.PointReason
import java.time.Instant
import java.time.LocalDate

/**
 * One movement of points that the daily close did not already account for.
 *
 * The points a day is worth are worked out from the close and never stored,
 * so that the score on Insights can never disagree with the counts it is
 * drawn from. This table holds only what that rule cannot express: points
 * granted for watching an ad, and points spent on something.
 *
 * [date] is the day the entry belongs to rather than the moment it was
 * written. For a spend they are usually the same; for a streak freeze the
 * date is the day being covered, which is what lets the run be worked out
 * from the ledger without a second table.
 */
data class PointEntry(
    val id: Long,
    val at: Instant,
    val date: LocalDate,
    /** Positive for points granted, negative for points spent. */
    val delta: Int,
    val reason: PointReason,
) {
    val isSpend: Boolean get() = delta < 0
}

/**
 * What the app owes and what has been used.
 *
 * [earned] counts everything ever earned and never goes down, because it is
 * the record of what somebody actually did and a record that shrinks when
 * they buy something is not a record. [balance] is what is left to spend.
 */
data class Wallet(val earned: Int, val spent: Int) {
    val balance: Int get() = earned - spent

    fun canAfford(cost: Int): Boolean = balance >= cost
}
