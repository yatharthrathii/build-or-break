package com.buildorbreak.core.domain.resolver

import com.buildorbreak.core.model.enums.DayMode
import com.buildorbreak.core.model.enums.Salience
import com.buildorbreak.core.model.execution.Occurrence
import com.buildorbreak.core.model.plan.Anchor
import com.buildorbreak.core.model.plan.Block
import com.buildorbreak.core.model.plan.Item
import com.buildorbreak.core.model.plan.Weekdays
import com.buildorbreak.core.model.resolved.ResolveIssue
import com.buildorbreak.core.model.resolved.ResolvedDay
import com.buildorbreak.core.testing.fixtures.ExecutionFixtures
import com.buildorbreak.core.testing.fixtures.PlanFixtures
import com.buildorbreak.core.testing.fixtures.PlanFixtures.fixedAt
import com.buildorbreak.core.testing.fixtures.PlanFixtures.item
import com.buildorbreak.core.testing.fixtures.PlanFixtures.relativeTo
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import org.junit.jupiter.api.Test

class DefaultTimelineResolverTest {

    private val resolver = DefaultTimelineResolver()

    /** 2026-01-05 is a Monday, so a weekday test has something to include and exclude. */
    private val date = ExecutionFixtures.DATE

    private fun resolve(
        items: List<Item>,
        blocks: List<Block> = emptyList(),
        occurrences: List<Occurrence> = emptyList(),
        dayShift: Duration = Duration.ZERO,
        mode: DayMode = DayMode.NORMAL,
        on: LocalDate = date,
    ): ResolvedDay = resolver.resolve(
        ResolveInput(
            template = PlanFixtures.template(),
            blocks = blocks,
            items = items,
            occurrences = occurrences,
            date = on,
            zone = ExecutionFixtures.ZONE,
            dayShift = dayShift,
            mode = mode,
        ),
    )

    private fun intervalItem(
        id: Long,
        everyMinutes: Long,
        from: LocalTime,
        to: LocalTime,
    ): Item = item(id = id, anchor = Anchor.Interval(everyMinutes.minutes, from, to))

    // 1. Filter --------------------------------------------------------------

    @Test
    fun `an empty plan resolves to an empty day rather than failing`() {
        val day = resolve(items = emptyList())

        assertThat(day.entries).isEmpty()
        assertThat(day.issues).isEmpty()
        assertThat(day.budgetWarning).isNull()
        assertThat(day.total).isEqualTo(0)
    }

    @Test
    fun `an archived item is not part of the day`() {
        val day = resolve(
            listOf(
                item(id = 1, anchor = fixedAt(8)),
                item(id = 2, anchor = fixedAt(9)).copy(archivedAt = java.time.Instant.EPOCH),
            ),
        )

        assertThat(day.entries.map { it.item.id }).containsExactly(1L)
    }

    @Test
    fun `an item that does not run on this weekday is left out`() {
        val day = resolve(
            listOf(
                item(id = 1, anchor = fixedAt(8), weekdays = Weekdays.MonToFri),
                item(id = 2, anchor = fixedAt(9), weekdays = Weekdays.Weekend),
            ),
        )

        assertThat(day.entries.map { it.item.id }).containsExactly(1L)
    }

    // 2. Shift ---------------------------------------------------------------

    @Test
    fun `a day shift moves every unpinned item`() {
        val day = resolve(listOf(item(id = 1, anchor = fixedAt(8))), dayShift = 90.minutes)

        assertThat(day.entries.single().at).isEqualTo(date.atTime(9, 30))
    }

    @Test
    fun `a pinned item stays where it is when the day moves`() {
        val day = resolve(
            items = listOf(
                item(id = 1, anchor = fixedAt(8)),
                item(id = 2, anchor = fixedAt(18), pinned = true),
            ),
            dayShift = 90.minutes,
        )

        assertThat(day.entryFor(1)?.at).isEqualTo(date.atTime(9, 30))
        assertThat(day.entryFor(2)?.at).isEqualTo(date.atTime(18, 0))
    }

