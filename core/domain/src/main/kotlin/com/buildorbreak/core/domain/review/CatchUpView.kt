package com.buildorbreak.core.domain.review

import com.buildorbreak.core.model.resolved.ResolvedDay
import java.time.LocalDateTime

/**
 * One missed step and the slot it could still take, named.
 *
 * [useMinimum] means the full version no longer fits but the smaller one does,
 * which is the entire reason a minimum is declared in advance: on the day it is
 * needed nobody is in a state to decide what a fair smaller version would be.
 */
data class CatchUpStep(
    val itemId: Long,
    /** Which repeat of an interval item. Water at 14:00 and water at 16:00 are two misses. */
    val sequenceInDay: Int,
    val occurrenceId: Long,
    val title: String,
    val at: LocalDateTime,
    val minutes: Int,
    val useMinimum: Boolean,
)

/**
 * What is still possible today, and what honestly is not.
 *
 * [outOfTime] is named rather than counted because "2 things will not fit" is
 * a number somebody has to go and decode, and "the gym and the long read will
 * not fit" is something they can act on immediately, including by deciding
 * they do not mind.
 */
data class CatchUpView(
    val steps: List<CatchUpStep>,
    val outOfTime: List<String>,
    /** Missed, still possible, and not offered because three is the limit. */
    val alsoMissed: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = steps.isEmpty() && outOfTime.isEmpty() && alsoMissed.isEmpty()
}

/**
 * Turns a fitted catch up plan into something a screen can draw.
 *
 * The planner works in item ids because it is pure arithmetic over a day. This
 * puts the titles back on and drops whatever is already being offered
 * elsewhere, which in practice is the step on the card at the top of Today.
 * Listing it twice would read as the app having lost count of its own day.
 */
class CatchUpViewBuilder(private val planner: CatchUpPlanner = CatchUpPlanner()) {

    /**
     * Returns null when there is nothing to say, which is most days. A panel
     * that is always on screen is a panel nobody reads, and a routine app that
     * greets an on time morning with a recovery plan is telling somebody they
     * are behind when they are not.
     */
    fun build(day: ResolvedDay, now: LocalDateTime, excludingOccurrenceId: Long = NONE): CatchUpView? {
        val plan = planner.plan(day, now)
        val titles = day.entries.associate { it.item.id to it.item.title }

        val steps = plan.suggestions
            .mapNotNull { suggestion ->
                val entry = day.entries.firstOrNull {
                    it.item.id == suggestion.itemId && it.sequenceInDay == suggestion.sequenceInDay
                } ?: return@mapNotNull null
                val occurrenceId = entry.occurrence?.id ?: NONE
                if (occurrenceId == excludingOccurrenceId) return@mapNotNull null

                CatchUpStep(
                    itemId = suggestion.itemId,
                    sequenceInDay = suggestion.sequenceInDay,
                    occurrenceId = occurrenceId,
                    title = if (suggestion.useMinimum) {
                        entry.item.minimum?.title ?: entry.item.title
                    } else {
                        entry.item.title
                    },
                    at = suggestion.at,
                    minutes = suggestion.duration.inWholeMinutes.toInt(),
                    useMinimum = suggestion.useMinimum,
                )
            }

        val view = CatchUpView(
            steps = steps,
            outOfTime = plan.outOfTime.mapNotNull { titles[it] },
            alsoMissed = plan.beyondCap.mapNotNull { titles[it] },
        )

        return view.takeIf { !it.isEmpty }
    }

    private companion object {
        const val NONE = 0L
    }
}
