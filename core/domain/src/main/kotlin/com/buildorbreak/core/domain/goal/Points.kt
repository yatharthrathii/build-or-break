package com.buildorbreak.core.domain.goal

import com.buildorbreak.core.model.goal.DayClose

/**
 * What a day is worth, in points.
 *
 * Nothing is stored. A day's points are a pure function of the row the daily
 * close already writes, so they can never disagree with the counts on the
 * Insights screen and there is no second ledger to keep in step. Somebody
 * who checks the arithmetic against the close will find it adds up, and
 * that is the only reason to show a number at all.
 *
 * Three rules, all additive. A step done is ten. A step done as its smaller
 * version is five, because scaling down is not failing. A whole day, every
 * step done in full, is twenty on top. Nothing is ever taken away: a poor
 * day earns whatever its few steps earned, and a missed step is worth
 * nothing rather than minus something. The person this app is for is the
 * person who already misses things, and a score that punishes is a score
 * that gets ignored.
 */
object Points {

    const val PER_STEP = 10
    const val PER_MINIMUM = 5
    const val FULL_DAY_BONUS = 20

    fun of(done: Int, minimum: Int, total: Int): Int {
        val earned = done * PER_STEP + minimum * PER_MINIMUM
        val bonus = if (total > 0 && done == total) FULL_DAY_BONUS else 0

        return earned + bonus
    }

    fun forDay(close: DayClose): Int = of(close.itemsDone, close.itemsMinimum, close.itemsTotal)
}

/**
 * Points already in the bank, up to and including yesterday.
 *
 * Today is not in here. It is still open and its number moves with every
 * tap; the screen adds it live so the total can go up during the day and
 * settle when the day closes.
 */
data class PointsTally(
    /** Every closed day since the plan began. */
    val banked: Int,
    /** Closed days from Monday of the current week. */
    val thisWeek: Int,
    /** The most any single closed day earned. Null before the first close. */
    val bestDay: Int?,
)
