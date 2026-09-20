package com.buildorbreak.core.domain.review

import com.buildorbreak.core.model.enums.Salience
import com.buildorbreak.core.model.resolved.ResolvedDay
import com.buildorbreak.core.model.resolved.ResolvedEntry
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * At most three. An evening cannot absorb everything a day dropped, and a list
 * that pretends otherwise is read once and never again. Three things somebody
 * will actually do beats nine things they will not.
 */
private const val MAX_SUGGESTIONS = 3

/** What an item with no stated duration is assumed to take. */
private val ASSUMED_DURATION = 15.minutes

/** Nothing is worth starting in the last few minutes of a day. */
private val LAST_USEFUL_TIME = LocalTime.of(22, 30)

/** A gap left between suggestions so the plan is not shoulder to shoulder. */
private val BREATHING_ROOM = 5.minutes

/**
 * One missed thing, and when it could still happen.
 *
 * [useMinimum] means the full version no longer fits but the smaller one does.
 * That is the entire reason a minimum is declared in advance: on the day it is
 * needed, nobody is in a state to decide what a fair smaller version would be.
 */
data class CatchUpSuggestion(
    val itemId: Long,
    val at: LocalDateTime,
    val duration: Duration,
    val useMinimum: Boolean = false,
    /** Which repeat of an interval item this is. Every repeat is its own miss. */
    val sequenceInDay: Int = 0,
)

/**
 * What is still possible today, and what honestly is not.
 *
 * [outOfTime] is not a failure list to display as one. It is what the day close
 * will settle to MISSED, and knowing it early is what lets the app stop nagging
 * about things that can no longer happen.
 */
data class CatchUpPlan(
    val suggestions: List<CatchUpSuggestion>,
    val outOfTime: List<Long>,
    /**
     * Missed, and it would still fit, but three is as much as is offered.
     *
     * Kept apart from [outOfTime] because the two are different news and
     * only one of them is final. Telling somebody at three in the afternoon
     * that there is no room left for a fifteen minute step, when there are
     * seven hours of evening in front of them, is the app being wrong in the
     * one place it is asking to be believed.
     */
    val beyondCap: List<Long> = emptyList(),
) {
    val hasRoom: Boolean get() = suggestions.isNotEmpty()
}

/**
 * Fits what was missed into the time that is actually left.
 *
 * The honest version of a catch up feature. Most apps either say nothing when a
 * day slips or roll everything forward until the list is absurd, and both teach
 * the user that the list is fiction. This one packs the remaining hours, in
 * order of how much each thing matters, offers the smaller version where only
 * that fits, and says plainly what will not fit at all.
 *
 * Pinned items are never rescheduled. A booked class or a dose that has to land
 * at a particular hour is pinned precisely because moving it is not an option,
 * and quietly proposing a new time for it would be wrong in the one case where
 * being wrong matters most.
 */
