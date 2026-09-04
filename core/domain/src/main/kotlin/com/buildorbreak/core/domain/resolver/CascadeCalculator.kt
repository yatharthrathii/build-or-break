package com.buildorbreak.core.domain.resolver

import com.buildorbreak.core.model.enums.OccurrenceState
import com.buildorbreak.core.model.execution.Occurrence
import com.buildorbreak.core.model.resolved.CascadePreview
import com.buildorbreak.core.model.resolved.Collision
import com.buildorbreak.core.model.resolved.MovedEntry
import com.buildorbreak.core.model.resolved.ResolvedDay
import com.buildorbreak.core.model.resolved.ResolvedEntry
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Two items ending up closer together than this counts as a clash worth warning
 * about. Below five minutes there is no room to actually do the first thing.
 */
private val COLLISION_THRESHOLD = 5.minutes

/** Never persisted. Exists only inside the second, hypothetical resolve. */
private const val HYPOTHETICAL_OCCURRENCE_ID = -1L

/**
 * What moving one item would do to the rest of the day.
 *
 * architecture.md section 5.1: runs resolve twice and diffs. Every competing app
 * offers a snooze and none of them tell you what it costs, and the only reason
 * this one can is that [TimelineResolver] is a pure function. Run it with the
 * snooze and without, compare the two days, and the answer falls out. There is
 * no separate cascade algorithm to keep in agreement with the resolver, which
 * means the preview can never disagree with what actually happens.
 */
fun interface CascadeCalculator {
    fun preview(input: ResolveInput, itemId: Long, shift: Duration): CascadePreview
}

class DefaultCascadeCalculator(
    private val resolver: TimelineResolver = DefaultTimelineResolver(),
) : CascadeCalculator {

    override fun preview(input: ResolveInput, itemId: Long, shift: Duration): CascadePreview {
        val before = resolver.resolve(input)

        // Not on the plan today. Snoozing it moves nothing, which is a real and
        // useful answer rather than an error.
        val target = before.entryFor(itemId)
            ?: return CascadePreview(itemId, shift, moved = emptyList(), collisions = emptyList())

        val after = resolver.resolve(input.copy(occurrences = withSnooze(input.occurrences, target, shift)))

        return CascadePreview(
            itemId = itemId,
            shift = shift,
            moved = movedBetween(before, after),
            collisions = newCollisions(before, after),
        )
    }

    /**
     * The hypothetical day, expressed as an occurrence rather than as an edited
     * item.
     *
     * A snooze is a fact about one day, not a change to the plan, so this is the
     * same shape the real snooze takes when it is written. That is what makes
     * the preview trustworthy: the second resolve is not a special mode, it is
     * an ordinary resolve of the day the user is about to create.
     */
    private fun withSnooze(occurrences: List<Occurrence>, target: ResolvedEntry, shift: Duration): List<Occurrence> {
        val minutes = shift.inWholeMinutes.toInt()
        val existing = target.occurrence

        if (existing != null) {
            return occurrences.map {
                if (it.id == existing.id) it.copy(shiftMinutes = it.shiftMinutes + minutes) else it
            }
        }

        return occurrences + Occurrence(
            id = HYPOTHETICAL_OCCURRENCE_ID,
            itemId = target.item.id,
            date = target.at.toLocalDate(),
            plannedAt = target.at,
            scheduledAt = null,
            firedAt = null,
            settledAt = null,
            state = OccurrenceState.PENDING,
            shiftMinutes = minutes,
            sequenceInDay = target.sequenceInDay,
        )
    }

    private fun movedBetween(before: ResolvedDay, after: ResolvedDay): List<MovedEntry> {
        val afterByKey = after.entries.associateBy(::keyOf)

        return before.entries.mapNotNull { was ->
            val now = afterByKey[keyOf(was)] ?: return@mapNotNull null
            val from = positionOf(was)
            val to = positionOf(now)

            if (from == to) null else MovedEntry(was.item.id, was.item.title, from, to)
        }
    }

    /**
     * Only clashes the snooze would create.
     *
     * A plan that already has two things at the same minute is not a cost of
     * snoozing, and reporting it as one would train the user to ignore the
     * warning entirely.
     */
    private fun newCollisions(before: ResolvedDay, after: ResolvedDay): List<Collision> {
        val alreadyClashing = collisionsIn(before).map { it.firstItemId to it.secondItemId }.toSet()

        return collisionsIn(after).filterNot { (it.firstItemId to it.secondItemId) in alreadyClashing }
    }

    private fun collisionsIn(day: ResolvedDay): List<Collision> =
        day.entries.sortedBy(::positionOf).zipWithNext().mapNotNull { (first, second) ->
            // Repeats of one INTERVAL item are not in each other's way.
            if (first.item.id == second.item.id) return@mapNotNull null

            val gap = ChronoUnit.SECONDS.between(positionOf(first), positionOf(second)).seconds
            if (gap < COLLISION_THRESHOLD) Collision(first.item.id, second.item.id, gap) else null
        }

    private companion object {
        /**
         * Where an entry actually sits once its own snooze is counted.
         *
         * [ResolvedEntry.at] is the freshly resolved planned time, which already
         * carries any shift inherited from a parent. An item's own shift lives on
         * its occurrence, and adding it here is what lets the diff see the
         * snoozed item move rather than only its children.
         */
        fun positionOf(entry: ResolvedEntry): LocalDateTime =
            entry.at.plusMinutes(entry.occurrence?.shiftMinutes?.toLong() ?: 0L)

        fun keyOf(entry: ResolvedEntry): Pair<Long, Int> = entry.item.id to entry.sequenceInDay
    }
}
