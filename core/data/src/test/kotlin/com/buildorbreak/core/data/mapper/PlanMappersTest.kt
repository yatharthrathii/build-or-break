package com.buildorbreak.core.data.mapper

import com.buildorbreak.core.data.entity.AnchorColumns
import com.buildorbreak.core.model.enums.Salience
import com.buildorbreak.core.model.plan.Anchor
import com.buildorbreak.core.model.plan.Weekdays
import com.buildorbreak.core.testing.fixtures.PlanFixtures
import com.buildorbreak.core.testing.fixtures.PlanFixtures.fixedAt
import com.buildorbreak.core.testing.fixtures.PlanFixtures.item
import com.buildorbreak.core.testing.fixtures.PlanFixtures.relativeTo
import com.google.common.truth.Truth.assertThat
import java.time.LocalTime
import kotlin.time.Duration.Companion.minutes
import org.junit.Test

/**
 * Mapping is where a schema change turns into a wrong plan, so every anchor
 * shape goes out and comes back, and every fallback has a test of its own. A
 * round trip that loses a field is a user losing part of their routine on the
 * next launch.
 */
class PlanMappersTest {

    @Test
    fun `a fixed anchor survives a round trip`() {
        val anchor = fixedAt(6, 30)

        assertThat(anchor.toColumns().toModel()).isEqualTo(anchor)
    }

    @Test
    fun `a relative anchor keeps its parent and its offset`() {
        val anchor = relativeTo(parentItemId = 7, offset = 45.minutes)

        assertThat(anchor.toColumns().toModel()).isEqualTo(anchor)
    }

    @Test
    fun `a window keeps its nag ladder in order`() {
        val anchor = Anchor.Window(
            from = LocalTime.of(17, 30),
            to = LocalTime.of(19, 30),
            nagLadder = listOf(30.minutes, 60.minutes, 90.minutes),
        )

        assertThat(anchor.toColumns().toModel()).isEqualTo(anchor)
    }

    @Test
    fun `a window with no ladder comes back with no ladder, not with an empty string`() {
        val anchor = Anchor.Window(LocalTime.of(11, 0), LocalTime.of(14, 0))

        val stored = anchor.toColumns()

        assertThat(stored.nagLadder).isNull()
        assertThat(stored.toModel()).isEqualTo(anchor)
    }

    @Test
    fun `an interval keeps its step and its window`() {
        val anchor = Anchor.Interval(45.minutes, LocalTime.of(11, 0), LocalTime.of(15, 0))

        assertThat(anchor.toColumns().toModel()).isEqualTo(anchor)
    }

    @Test
    fun `a whole item survives a round trip with everything on it`() {
        val original = item(
            id = 3,
            title = "Study block",
            anchor = Anchor.Window(LocalTime.of(7, 30), LocalTime.of(9, 30), listOf(30.minutes)),
            blockId = 10,
            detail = "the hard one first",
            duration = 60.minutes,
            salience = Salience.ALARM,
            weekdays = Weekdays.MonToFri,
            pinned = true,
            minimum = PlanFixtures.minimum("Fifteen minutes", 15.minutes),
            sortOrder = 4,
        )

        assertThat(original.toEntity().toModel()).isEqualTo(original)
    }

    @Test
    fun `an archived item stays archived`() {
        val archived = item(id = 1).copy(archivedAt = java.time.Instant.EPOCH)

        assertThat(archived.toEntity().toModel().isArchived).isTrue()
    }

    // What happens to a row that is not what this build expects ----------------

    @Test
    fun `an unreadable salience reads as silent rather than as an alarm`() {
        val stored = item(id = 1).toEntity().copy(salience = "SHOUTING")

        // Waking somebody at six because a column could not be read is the worse
        // of the two failures available here.
        assertThat(stored.toModel().salience).isEqualTo(Salience.SILENT)
    }

    @Test
    fun `an unreadable anchor type falls back to a fixed time and does not throw`() {
        val stored = AnchorColumns(type = "SOMETHING_NEW", at = LocalTime.of(8, 0))

        assertThat(stored.toModel()).isEqualTo(Anchor.Fixed(LocalTime.of(8, 0)))
    }

    @Test
    fun `a fixed anchor with no time reads as midnight rather than crashing`() {
        assertThat(AnchorColumns(type = "FIXED").toModel()).isEqualTo(Anchor.Fixed(LocalTime.MIDNIGHT))
    }

    @Test
    fun `a relative anchor with no parent points at nothing that exists`() {
        val rebuilt = AnchorColumns(type = "RELATIVE").toModel() as Anchor.Relative

        // The anchor graph reports this as a missing parent and the day still
        // resolves, which is what a dangling row should look like.
        assertThat(rebuilt.parentItemId).isLessThan(0L)
    }

    @Test
    fun `a weekday mask with junk in the high bits is trimmed to seven days`() {
        val stored = item(id = 1).toEntity().copy(weekdays = -1)

        assertThat(stored.toModel().weekdays).isEqualTo(Weekdays.EveryDay)
    }

    @Test
    fun `a nag ladder with an unreadable entry keeps the readable ones`() {
        val stored = AnchorColumns(
            type = "WINDOW",
            from = LocalTime.of(11, 0),
            to = LocalTime.of(14, 0),
            nagLadder = "30,oops,60",
        )

        assertThat((stored.toModel() as Anchor.Window).nagLadder).containsExactly(30.minutes, 60.minutes).inOrder()
    }
}
