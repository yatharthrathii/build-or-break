package com.buildorbreak.core.domain.resolver

import com.buildorbreak.core.model.enums.DayMode
import com.buildorbreak.core.model.enums.Salience
import com.buildorbreak.core.model.plan.Block
import com.buildorbreak.core.model.plan.Item
import com.buildorbreak.core.model.resolved.ResolvedDay
import com.buildorbreak.core.testing.fixtures.ExecutionFixtures
import com.buildorbreak.core.testing.fixtures.PlanFixtures
import com.buildorbreak.core.testing.fixtures.PlanFixtures.block
import com.buildorbreak.core.testing.fixtures.PlanFixtures.fixedAt
import com.buildorbreak.core.testing.fixtures.PlanFixtures.item
import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import kotlin.time.Duration
import org.junit.jupiter.api.Test

private const val BLOCK_ID = 9L

/**
 * A group is one interruption, not five.
 *
 * rules.md section 1 rule 4. Five things between 08:00 and 08:30 delivered as
 * five alarms is how an app gets muted inside a week, and the resolver is where
 * that is decided so that the budget, the scheduler and the catch up planner
 * all read the same answer.
 */
class BlockSalienceTest {

    private val resolver = DefaultTimelineResolver()

    private fun resolve(
        items: List<Item>,
        blocks: List<Block> = emptyList(),
        startedAt: LocalDateTime? = null,
    ): ResolvedDay = resolver.resolve(
        ResolveInput(
            template = PlanFixtures.template(),
            blocks = blocks,
            items = items,
            occurrences = emptyList(),
            date = ExecutionFixtures.DATE,
            zone = ExecutionFixtures.ZONE,
            dayShift = Duration.ZERO,
            mode = DayMode.NORMAL,
            startedAt = startedAt,
        ),
    )

    private fun grouped(id: Long, hour: Int, minute: Int = 0) =
        item(id = id, anchor = fixedAt(hour, minute), blockId = BLOCK_ID, salience = Salience.SILENT)

    @Test
    fun `a step outside any group keeps its own loudness`() {
        val day = resolve(listOf(item(id = 1, salience = Salience.ALARM)))

        assertThat(day.entries.single().salience).isEqualTo(Salience.ALARM)
        assertThat(day.entries.single().isBlockLead).isTrue()
    }

    @Test
    fun `the first step of a group speaks for the group`() {
        val day = resolve(
            items = listOf(grouped(1, 8), grouped(2, 8, 10)),
            blocks = listOf(block(id = BLOCK_ID, salience = Salience.ALARM)),
        )

        assertThat(day.entries.first { it.item.id == 1L }.salience).isEqualTo(Salience.ALARM)
    }

    @Test
    fun `every step after the first one in a group runs silent`() {
        val day = resolve(
            items = listOf(grouped(1, 8), grouped(2, 8, 10), grouped(3, 8, 20)),
            blocks = listOf(block(id = BLOCK_ID, salience = Salience.ALARM)),
        )

        val followers = day.entries.filter { it.item.id != 1L }
        assertThat(followers.map { it.salience }).containsExactly(Salience.SILENT, Salience.SILENT)
    }

    @Test
    fun `the earliest step leads, whatever order the plan lists them in`() {
        val day = resolve(
            items = listOf(grouped(1, 9), grouped(2, 8)),
            blocks = listOf(block(id = BLOCK_ID, salience = Salience.NOTIFY)),
        )

        assertThat(day.entries.first { it.item.id == 2L }.isBlockLead).isTrue()
        assertThat(day.entries.first { it.item.id == 1L }.isBlockLead).isFalse()
    }

    @Test
    fun `two steps at the same minute cannot swap the loud one between resolves`() {
        val first = resolve(
            items = listOf(grouped(2, 8), grouped(1, 8)),
            blocks = listOf(block(id = BLOCK_ID, salience = Salience.NOTIFY)),
        )
        val second = resolve(
            items = listOf(grouped(1, 8), grouped(2, 8)),
            blocks = listOf(block(id = BLOCK_ID, salience = Salience.NOTIFY)),
        )

        assertThat(first.entries.single { it.isBlockLead }.item.id).isEqualTo(1L)
        assertThat(second.entries.single { it.isBlockLead }.item.id).isEqualTo(1L)
    }

    @Test
    fun `a group of five costs the day one alarm, not five`() {
        val day = resolve(
            items = (1L..5L).map { grouped(it, 8, (it * 5).toInt()) },
            blocks = listOf(block(id = BLOCK_ID, salience = Salience.ALARM)),
        )

        assertThat(day.entries.count { it.salience == Salience.ALARM }).isEqualTo(1)
    }

    @Test
    fun `a silenced step is still on the day, because losing one is far worse`() {
        val day = resolve(
            items = listOf(grouped(1, 8), grouped(2, 8, 10)),
            blocks = listOf(block(id = BLOCK_ID, salience = Salience.ALARM)),
        )

        assertThat(day.entries).hasSize(2)
        assertThat(day.entries.none { it.salience == Salience.TIMELINE }).isTrue()
    }

    @Test
    fun `a group whose row has gone leaves its steps as ordinary steps`() {
        // The block id is a plain column with no foreign key behind it, so a
        // dangling reference has to degrade rather than blank the day.
        val day = resolve(items = listOf(grouped(1, 8), grouped(2, 9)), blocks = emptyList())

        assertThat(day.entries.map { it.salience }).containsExactly(Salience.SILENT, Salience.SILENT)
        assertThat(day.entries.all { it.block == null }).isTrue()
    }

    /**
     * On the day a plan begins, the steps before the moment it began are not
     * on today. A group whose first step was one of them still has to speak
     * through somebody, or the whole group runs silent on its first day.
     */
    @Test
    fun `a group whose first step fell before the plan began still has a lead today`() {
        val day = resolve(
            items = listOf(grouped(1, 8), grouped(2, 8, 10), grouped(3, 8, 20)),
            blocks = listOf(block(id = BLOCK_ID, salience = Salience.ALARM)),
            startedAt = ExecutionFixtures.DATE.atTime(8, 5),
        )

        assertThat(day.entries.map { it.item.id }).containsExactly(2L, 3L).inOrder()
        assertThat(day.entries.first { it.item.id == 2L }.salience).isEqualTo(Salience.ALARM)
        assertThat(day.entries.first { it.item.id == 3L }.salience).isEqualTo(Salience.SILENT)
    }
}
