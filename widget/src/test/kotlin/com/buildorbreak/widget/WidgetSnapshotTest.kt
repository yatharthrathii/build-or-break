package com.buildorbreak.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.buildorbreak.core.model.enums.DayMode
import com.buildorbreak.core.model.execution.Occurrence
import com.buildorbreak.core.model.plan.Item
import com.buildorbreak.core.model.resolved.ResolvedDay
import com.buildorbreak.core.model.resolved.ResolvedEntry
import com.buildorbreak.core.testing.fixtures.ExecutionFixtures
import com.buildorbreak.core.testing.fixtures.PlanFixtures
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import kotlin.time.Duration
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val DATE: LocalDate = ExecutionFixtures.DATE

/**
 * What the home screen widget says about a day.
 *
 * The widget is the app for anybody who put it on their home screen: most
 * days they never open anything else. It makes two decisions, which step is
 * next and what to say when none is, and both were untested.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class WidgetSnapshotTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun entry(
        item: Item,
        hour: Int,
        occurrence: Occurrence? = null,
        reduced: Boolean = false,
    ) = ResolvedEntry(
        item = item,
        block = null,
        at = DATE.atTime(hour, 0),
        occurrence = occurrence,
        reduced = reduced,
    )

    private fun day(vararg entries: ResolvedEntry) = ResolvedDay(
        date = DATE,
        template = PlanFixtures.template(),
        entries = entries.toList(),
        dayShift = Duration.ZERO,
        mode = DayMode.NORMAL,
        budgetWarning = null,
        issues = emptyList(),
    )

    private val wake = PlanFixtures.item(id = 1, title = "Wake up")
    private val gym = PlanFixtures.item(id = 2, title = "Gym")

    @Test
    fun `the next step is the first one that has not been settled`() {
        val snapshot = WidgetData.snapshotOf(
            context,
            day(entry(wake, hour = 6, occurrence = ExecutionFixtures.done(itemId = 1)), entry(gym, hour = 7)),
        )

        assertThat(snapshot.title).isEqualTo("Gym")
        assertThat(snapshot.count).isEqualTo("1 / 2")
    }

    @Test
    fun `a skipped step is settled too, so the widget moves past it`() {
        val snapshot = WidgetData.snapshotOf(
            context,
            day(entry(wake, hour = 6, occurrence = ExecutionFixtures.skipped(itemId = 1)), entry(gym, hour = 7)),
        )

        assertThat(snapshot.title).isEqualTo("Gym")
        // Skipped is not done. The count says what was kept, not what was dealt with.
        assertThat(snapshot.count).isEqualTo("0 / 2")
    }

    @Test
    fun `the done button carries the occurrence it would settle`() {
        val pending = ExecutionFixtures.occurrence(itemId = 2, id = 42)

        val snapshot = WidgetData.snapshotOf(context, day(entry(gym, hour = 7, occurrence = pending)))

        assertThat(snapshot.occurrenceId).isEqualTo(42)
    }

    @Test
    fun `a step with no row yet has no id, and the buttons know not to act on it`() {
        val snapshot = WidgetData.snapshotOf(context, day(entry(gym, hour = 7)))

        assertThat(snapshot.occurrenceId).isEqualTo(WidgetData.NO_ID)
    }

    @Test
    fun `on a reduced day the widget asks for the smaller version`() {
        val walk = PlanFixtures.item(
            id = 3,
            title = "Run 5 km",
            minimum = PlanFixtures.minimum(title = "Walk ten minutes"),
        )

        val snapshot = WidgetData.snapshotOf(context, day(entry(walk, hour = 18, reduced = true)))

        assertThat(snapshot.title).isEqualTo("Walk ten minutes")
    }

    @Test
    fun `an ordinary day asks for the full step even when a smaller one exists`() {
        val walk = PlanFixtures.item(
            id = 3,
            title = "Run 5 km",
            minimum = PlanFixtures.minimum(title = "Walk ten minutes"),
        )

        val snapshot = WidgetData.snapshotOf(context, day(entry(walk, hour = 18)))

        assertThat(snapshot.title).isEqualTo("Run 5 km")
    }

    @Test
    fun `when everything is settled there is no next step, and it says the day is done`() {
        val snapshot = WidgetData.snapshotOf(
            context,
            day(entry(wake, hour = 6, occurrence = ExecutionFixtures.done(itemId = 1))),
        )

        assertThat(snapshot.title).isNull()
        assertThat(snapshot.time).isNull()
        assertThat(snapshot.emptyLine).isEqualTo("Nothing left on the rails.")
    }

    @Test
    fun `with no plan at all it says so, rather than claiming the day is done`() {
        val snapshot = WidgetData.snapshotOf(context, day = null)

        assertThat(snapshot.title).isNull()
        assertThat(snapshot.count).isEqualTo("0 / 0")
        assertThat(snapshot.emptyLine).isEqualTo("No plan yet. Open the app to add one.")
    }
}
