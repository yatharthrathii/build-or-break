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
 */
object Streaks {

    fun currentRun(closes: List<DayClose>, today: LocalDate): Int {
        val byDate = closes.associateBy { it.date }
        var day = today.minusDays(1)
        var run = 0

        while (byDate[day]?.let { it.quality != DayQuality.POOR } == true) {
            run++
            day = day.minusDays(1)
        }

        return run
    }
}
