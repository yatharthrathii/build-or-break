package com.buildorbreak.core.domain.goal

import com.buildorbreak.core.model.enums.DayQuality

/** `DayQuality.GOOD`: eighty percent or more completed. */
private const val GOOD_THRESHOLD = 0.8

/** `DayQuality.OK`: between fifty and eighty percent. Below it the day is POOR. */
private const val OK_THRESHOLD = 0.5

/**
 * How a finished day went, as one of three answers.
 *
 * rules.md section 2 rule 8: a POOR day shows no countdown, no progress bar and
 * no milestone. This classification is what enforces that, and it lives in the
 * domain precisely so that no screen can get it wrong by accident. A bad day is
 * not the moment to congratulate somebody.
 */
fun interface DayQualityClassifier {
    fun classify(done: Int, minimum: Int, total: Int): DayQuality
}

class DefaultDayQualityClassifier : DayQualityClassifier {

    /**
     * A minimum version counts as done, matching `DayClose.adherence`.
     *
     * This is the whole point of declaring a minimum in advance. Taking the
     * smaller version on a bad day is succeeding at the thing the user planned
     * for, and scoring it as a partial failure would teach them to skip instead.
     */
    override fun classify(done: Int, minimum: Int, total: Int): DayQuality {
        // A day with nothing scheduled cannot be failed. This matches
        // DayClose.adherence, which returns 1f rather than dividing by zero.
        if (total <= 0) return DayQuality.GOOD

        val adherence = (done + minimum).toDouble() / total

        return when {
            adherence >= GOOD_THRESHOLD -> DayQuality.GOOD
            adherence >= OK_THRESHOLD -> DayQuality.OK
            else -> DayQuality.POOR
        }
    }
}
