package com.buildorbreak.core.domain.goal

import com.buildorbreak.core.model.goal.Reading

/** Seven days. `GoalProgress.smoothedValue` documents this as the honest number. */
private const val DEFAULT_WINDOW_DAYS = 7

/**
 * A trailing average over a window of calendar days.
 *
 * Body weight swings by up to a kilogram a day on water and food alone. A raw
 * reading must never drive a suggestion, a projection or a milestone, because
 * the user would be told they had gained half a kilogram overnight and then
 * told they had lost it again. Smoothing is what makes the number honest enough
 * to show at all.
 *
 * The window is measured in **days, not in readings**. Somebody who weighs
 * themselves twice in a week should get an average of that week, not an average
 * of their last seven weigh ins stretched across two months.
 */
class MovingAverage(private val windowDays: Int = DEFAULT_WINDOW_DAYS) {

    init {
        require(windowDays >= 1) { "Moving average window must be at least one day: $windowDays" }
    }

    /**
     * One smoothed value per reading, in date order.
     *
     * Early readings average over the days that exist rather than being dropped
     * or padded. A goal has to show something on day two, and the average of two
     * days is the truthful answer to what the first two days say.
     */
    fun smooth(readings: List<Reading>): List<Double> {
        if (readings.isEmpty()) return emptyList()

        val sorted = readings.sortedBy { it.date }
        val smoothed = ArrayList<Double>(sorted.size)

        var oldest = 0
        var sum = 0.0

        sorted.forEachIndexed { index, reading ->
            sum += reading.value

            // Drop everything that has fallen out of the back of the window.
            val earliest = reading.date.minusDays(windowDays - 1L)
            while (sorted[oldest].date < earliest) {
                sum -= sorted[oldest].value
                oldest++
            }

            smoothed += sum / (index - oldest + 1)
        }

        return smoothed
    }
}
