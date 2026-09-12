package com.buildorbreak.app.feature.plan

import com.buildorbreak.core.testing.fixtures.PlanFixtures.fixedAt
import com.buildorbreak.core.testing.fixtures.PlanFixtures.item
import com.buildorbreak.core.testing.fixtures.PlanFixtures.relativeTo
import com.buildorbreak.core.testing.fixtures.PlanFixtures.window
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.minutes
import org.junit.Test

/**
 * The plan reads top to bottom the way the day runs.
 *
 * A step added at 08:10 used to land under one at 11:15 pm because the list
 * was in typing order. The list is in clock order now, and only two steps at
 * the same minute have an order the user can change by hand.
 */
class PlanOrderTest {

    @Test
    fun `a step added last still sits at its time`() {
        val items = listOf(
            item(id = 1, anchor = fixedAt(8), sortOrder = 0),
            item(id = 2, anchor = fixedAt(23, 15), sortOrder = 1),
            item(id = 3, anchor = fixedAt(8, 10), sortOrder = 2),
        )

        assertThat(PlanOrder.sorted(items).map { it.id }).containsExactly(1L, 3L, 2L).inOrder()
    }

    @Test
    fun `a step that comes after another sits after it`() {
        val items = listOf(
            item(id = 1, anchor = fixedAt(9), sortOrder = 5),
            item(id = 2, anchor = relativeTo(1, 20.minutes), sortOrder = 0),
            item(id = 3, anchor = fixedAt(9, 10), sortOrder = 1),
        )

        assertThat(PlanOrder.sorted(items).map { it.id }).containsExactly(1L, 3L, 2L).inOrder()
    }

    @Test
    fun `a window sits at its start`() {
        val items = listOf(
            item(id = 1, anchor = fixedAt(12), sortOrder = 0),
            item(id = 2, anchor = window(11, 13), sortOrder = 1),
        )

        assertThat(PlanOrder.sorted(items).map { it.id }).containsExactly(2L, 1L).inOrder()
    }

    @Test
    fun `two steps at the same minute keep the order the user gave`() {
        val items = listOf(
            item(id = 1, anchor = fixedAt(11), sortOrder = 2),
            item(id = 2, anchor = fixedAt(11), sortOrder = 1),
        )

        assertThat(PlanOrder.sorted(items).map { it.id }).containsExactly(2L, 1L).inOrder()
        assertThat(PlanOrder.slotOf(items[0], items)).isEqualTo(PlanOrder.slotOf(items[1], items))
    }

    @Test
    fun `a step whose parent is missing goes to the bottom with no slot`() {
        val orphan = item(id = 2, anchor = relativeTo(99), sortOrder = 0)
        val items = listOf(orphan, item(id = 1, anchor = fixedAt(22), sortOrder = 1))

        assertThat(PlanOrder.sorted(items).map { it.id }).containsExactly(1L, 2L).inOrder()
        assertThat(PlanOrder.slotOf(orphan, items)).isEqualTo(PlanOrder.NO_SLOT)
    }

    @Test
    fun `a loop of after steps does not hang the list`() {
        val items = listOf(
            item(id = 1, anchor = relativeTo(2), sortOrder = 0),
            item(id = 2, anchor = relativeTo(1), sortOrder = 1),
        )

        assertThat(PlanOrder.sorted(items)).hasSize(2)
    }

    @Test
    fun `a step after one late at night stays in the evening`() {
        val items = listOf(
            item(id = 1, anchor = fixedAt(23, 50), sortOrder = 0),
            item(id = 2, anchor = relativeTo(1, 20.minutes), sortOrder = 1),
            item(id = 3, anchor = fixedAt(6), sortOrder = 2),
        )

        assertThat(PlanOrder.sorted(items).map { it.id }).containsExactly(3L, 1L, 2L).inOrder()
    }
}
