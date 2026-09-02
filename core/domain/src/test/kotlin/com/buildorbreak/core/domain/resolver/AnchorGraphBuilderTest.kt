package com.buildorbreak.core.domain.resolver

import com.buildorbreak.core.testing.fixtures.PlanFixtures.fixedAt
import com.buildorbreak.core.testing.fixtures.PlanFixtures.item
import com.buildorbreak.core.testing.fixtures.PlanFixtures.relativeTo
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class AnchorGraphBuilderTest {

    private val builder = AnchorGraphBuilder()

    @Test
    fun `an item with no parent orders on its own and reports no issues`() {
        val graph = builder.build(listOf(item(id = 1, anchor = fixedAt(8))))

        assertThat(graph.order).containsExactly(1L)
        assertThat(graph.hasIssues).isFalse()
        assertThat(graph.cycles).isEmpty()
        assertThat(graph.effectiveParentOf(1)).isNull()
    }

    @Test
    fun `a three deep chain orders parents before children`() {
        val graph = builder.build(
            listOf(
                item(id = 1, anchor = fixedAt(8)),
                item(id = 2, anchor = relativeTo(1)),
                item(id = 3, anchor = relativeTo(2)),
            ),
        )

        assertThat(graph.order).containsExactly(1L, 2L, 3L).inOrder()
        assertThat(graph.hasIssues).isFalse()
    }

    @Test
    fun `a chain declared out of order still resolves parents first`() {
        val graph = builder.build(
            listOf(
                item(id = 3, anchor = relativeTo(2)),
                item(id = 2, anchor = relativeTo(1)),
                item(id = 1, anchor = fixedAt(8)),
            ),
        )

        assertThat(graph.order).containsExactly(1L, 2L, 3L).inOrder()
    }

    @Test
    fun `two items pointing at each other is detected as a cycle`() {
        val graph = builder.build(
            listOf(
                item(id = 1, anchor = relativeTo(2)),
                item(id = 2, anchor = relativeTo(1)),
            ),
        )

        assertThat(graph.cycles).hasSize(1)
        assertThat(graph.cycles.single()).containsExactly(1L, 2L)
    }

    @Test
    fun `a cycle is broken at the largest id, which is the most recently created`() {
        val graph = builder.build(
            listOf(
                item(id = 1, anchor = relativeTo(2)),
                item(id = 2, anchor = relativeTo(1)),
            ),
        )

        assertThat(graph.brokenByCycle).containsExactly(2L)
        assertThat(graph.isDegraded(2)).isTrue()
        assertThat(graph.isDegraded(1)).isFalse()
    }

    @Test
    fun `after breaking a cycle the broken item is ordered before the one that depends on it`() {
        val graph = builder.build(
            listOf(
                item(id = 1, anchor = relativeTo(2)),
                item(id = 2, anchor = relativeTo(1)),
            ),
        )

        // 2 loses its anchor and falls back, so it can be placed first, and 1
        // can then legitimately resolve from it.
        assertThat(graph.order).containsExactly(2L, 1L).inOrder()
        assertThat(graph.effectiveParentOf(2)).isNull()
        assertThat(graph.effectiveParentOf(1)).isEqualTo(2L)
    }

    @Test
    fun `a three item cycle is detected and broken once`() {
        val graph = builder.build(
            listOf(
                item(id = 1, anchor = relativeTo(2)),
                item(id = 2, anchor = relativeTo(3)),
                item(id = 3, anchor = relativeTo(1)),
            ),
        )

        assertThat(graph.cycles).hasSize(1)
        assertThat(graph.cycles.single()).containsExactly(1L, 2L, 3L)
        assertThat(graph.brokenByCycle).containsExactly(3L)
        assertThat(graph.order).containsExactly(3L, 2L, 1L).inOrder()
    }

    @Test
    fun `an item pointing at a parent that is not on this template is flagged`() {
        val graph = builder.build(
            listOf(
                item(id = 1, anchor = fixedAt(8)),
                item(id = 2, anchor = relativeTo(parentItemId = 99)),
            ),
        )

        assertThat(graph.missingParent).containsExactly(2L)
        assertThat(graph.isDegraded(2)).isTrue()
        assertThat(graph.cycles).isEmpty()
        assertThat(graph.effectiveParentOf(2)).isNull()
        assertThat(graph.order).containsExactly(1L, 2L)
    }

    @Test
    fun `independent items and a chain coexist without either affecting the other`() {
        val graph = builder.build(
            listOf(
                item(id = 1, anchor = fixedAt(8)),
                item(id = 2, anchor = relativeTo(1)),
                item(id = 3, anchor = fixedAt(13)),
                item(id = 4, anchor = fixedAt(19)),
            ),
        )

        assertThat(graph.hasIssues).isFalse()
        assertThat(graph.order).hasSize(4)
        assertThat(graph.order.indexOf(1L)).isLessThan(graph.order.indexOf(2L))
    }

    @Test
    fun `the same broken plan always produces the same order`() {
        val items = listOf(
            item(id = 5, anchor = relativeTo(7)),
            item(id = 7, anchor = relativeTo(5)),
            item(id = 9, anchor = fixedAt(6)),
            item(id = 2, anchor = relativeTo(9)),
        )

        val first = builder.build(items)
        val second = builder.build(items)
        val reversed = builder.build(items.reversed())

        assertThat(second.order).isEqualTo(first.order)
        assertThat(second.brokenByCycle).isEqualTo(first.brokenByCycle)
        // Input order may change which root is visited first, but the cut edge
        // must not depend on it. A plan that reshuffles between launches is
        // worse than one that is simply wrong.
        assertThat(reversed.brokenByCycle).isEqualTo(first.brokenByCycle)
    }

    @Test
    fun `two separate cycles are both found`() {
        val graph = builder.build(
            listOf(
                item(id = 1, anchor = relativeTo(2)),
                item(id = 2, anchor = relativeTo(1)),
                item(id = 3, anchor = relativeTo(4)),
                item(id = 4, anchor = relativeTo(3)),
            ),
        )

        assertThat(graph.cycles).hasSize(2)
        assertThat(graph.brokenByCycle).containsExactly(2L, 4L)
    }

    @Test
    fun `an empty template produces an empty graph rather than throwing`() {
        val graph = builder.build(emptyList())

        assertThat(graph.order).isEmpty()
        assertThat(graph.hasIssues).isFalse()
    }
}
