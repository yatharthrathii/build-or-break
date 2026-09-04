package com.buildorbreak.core.domain.resolver

import com.buildorbreak.core.model.plan.Anchor
import com.buildorbreak.core.testing.fixtures.PlanFixtures.fixedAt
import com.buildorbreak.core.testing.fixtures.PlanFixtures.item
import com.buildorbreak.core.testing.fixtures.PlanFixtures.relativeTo
import com.buildorbreak.core.testing.fixtures.PlanFixtures.window
import com.google.common.truth.Truth.assertThat
import java.time.LocalTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import org.junit.jupiter.api.Test

class DayShifterTest {

    private val shifter = DayShifter()

    private fun fixedTimeOf(anchor: Anchor): LocalTime = (anchor as Anchor.Fixed).at

    @Test
    fun `a day that has not moved is handed back untouched`() {
        val items = listOf(item(id = 1, anchor = fixedAt(8)))

        val shifted = shifter.apply(items, Duration.ZERO)

        assertThat(shifted.items).isEqualTo(items)
        assertThat(shifted.clamped).isEmpty()
    }

    @Test
    fun `a fixed anchor moves by the shift`() {
        val shifted = shifter.apply(listOf(item(id = 1, anchor = fixedAt(8))), 90.minutes)

        assertThat(fixedTimeOf(shifted.items.single().anchor)).isEqualTo(LocalTime.of(9, 30))
    }

    @Test
    fun `a window moves both of its ends, keeping its length`() {
        val shifted = shifter.apply(listOf(item(id = 1, anchor = window(11, 14))), 60.minutes)

        val moved = shifted.items.single().anchor as Anchor.Window
        assertThat(moved.from).isEqualTo(LocalTime.of(12, 0))
        assertThat(moved.to).isEqualTo(LocalTime.of(15, 0))
    }

    @Test
    fun `an interval moves both of its ends`() {
        val anchor = Anchor.Interval(45.minutes, LocalTime.of(11, 0), LocalTime.of(13, 30))

        val shifted = shifter.apply(listOf(item(id = 1, anchor = anchor)), 30.minutes)

        val moved = shifted.items.single().anchor as Anchor.Interval
        assertThat(moved.from).isEqualTo(LocalTime.of(11, 30))
        assertThat(moved.to).isEqualTo(LocalTime.of(14, 0))
        assertThat(moved.every).isEqualTo(45.minutes)
    }

    @Test
    fun `a relative anchor is left alone so its parent does not move it twice`() {
        val relative = relativeTo(1, 30.minutes)

        val shifted = shifter.apply(listOf(item(id = 2, anchor = relative)), 90.minutes)

        assertThat(shifted.items.single().anchor).isEqualTo(relative)
    }

    @Test
    fun `a pinned item stays where it is`() {
        val shifted = shifter.apply(
            listOf(
                item(id = 1, anchor = fixedAt(8)),
                item(id = 2, anchor = fixedAt(18), pinned = true),
            ),
            90.minutes,
        )

        assertThat(fixedTimeOf(shifted.items.first { it.id == 1L }.anchor)).isEqualTo(LocalTime.of(9, 30))
        assertThat(fixedTimeOf(shifted.items.first { it.id == 2L }.anchor)).isEqualTo(LocalTime.of(18, 0))
    }

    @Test
    fun `a shift that runs past midnight clamps and names the item`() {
        val shifted = shifter.apply(listOf(item(id = 7, anchor = fixedAt(23, 0))), 120.minutes)

        assertThat(fixedTimeOf(shifted.items.single().anchor)).isEqualTo(LocalTime.of(23, 59))
        assertThat(shifted.clamped).containsExactly(7L)
    }

    @Test
    fun `a shift backwards past midnight clamps to the start of the day`() {
        val shifted = shifter.apply(listOf(item(id = 3, anchor = fixedAt(0, 30))), (-90).minutes)

        assertThat(fixedTimeOf(shifted.items.single().anchor)).isEqualTo(LocalTime.MIN)
        assertThat(shifted.clamped).containsExactly(3L)
    }

    @Test
    fun `an item that fits keeps its place out of the clamped set`() {
        val shifted = shifter.apply(listOf(item(id = 1, anchor = fixedAt(8))), 90.minutes)

        assertThat(shifted.clamped).isEmpty()
    }
}