    @Test
    fun `a relative child follows its parent through a shift exactly once`() {
        val day = resolve(
            items = listOf(
                item(id = 1, anchor = fixedAt(8)),
                item(id = 2, anchor = relativeTo(1, 30.minutes)),
            ),
            dayShift = 90.minutes,
        )

        // 08:00 + 90 = 09:30, and the child is still exactly thirty past it.
        assertThat(day.entryFor(1)?.at).isEqualTo(date.atTime(9, 30))
        assertThat(day.entryFor(2)?.at).isEqualTo(date.atTime(10, 0))
    }

    @Test
    fun `a shift that runs off the end of the day clamps and says so`() {
        val day = resolve(listOf(item(id = 1, anchor = fixedAt(23, 0))), dayShift = 120.minutes)

        assertThat(day.entries.single().at).isEqualTo(date.atTime(23, 59))
        assertThat(day.issues).contains(ResolveIssue.ClampedToMidnight(1))
    }

    // 3, 4. Graph and place --------------------------------------------------

    @Test
    fun `a completed parent drags its chain to when it actually finished`() {
        val day = resolve(
            items = listOf(
                item(id = 1, anchor = fixedAt(8)),
                item(id = 2, anchor = relativeTo(1, 30.minutes)),
                item(id = 3, anchor = relativeTo(2, 15.minutes)),
            ),
            occurrences = listOf(ExecutionFixtures.completedAt(1, date.atTime(8, 40))),
        )

        assertThat(day.entryFor(2)?.at).isEqualTo(date.atTime(9, 10))
        assertThat(day.entryFor(3)?.at).isEqualTo(date.atTime(9, 25))
    }

    // 5. Expand --------------------------------------------------------------

    @Test
    fun `an interval item becomes one entry per repeat`() {
        val day = resolve(listOf(intervalItem(1, 45, LocalTime.of(11, 0), LocalTime.of(13, 30))))

        assertThat(day.entries).hasSize(4)
        assertThat(day.entries.map { it.at }).containsExactly(
            date.atTime(11, 0),
            date.atTime(11, 45),
            date.atTime(12, 30),
            date.atTime(13, 15),
        ).inOrder()
        assertThat(day.entries.map { it.sequenceInDay }).containsExactly(0, 1, 2, 3).inOrder()
    }

    // 6. Attach --------------------------------------------------------------

    @Test
    fun `an entry carries its block, and the block decides how loud it is`() {
        val day = resolve(
            items = listOf(item(id = 1, anchor = fixedAt(8), blockId = 10, salience = Salience.SILENT)),
            blocks = listOf(PlanFixtures.block(id = 10, salience = Salience.ALARM)),
        )

        val entry = day.entries.single()
        assertThat(entry.block?.id).isEqualTo(10L)
        assertThat(entry.isInBlock).isTrue()
        assertThat(entry.salience).isEqualTo(Salience.ALARM)
    }

    @Test
    fun `an occurrence is matched to the repeat it belongs to, not to the first one`() {
        val third = ExecutionFixtures.occurrence(itemId = 1, id = 99, sequenceInDay = 2)
        val day = resolve(
            items = listOf(intervalItem(1, 45, LocalTime.of(11, 0), LocalTime.of(13, 30))),
            occurrences = listOf(third),
        )

        assertThat(day.entries.map { it.occurrence?.id }).containsExactly(null, null, 99L, null).inOrder()
    }

    // 7. Sort ----------------------------------------------------------------

    @Test
    fun `entries come back in time order regardless of how the plan was written`() {
        val day = resolve(
            listOf(
                item(id = 1, anchor = fixedAt(18)),
                item(id = 2, anchor = fixedAt(7)),
                item(id = 3, anchor = fixedAt(12)),
            ),
        )

        assertThat(day.entries.map { it.item.id }).containsExactly(2L, 3L, 1L).inOrder()
    }

