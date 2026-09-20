package com.buildorbreak.core.domain.review

import com.buildorbreak.core.model.enums.DayMode
import com.buildorbreak.core.model.enums.OccurrenceState
import com.buildorbreak.core.model.enums.Salience
import com.buildorbreak.core.model.plan.Item
import com.buildorbreak.core.model.resolved.ResolvedDay
import com.buildorbreak.core.model.resolved.ResolvedEntry
import com.buildorbreak.core.testing.fixtures.ExecutionFixtures
import com.buildorbreak.core.testing.fixtures.ExecutionFixtures.occurrence
import com.buildorbreak.core.testing.fixtures.PlanFixtures
import com.buildorbreak.core.testing.fixtures.PlanFixtures.item
import com.buildorbreak.core.testing.fixtures.PlanFixtures.minimum
import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import org.junit.jupiter.api.Test

/** Late enough that the morning has gone, early enough that the evening has not. */
private val NOW: LocalDateTime = ExecutionFixtures.DATE.atTime(18, 0)

class CatchUpViewBuilderTest {

    private val builder = CatchUpViewBuilder()

    private fun day(vararg entries: ResolvedEntry) = ResolvedDay(
        date = ExecutionFixtures.DATE,
        template = PlanFixtures.template(),
        entries = entries.toList(),
        dayShift = Duration.ZERO,
        mode = DayMode.NORMAL,
        budgetWarning = null,
        issues = emptyList(),
    )

    private fun entry(
        id: Long,
        hour: Int,
        settled: Boolean = false,
        item: Item = item(id = id, duration = 20.minutes),
    ) = ResolvedEntry(
        item = item,
        block = null,
        at = ExecutionFixtures.DATE.atTime(hour, 0),
        occurrence = occurrence(
            itemId = id,
            id = id,
            state = if (settled) OccurrenceState.MISSED else OccurrenceState.PENDING,
        ),
    )

    @Test
    fun `an on time day produces no panel at all`() {
        // Everything either settled or still ahead. A recovery plan on a good
        // morning tells somebody they are behind when they are not.
        val view = builder.build(day(entry(1, 8, settled = true), entry(2, 21)), NOW)

        assertThat(view).isNull()
    }

    @Test
    fun `a missed step comes back with a time it could still take`() {
        val view = builder.build(day(entry(1, 9)), NOW)

        assertThat(view!!.steps).hasSize(1)
        assertThat(view.steps.first().title).isEqualTo("Item 1")
        assertThat(view.steps.first().at).isEqualTo(NOW)
        assertThat(view.steps.first().minutes).isEqualTo(20)
    }

    @Test
    fun `the step already on the card is not offered a second time`() {
        // Today shows the first open step on its own card with its own Done
        // button. Repeating it below reads as the app losing count of the day.
        val view = builder.build(day(entry(1, 9), entry(2, 10)), NOW, excludingOccurrenceId = 1)

        assertThat(view!!.steps.map { it.itemId }).containsExactly(2L)
    }

    @Test
    fun `steps are laid out one after another rather than all at once`() {
        val view = builder.build(day(entry(1, 9), entry(2, 10)), NOW)

        val times = view!!.steps.map { it.at }
        assertThat(times[1]).isGreaterThan(times[0])
    }

    @Test
    fun `what will not fit is named, not counted`() {
        // Two hours of reading with four hours of evening left, twice over.
        val long = item(id = 1, title = "Long read", duration = 200.minutes)
        val alsoLong = item(id = 2, title = "Longer read", duration = 200.minutes)

        val view = builder.build(day(entry(1, 9, item = long), entry(2, 10, item = alsoLong)), NOW)

        assertThat(view!!.outOfTime).containsExactly("Longer read")
    }

    @Test
    fun `the smaller version is offered when only the smaller version fits`() {
        val big = item(id = 1, title = "Deep work", duration = 300.minutes, minimum = minimum("Fifteen minutes"))

        val view = builder.build(day(entry(1, 9, item = big)), NOW)

        assertThat(view!!.steps.first().useMinimum).isTrue()
        assertThat(view.steps.first().title).isEqualTo("Fifteen minutes")
    }

    @Test
    fun `a pinned step is never proposed at a new time`() {
        val pinned = item(id = 1, title = "Class", duration = 20.minutes, pinned = true)

        val view = builder.build(day(entry(1, 9, item = pinned)), NOW)

        assertThat(view).isNull()
    }

    @Test
    fun `a step that only makes sense at its own hour is left alone`() {
        val wake = item(id = 1, title = "Wake up", duration = 5.minutes).copy(catchable = false)

        val view = builder.build(day(entry(1, 6, item = wake)), NOW)

        assertThat(view).isNull()
    }

    @Test
    fun `a timeline note is not something to catch up on`() {
        val note = item(id = 1, title = "Bin day", salience = Salience.TIMELINE)

        val view = builder.build(day(entry(1, 9, item = note)), NOW)

        assertThat(view).isNull()
    }

    @Test
    fun `a fourth missed step is left over, not declared impossible`() {
        // Three is the limit on what is offered, not on what the evening can
        // hold. Saying there is no room at six in the evening for a twenty
        // minute step would be the app wrong in the one place it is asking
        // to be believed.
        val view = builder.build(day(entry(1, 9), entry(2, 10), entry(3, 11), entry(4, 12)), NOW)

        assertThat(view!!.steps).hasSize(3)
        assertThat(view.outOfTime).isEmpty()
        assertThat(view.alsoMissed).containsExactly("Item 4")
    }

    @Test
    fun `late at night everything left over is named as out of time`() {
        val view = builder.build(day(entry(1, 9), entry(2, 10)), ExecutionFixtures.DATE.atTime(23, 30))

        assertThat(view!!.steps).isEmpty()
        assertThat(view.outOfTime).containsExactly("Item 1", "Item 2")
    }
}