class CatchUpPlanner(
    private val maxSuggestions: Int = MAX_SUGGESTIONS,
    private val lastUsefulTime: LocalTime = LAST_USEFUL_TIME,
) {

    fun plan(day: ResolvedDay, now: LocalDateTime): CatchUpPlan {
        val deadline = now.toLocalDate().atTime(lastUsefulTime)
        val candidates = missedSoFar(day, now)

        if (candidates.isEmpty() || !now.isBefore(deadline)) {
            return CatchUpPlan(emptyList(), candidates.map { it.item.id })
        }

        val suggestions = mutableListOf<CatchUpSuggestion>()
        val outOfTime = mutableListOf<Long>()
        val beyondCap = mutableListOf<Long>()
        val busy = stillToCome(day, now)
        var cursor = now

        candidates.forEach { entry ->
            if (suggestions.size >= maxSuggestions) {
                beyondCap += entry.item.id
                return@forEach
            }

            val fitted = fit(entry, cursor, deadline, busy)
            if (fitted == null) {
                outOfTime += entry.item.id
            } else {
                suggestions += fitted
                cursor = fitted.at.plusSeconds((fitted.duration + BREATHING_ROOM).inWholeSeconds)
            }
        }

        return CatchUpPlan(suggestions, outOfTime, beyondCap)
    }

    /**
     * Everything whose moment has passed and which nobody has settled, ordered
     * by how much it matters and then by when it was meant to happen. An alarm
     * that was missed outranks a silent note that was missed, however much
     * earlier the note was due.
     */
    private fun missedSoFar(day: ResolvedDay, now: LocalDateTime): List<ResolvedEntry> = day.entries
        .filter { it.at < now && it.occurrence?.isSettled != true && !it.item.pinned && it.item.catchable }
        .filter { it.salience != Salience.TIMELINE }
        .sortedWith(compareBy({ salienceRank(it.salience) }, { it.at }, { it.item.id }))

    /**
     * The full version if it fits, the minimum if only that does, nothing if
     * neither. Trying the full version first matters: offering the smaller one
     * while there was still room for the real thing quietly lowers the bar.
     */
    private fun fit(
        entry: ResolvedEntry,
        from: LocalDateTime,
        deadline: LocalDateTime,
        busy: List<Slot>,
    ): CatchUpSuggestion? {
        val full = entry.item.duration ?: ASSUMED_DURATION
        val fullStart = freeFrom(from, full, busy)
        if (fitsBefore(fullStart, full, deadline)) {
            return CatchUpSuggestion(entry.item.id, fullStart, full, useMinimum = false, entry.sequenceInDay)
        }

        val minimum = entry.item.minimum?.let { it.duration ?: ASSUMED_DURATION } ?: return null
        val minimumStart = freeFrom(from, minimum, busy)
        if (fitsBefore(minimumStart, minimum, deadline)) {
            return CatchUpSuggestion(entry.item.id, minimumStart, minimum, useMinimum = true, entry.sequenceInDay)
        }

        return null
    }

    /**
     * The evening is not empty. Whatever is still to come, pinned or not,
     * holds its slot, and a missed step is fitted into the gaps between them
     * rather than on top of them. Two alarms in the same minute, one for
     * dinner and one for the gym that was missed at six, is the app moving
     * the problem rather than solving it.
     */
    private fun stillToCome(day: ResolvedDay, now: LocalDateTime): List<Slot> = day.entries
        .filter { it.at >= now && it.occurrence?.isSettled != true && it.salience != Salience.TIMELINE }
        .map { Slot(it.at, it.at.plusSeconds((it.item.duration ?: ASSUMED_DURATION).inWholeSeconds)) }
        .sortedBy { it.start }

    /** The first start at or after [from] where [duration] overlaps nothing in [busy]. */
    private fun freeFrom(from: LocalDateTime, duration: Duration, busy: List<Slot>): LocalDateTime {
        var start = from

        // One pass is enough: the slots are sorted, so a start pushed past one
        // can only collide with a later one, which is the next one checked.
        busy.forEach { slot ->
            val end = start.plusSeconds(duration.inWholeSeconds)
            if (start < slot.end && end > slot.start) {
                start = slot.end.plusSeconds(BREATHING_ROOM.inWholeSeconds)
            }
        }

        return start
    }

    private data class Slot(val start: LocalDateTime, val end: LocalDateTime)

    private fun fitsBefore(from: LocalDateTime, duration: Duration, deadline: LocalDateTime): Boolean =
        !from.plusSeconds(duration.inWholeSeconds).isAfter(deadline)

    private fun salienceRank(salience: Salience): Int = when (salience) {
        Salience.ALARM -> 0
        Salience.NOTIFY -> 1
        Salience.SILENT -> 2
        // Filtered out before this point. Sorted last rather than given a rank,
        // so a leak upstream shows up as a stray entry at the end of the list
        // instead of quietly outranking something that matters.
        Salience.TIMELINE -> Int.MAX_VALUE
    }
}
