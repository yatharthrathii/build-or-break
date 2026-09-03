package com.buildorbreak.core.domain.resolver

import com.buildorbreak.core.model.plan.Anchor
import com.buildorbreak.core.model.plan.Item
import com.buildorbreak.core.testing.fixtures.ExecutionFixtures
import com.buildorbreak.core.testing.fixtures.PlanFixtures.fixedAt
import com.buildorbreak.core.testing.fixtures.PlanFixtures.item
import com.google.common.truth.Truth.assertThat
import java.time.LocalTime
import kotlin.time.Duration.Companion.minutes
import org.junit.jupiter.api.Test

class IntervalExpanderTest {

    private val expander = IntervalExpander()
    private val resolver = AnchorResolver()
    private val builder = AnchorGraphBuilder()

    private val date = ExecutionFixtures.DATE
    private val zone = ExecutionFixtures.ZONE
    private val dayStart: LocalTime = LocalTime.of(6, 0)

    /** Places the item the way the timeline resolver will, then expands it. */
    private fun expand(item: Item): List<Placement> {
        val graph = builder.build(listOf(item))
        val base = resolver.resolve(item, AnchorContext(date, zone, dayStart, graph, placed = emptyMap()))
        return expander.expand(item, base)
    }

    private fun intervalItem(everyMinutes: Long, from: LocalTime, to: LocalTime): Item =
        item(id = 1, anchor = Anchor.Interval(everyMinutes.minutes, from, to))

    @Test
    fun `an item that is not an interval expands to itself`() {
        val expanded = expand(item(id = 1, anchor = fixedAt(8)))

        assertThat(expanded).hasSize(1)
        assertThat(expanded.single().at).isEqualTo(date.atTime(8, 0))
        assertThat(expanded.single().sequenceInDay).isEqualTo(0)
    }

    @Test
    fun `every forty five minutes between eleven and half past one gives four repeats`() {
        val expanded = expand(intervalItem(45, LocalTime.of(11, 0), LocalTime.of(13, 30)))

        assertThat(expanded.map { it.at }).containsExactly(
            date.atTime(11, 0),
            date.atTime(11, 45),
            date.atTime(12, 30),
            date.atTime(13, 15),
        ).inOrder()
    }

    @Test
    fun `repeats are numbered from zero so an occurrence can tell them apart`() {
        val expanded = expand(intervalItem(45, LocalTime.of(11, 0), LocalTime.of(13, 30)))

        assertThat(expanded.map { it.sequenceInDay }).containsExactly(0, 1, 2, 3).inOrder()
    }

    @Test
    fun `a repeat landing exactly on the end of the window is included`() {
        val expanded = expand(intervalItem(45, LocalTime.of(11, 0), LocalTime.of(12, 30)))

        assertThat(expanded.map { it.at }).containsExactly(
            date.atTime(11, 0),
            date.atTime(11, 45),
            date.atTime(12, 30),
        ).inOrder()
    }

    @Test
    fun `a window shorter than the interval still yields one occurrence, never zero`() {
        val expanded = expand(intervalItem(45, LocalTime.of(11, 0), LocalTime.of(11, 30)))

        assertThat(expanded).hasSize(1)
        assertThat(expanded.single().at).isEqualTo(date.atTime(11, 0))
    }

    @Test
    fun `a zero interval yields one occurrence rather than looping forever`() {
        val expanded = expand(intervalItem(0, LocalTime.of(11, 0), LocalTime.of(13, 30)))

        assertThat(expanded).hasSize(1)
        assertThat(expanded.single().at).isEqualTo(date.atTime(11, 0))
    }

    @Test
    fun `a window that ends before it starts yields one occurrence`() {
        val expanded = expand(intervalItem(45, LocalTime.of(13, 0), LocalTime.of(11, 0)))

        assertThat(expanded).hasSize(1)
        assertThat(expanded.single().at).isEqualTo(date.atTime(13, 0))
    }

    @Test
    fun `expansion stops at the daily ceiling rather than filling the day`() {
        // One minute apart across the whole day would otherwise be 1440 entries.
        val expanded = expand(intervalItem(1, LocalTime.of(0, 0), LocalTime.of(23, 59)))

        assertThat(expanded).hasSize(IntervalExpander.MAX_REPEATS_PER_DAY)
        assertThat(expanded.last().sequenceInDay).isEqualTo(IntervalExpander.MAX_REPEATS_PER_DAY - 1)
    }

    @Test
    fun `every repeat carries the flags decided when the first one was placed`() {
        val base = Placement(itemId = 1, at = date.atTime(11, 0), degraded = true, clamped = true)
        val expanded = expander.expand(intervalItem(45, LocalTime.of(11, 0), LocalTime.of(13, 30)), base)

        assertThat(expanded).hasSize(4)
        assertThat(expanded.all { it.degraded && it.clamped }).isTrue()
    }
}
