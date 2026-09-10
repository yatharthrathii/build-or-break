package com.buildorbreak.core.domain.goal

import com.buildorbreak.core.model.execution.Occurrence
import java.time.LocalDate

/**
 * How many days in a row each step has been done, ending today.
 *
 * Per step, not per day. A whole day run says the plan was kept; a step run
 * says one habit has held for thirty days, which is the milestone people
 * actually remember. Counted from occurrences rather than closes because
 * closes only carry totals.
 *
 * A day with no occurrence for the step does not break the run: the step may
 * simply not run on that weekday. A day where it was missed or skipped does.
 */
object ItemRuns {

    fun longest(occurrences: List<Occurrence>, today: LocalDate): ItemRun? = occurrences.groupBy { it.itemId }
        .map { (itemId, rows) -> ItemRun(itemId, runOf(rows, today)) }
        .filter { it.days > 0 }
        .maxByOrNull { it.days }

    private fun runOf(rows: List<Occurrence>, today: LocalDate): Int {
        val byDate = rows.filter { it.isSettled }.groupBy { it.date }
        val earliest = byDate.keys.minOrNull() ?: return 0
        var day = today
        var run = 0

        while (!day.isBefore(earliest)) {
            val settled = byDate[day]
            when {
                settled == null -> Unit
                settled.all { it.isDone } -> run++
                else -> return run
            }
            day = day.minusDays(1)
        }

        return run
    }
}
