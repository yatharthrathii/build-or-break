package com.buildorbreak.core.domain.goal

import com.buildorbreak.core.model.enums.DayQuality
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class DayQualityClassifierTest {

    private val classifier = DefaultDayQualityClassifier()

    @Test
    fun `everything done is a good day`() {
        assertThat(classifier.classify(done = 10, minimum = 0, total = 10)).isEqualTo(DayQuality.GOOD)
    }

    @Test
    fun `exactly eighty percent is still a good day`() {
        assertThat(classifier.classify(done = 8, minimum = 0, total = 10)).isEqualTo(DayQuality.GOOD)
    }

    @Test
    fun `just under eighty percent drops to ok`() {
        assertThat(classifier.classify(done = 7, minimum = 0, total = 10)).isEqualTo(DayQuality.OK)
    }

    @Test
    fun `exactly half is still ok`() {
        assertThat(classifier.classify(done = 5, minimum = 0, total = 10)).isEqualTo(DayQuality.OK)
    }

    @Test
    fun `under half is a poor day`() {
        assertThat(classifier.classify(done = 4, minimum = 0, total = 10)).isEqualTo(DayQuality.POOR)
    }

    @Test
    fun `a minimum version counts exactly as much as the full one`() {
        // Scaling down on a bad day is succeeding at what was planned for.
        assertThat(classifier.classify(done = 4, minimum = 4, total = 10)).isEqualTo(DayQuality.GOOD)
    }

    @Test
    fun `a day with nothing scheduled cannot be failed`() {
        assertThat(classifier.classify(done = 0, minimum = 0, total = 0)).isEqualTo(DayQuality.GOOD)
    }

    @Test
    fun `a poor day allows no countdown, no milestone and no praise`() {
        val poor = classifier.classify(done = 1, minimum = 0, total = 10)

        assertThat(poor.allowsCountdown).isFalse()
        assertThat(poor.allowsMilestone).isFalse()
        assertThat(poor.allowsPraise).isFalse()
    }

    @Test
    fun `only a good day allows praise`() {
        assertThat(classifier.classify(done = 6, minimum = 0, total = 10).allowsPraise).isFalse()
        assertThat(classifier.classify(done = 9, minimum = 0, total = 10).allowsPraise).isTrue()
    }
}
