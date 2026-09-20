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
object Streaks {

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
