package com.buildorbreak.core.domain.review

import com.buildorbreak.core.model.enums.OccurrenceState
import com.buildorbreak.core.model.enums.SkipChip
import com.buildorbreak.core.model.execution.Occurrence
import com.buildorbreak.core.model.execution.SkipReason
import java.time.DayOfWeek

/** Fewer than three chances is not enough to call anything a habit or a problem. */
private const val MINIMUM_OPPORTUNITIES = 3

/** Missing this share of the chances is past bad luck. */
private const val MISS_RATE = 0.4

/** Two misses on the same weekday out of three chances is a shape, not a coincidence. */
private const val WEEKDAY_CLUSTER_MISSES = 2

/**
 * Why something keeps being missed. This is the whole value of the detector.
 *
 * Three items each missed three times look identical to anything that only
 * counts. They are not the same problem and they do not have the same fix, and
 * an app that offers one answer to all three will be wrong most of the time.
 */
enum class SkipCause {
    /** Something else was happening. The slot is in the wrong place. */
    TIMING,

    /** Nothing was in the way. The step is probably too big. */
    MOTIVATION,

    /** It was simply forgotten. The reminder is too quiet. */
    REMINDER,

    /** Not enough was said to tell. Skip reasons are always optional. */
    UNKNOWN,
    ;

    /** Whether the suggested fix is to move the slot rather than change it. */
    val suggestsMoving: Boolean get() = this == TIMING
}

/**
 * One item that is being missed often enough to mention.
 *
 * [weekday] is set only when the misses cluster on one day, which is a different
 * story from being missed at random and deserves a different suggestion.
 */
data class SkipPattern(
    val itemId: Long,
    val misses: Int,
    val opportunities: Int,
    val cause: SkipCause,
    val weekday: DayOfWeek? = null,
) {
    val missRate: Float get() = if (opportunities == 0) 0f else misses.toFloat() / opportunities
}

/**
 * Finds items that are being missed, and works out what kind of missing it is.
 *
 * Two deliberate restraints:
 *
 * **One miss is never a pattern.** Reacting to a single miss the same day is
 * useful and belongs to the scheduler. Telling somebody they have a pattern
 * after one miss is a lie, and an app that cries wolf gets ignored by the time
 * it is finally right.
 *
 * **The reason outweighs the count.** `SkipReason` is optional and always
 * offered after the fact, so most misses will carry nothing. The detector works
 * on counts alone and gets sharper when reasons exist, rather than needing them.
 */
class SkipPatternDetector(
    private val minimumOpportunities: Int = MINIMUM_OPPORTUNITIES,
    private val missRate: Double = MISS_RATE,
) {

    fun detect(occurrences: List<Occurrence>, reasons: List<SkipReason> = emptyList()): List<SkipPattern> {
        val reasonByOccurrence = reasons.associateBy { it.occurrenceId }

        return occurrences.groupBy { it.itemId }
            .mapNotNull { (itemId, forItem) -> patternFor(itemId, forItem, reasonByOccurrence) }
            .sortedByDescending { it.misses }
    }

    private fun patternFor(itemId: Long, occurrences: List<Occurrence>, reasons: Map<Long, SkipReason>): SkipPattern? {
        // Only settled days count as chances. A day still pending has not been
        // missed yet, and counting it would make every plan look broken by noon.
        val settled = occurrences.filter { it.isSettled }
        if (settled.size < minimumOpportunities) return null

        val missed = settled.filter { it.state == OccurrenceState.MISSED || it.state == OccurrenceState.SKIPPED }
        if (missed.size.toDouble() / settled.size < missRate) return null

        return SkipPattern(
            itemId = itemId,
            misses = missed.size,
            opportunities = settled.size,
            cause = causeOf(missed, reasons),
            weekday = clusteredWeekday(settled, missed),
        )
    }

    /**
     * The most common reason given, mapped to the fix it implies. With nothing
     * recorded this stays [SkipCause.UNKNOWN] rather than guessing, because a
     * confident wrong suggestion costs more than an honest vague one.
     */
    private fun causeOf(missed: List<Occurrence>, reasons: Map<Long, SkipReason>): SkipCause {
        val chips = missed.mapNotNull { reasons[it.id]?.chip }
        if (chips.isEmpty()) return SkipCause.UNKNOWN

        val dominant = chips.groupingBy(::causeOfChip).eachCount()
            .filterKeys { it != SkipCause.UNKNOWN }
            .maxByOrNull { it.value }

        return dominant?.key ?: SkipCause.UNKNOWN
    }

    private fun causeOfChip(chip: SkipChip): SkipCause = when (chip) {
        SkipChip.WORK_CAME_UP, SkipChip.NO_TIME, SkipChip.TRAVELLING -> SkipCause.TIMING
        SkipChip.NOT_IN_MOOD -> SkipCause.MOTIVATION
        SkipChip.FORGOT -> SkipCause.REMINDER
        // Being unwell is not a plan problem, and doing it later is not a miss
        // worth redesigning the day around.
        SkipChip.UNWELL, SkipChip.DID_IT_LATER, SkipChip.OTHER -> SkipCause.UNKNOWN
    }

    /**
     * A weekday is only a cluster when the misses land there and the same day
     * offered a real chance more than once. Missing the one Saturday in the data
     * is not a Saturday problem.
     */
    private fun clusteredWeekday(settled: List<Occurrence>, missed: List<Occurrence>): DayOfWeek? {
        val missesByDay = missed.groupingBy { it.date.dayOfWeek }.eachCount()
        val chancesByDay = settled.groupingBy { it.date.dayOfWeek }.eachCount()

        return missesByDay.entries
            .filter { (day, count) ->
                count >= WEEKDAY_CLUSTER_MISSES && count == chancesByDay.getValue(day)
            }
            .maxByOrNull { it.value }
            ?.key
    }
}
