package com.buildorbreak.core.domain.goal

import com.buildorbreak.core.model.enums.DayQuality
import com.buildorbreak.core.model.goal.DayClose
import java.time.LocalDate

/**
 * How many days in a row the plan has been kept.
 *
 * A day counts when it closed as `GOOD` or `OK`. `POOR` breaks the run and a
 * missing day breaks it too, because a day the app never closed is a day with
 * no evidence either way, and a streak built on absence is a streak that lies.
 *
 * **Yesterday is the last day that can count.** Today is still open, and
 * counting it would make the number go up at midnight and down at breakfast.
 *
 * **A frozen day carries the run without adding to it.** A freeze is bought
 * with points for a day that was genuinely missed, and it says "do not count
 * this against me" rather than "pretend I did it". Letting it add to the
 * number would make the run a thing you can buy, which is the one property
 * a run must not have.
 */
/** One day a freeze could cover, and what the run would be either way. */
data class FreezeOffer(val date: LocalDate, val runNow: Int, val runAfter: Int)

object Streaks {

    /**
     * The missed day that ended the run, when covering it would mend the run.
     *
     * Null when there is nothing to mend: no history yet, or a gap with no
     * kept days behind it. Offering a freeze for a day like that would take
     * points and change nothing, which is the one thing a price must not do.
     */
    fun freezeOffer(closes: List<DayClose>, today: LocalDate, frozen: Set<LocalDate> = emptySet()): FreezeOffer? {
        val earliest = closes.minOfOrNull { it.date } ?: return null
        val byDate = closes.associateBy { it.date }
        var day = today.minusDays(1)

        while (day >= earliest) {
            val kept = byDate[day]?.let { it.quality != DayQuality.POOR } == true
            if (!kept && day !in frozen) break

            day = day.minusDays(1)
        }

        if (day < earliest) return null

        val now = currentRun(closes, today, frozen)
        val after = currentRun(closes, today, frozen + day)

        return if (after > now) FreezeOffer(date = day, runNow = now, runAfter = after) else null
    }

    fun currentRun(closes: List<DayClose>, today: LocalDate, frozen: Set<LocalDate> = emptySet()): Int {
        val byDate = closes.associateBy { it.date }
        var day = today.minusDays(1)
        var run = 0

        while (true) {
            val kept = byDate[day]?.let { it.quality != DayQuality.POOR } == true

            when {
                kept -> run++
                day in frozen -> Unit
                else -> return run
            }

            day = day.minusDays(1)
        }
    }
}