    @Test
    fun `two items at the same minute fall back to the order the user gave them`() {
        val day = resolve(
            listOf(
                item(id = 1, anchor = fixedAt(8), sortOrder = 2),
                item(id = 2, anchor = fixedAt(8), sortOrder = 1),
            ),
        )

        assertThat(day.entries.map { it.item.id }).containsExactly(2L, 1L).inOrder()
    }

    // 8. Budget --------------------------------------------------------------

    @Test
    fun `a day inside the budget carries no warning`() {
        val day = resolve(
            listOf(
                item(id = 1, anchor = fixedAt(7), salience = Salience.ALARM),
                item(id = 2, anchor = fixedAt(8), salience = Salience.ALARM),
                item(id = 3, anchor = fixedAt(9), salience = Salience.NOTIFY),
            ),
        )

        assertThat(day.budgetWarning).isNull()
    }

    @Test
    fun `a fourth alarm in one day trips the budget warning`() {
        val day = resolve((1L..4L).map { item(id = it, anchor = fixedAt(6 + it.toInt()), salience = Salience.ALARM) })

        assertThat(day.budgetWarning).isNotNull()
        assertThat(day.budgetWarning?.alarmCount).isEqualTo(4)
    }

    @Test
    fun `an interval item is counted per repeat, because that is what the user hears`() {
        // Eleven repeats of a NOTIFY item is eleven notifications, over the ten cap.
        val day = resolve(listOf(intervalItem(1, 30, LocalTime.of(9, 0), LocalTime.of(14, 0))))

        assertThat(day.entries).hasSize(11)
        assertThat(day.budgetWarning?.notifyCount).isEqualTo(11)
    }

    @Test
    fun `timeline items are never counted against the budget`() {
        val day = resolve((1L..20L).map { item(id = it, anchor = fixedAt(8), salience = Salience.TIMELINE) })

        assertThat(day.entries).hasSize(20)
        assertThat(day.budgetWarning).isNull()
    }

    // 9. Issues --------------------------------------------------------------

    @Test
    fun `a cycle is reported and the day still renders every item`() {
        val day = resolve(
            listOf(
                item(id = 1, anchor = relativeTo(2, 10.minutes)),
                item(id = 2, anchor = relativeTo(1, 10.minutes)),
            ),
        )

        assertThat(day.entries).hasSize(2)
        assertThat(day.issues.filterIsInstance<ResolveIssue.AnchorCycle>()).hasSize(1)
        assertThat(day.entryFor(2)?.degraded).isTrue()
    }

    @Test
    fun `a missing parent is reported with the parent it was looking for`() {
        val day = resolve(
            listOf(
                item(id = 1, anchor = fixedAt(8)),
                item(id = 2, anchor = relativeTo(parentItemId = 99, offset = 20.minutes)),
            ),
        )

        assertThat(day.issues).contains(ResolveIssue.MissingParent(itemId = 2, parentItemId = 99))
        assertThat(day.entries).hasSize(2)
    }

    // The property the whole design rests on ---------------------------------

    @Test
    fun `resolving the same input twice produces the same day`() {
        val items = listOf(
            item(id = 1, anchor = fixedAt(8)),
            item(id = 2, anchor = relativeTo(1, 30.minutes)),
            intervalItem(3, 45, LocalTime.of(11, 0), LocalTime.of(13, 30)),
            item(id = 4, anchor = fixedAt(18), pinned = true),
        )

        assertThat(resolve(items, dayShift = 45.minutes)).isEqualTo(resolve(items, dayShift = 45.minutes))
    }

    @Test
    fun `the day carries back the mode and shift it was resolved with`() {
        val day = resolve(listOf(item(id = 1)), dayShift = 90.minutes, mode = DayMode.SHIFTED)

        assertThat(day.mode).isEqualTo(DayMode.SHIFTED)
        assertThat(day.dayShift).isEqualTo(90.minutes)
        assertThat(day.date).isEqualTo(date)
    }
}
