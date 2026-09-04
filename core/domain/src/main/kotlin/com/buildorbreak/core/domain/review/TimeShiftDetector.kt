package com.buildorbreak.core.domain.review

import com.buildorbreak.core.model.execution.Occurrence
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** Below five completions there is no shape to read, only anecdotes. */
private const val MINIMUM_SAMPLES = 5

/**
 * Under twenty minutes is ordinary life, not a mistimed plan. Suggesting a move
 * for a ten minute drift would have the app rewriting the day every week.
 */
private val DEFAULT_THRESHOLD = 20.minutes

/**
 * How far off its planned time an item habitually happens.
 *
 * [median] is positive when the item consistently runs late and negative when it
 * consistently happens early. Reading somewhere before the planned time is a
 * real and common signal: a plan can be as wrong by being too late as by being
 * too early.
 */
data class TimeShift(val itemId: Long, val median: Duration, val samples: Int) {
    val isLate: Boolean get() = median.isPositive()
}

/**
 * Finds items whose planned time disagrees with when they actually happen.
 *
 * This is the difference between a plan that is being ignored and a plan that is
 * simply wrong. If the evening reading slot is at nine and it is genuinely done
 * at ten every night, nothing is broken except the number in the plan, and no
 * amount of louder reminding will fix it. Moving the slot will.
 *
 * **The median, not the mean.** One night that ran three hours late would drag
 * an average far enough to produce a nonsense suggestion, and one unusual day is
 * exactly the thing a habit app must not overreact to. The median ignores it.
 */
class TimeShiftDetector(
    private val minimumSamples: Int = MINIMUM_SAMPLES,
    private val threshold: Duration = DEFAULT_THRESHOLD,
) {

    /**
     * Returns null when there is nothing worth saying, which is most of the
     * time. A detector that always finds something is a detector nobody reads.
     */
    fun detect(itemId: Long, occurrences: List<Occurrence>, zone: ZoneId): TimeShift? {
        // Only a completed item teaches anything about when something really
        // happens. A skip has no real moment attached to it.
        val differences = occurrences
            .filter { it.itemId == itemId }
            .mapNotNull { occurrence ->
                occurrence.learnableInstant?.let { actual ->
                    ChronoUnit.MINUTES.between(occurrence.plannedAt, LocalDateTime.ofInstant(actual, zone))
                }
            }

        if (differences.size < minimumSamples) return null

        val median = medianOf(differences).minutes
        if (median.absoluteValue < threshold) return null

        return TimeShift(itemId = itemId, median = median, samples = differences.size)
    }

    /** The midpoint, averaging the middle pair on an even count. */
    private fun medianOf(values: List<Long>): Long {
        val sorted = values.sorted()
        val middle = sorted.size / 2

        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2
        }
    }
}
