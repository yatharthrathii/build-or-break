package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.domain.fake.FakeDayLogRepository
import com.buildorbreak.core.domain.fake.FakeItemRepository
import com.buildorbreak.core.domain.fake.FakeMeasurementRepository
import com.buildorbreak.core.domain.fake.FakeOccurrenceRepository
import com.buildorbreak.core.domain.fake.FakePlanRepository
import com.buildorbreak.core.domain.fake.FakeSettingsRepository
import com.buildorbreak.core.domain.fake.FakeTemplateRepository
import com.buildorbreak.core.domain.fake.RecordingAlarmGateway
import com.buildorbreak.core.domain.gateway.NotificationGateway
import com.buildorbreak.core.domain.gateway.WidgetGateway
import com.buildorbreak.core.domain.parse.PlanTextParser
import com.buildorbreak.core.domain.resolver.DefaultTimelineResolver
import com.buildorbreak.core.model.enums.Milestone
import com.buildorbreak.core.model.enums.OccurrenceState
import com.buildorbreak.core.model.enums.SkipChip
import com.buildorbreak.core.model.enums.ValueKind
import com.buildorbreak.core.model.execution.Measurement
import com.buildorbreak.core.model.execution.Occurrence
import com.buildorbreak.core.model.plan.Item
import com.buildorbreak.core.model.resolved.CascadePreview
import com.buildorbreak.core.testing.time.FakeTimeProvider
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Taking a settle back, and asking about one that arrived from a notification.
 *
 * Two halves of the same problem. Almost every interaction this app is designed
 * for happens in the notification shade, which means the two things it cannot
 * do there are correct a mistake and ask a question. Both have to be recoverable
 * later or the history quietly stops matching what happened, and every figure
 * the app shows is built on that history.
 */
class UndoAndExplainTest {

    private val plans = FakePlanRepository()
    private val templates = FakeTemplateRepository()
    private val items = FakeItemRepository()
    private val occurrences = FakeOccurrenceRepository()
    private val measurements = FakeMeasurementRepository()
    private val dayLogs = FakeDayLogRepository()
    private val alarms = RecordingAlarmGateway()

    // 06:00 UTC is 11:30 in Kolkata, a minute before the step below is due.
    private val time = FakeTimeProvider(
        initial = Instant.parse("2026-01-05T06:00:00Z"),
        currentZone = ZoneId.of("Asia/Kolkata"),
    )

    private val dispatchers = object : AppDispatchers {
        override val default = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
        override val main = Dispatchers.Unconfined
    }

    private val observeToday = ObserveTodayUseCase(
        plans = plans,
        templates = templates,
        items = items,
        occurrences = occurrences,
        dayLogs = dayLogs,
        settings = FakeSettingsRepository(),
        resolver = DefaultTimelineResolver(),
        time = time,
        dispatchers = dispatchers,
    )

    private val reschedule = RescheduleAllUseCase(observeToday, occurrences, alarms, time, dispatchers)

    private val silentWidget = object : WidgetGateway {
        override suspend fun refresh() = Unit
    }

    private val importPlan = ImportPlanUseCase(
        plans = plans,
        templates = templates,
        items = items,
        reschedule = reschedule,
        time = time,
        dispatchers = dispatchers,
    )

    private val skip = SkipItemUseCase(
        occurrences = occurrences,
        measurements = measurements,
        reschedule = reschedule,
        notifications = SilentNotifications(),
        alarms = alarms,
        widget = silentWidget,
        time = time,
        dispatchers = dispatchers,
    )

    private val complete = CompleteItemUseCase(
        occurrences = occurrences,
        reschedule = reschedule,
        notifications = SilentNotifications(),
        alarms = alarms,
        widget = silentWidget,
        time = time,
        dispatchers = dispatchers,
    )

    private val undo = UndoSettleUseCase(occurrences, measurements, reschedule, silentWidget, dispatchers)

    private val unexplained =
        ObserveUnexplainedSkipsUseCase(occurrences, measurements, items, time, dispatchers)

    private val explain = ExplainSkipUseCase(measurements, time)

    private suspend fun givenTwoSteps(): List<Long> {
        importPlan(
            PlanTextParser().parse("11:31 Stand up | alarm\n12:30 Lunch").items,
            templateName = "Weekday",
        )

        return occurrences.observeForDate(time.today()).first().map { it.id }
    }

    private suspend fun stateOf(id: Long) = occurrences.observeForDate(time.today()).first().first { it.id == id }.state

    // Undo --------------------------------------------------------------------

    @Test
    fun `a completion can be taken back`() = runTest {
        val id = givenTwoSteps().first()
        complete(id)
        assertThat(stateOf(id)).isEqualTo(OccurrenceState.DONE)

        undo(id)

        assertThat(stateOf(id)).isEqualTo(OccurrenceState.PENDING)
    }

    @Test
    fun `undoing a completion clears the time it claims to have been done at`() = runTest {
        val id = givenTwoSteps().first()
        complete(id)

        undo(id)

        val row = occurrences.observeForDate(time.today()).first().first { it.id == id }
        assertThat(row.settledAt).isNull()
    }

    /**
     * The reason has to go with the skip.
     *
     * Left behind, it would be counted by the weekly review, and the report
     * would explain a skip that no longer exists.
     */
    @Test
    fun `undoing a skip takes its reason with it`() = runTest {
        val id = givenTwoSteps().first()
        skip(id, reasonFor(id, SkipChip.WORK_CAME_UP))
        assertThat(measurements.skipReasonsFor(listOf(id))).isNotEmpty()

        undo(id)

        assertThat(measurements.skipReasonsFor(listOf(id))).isEmpty()
    }

    @Test
    fun `an undo leaves every other step alone`() = runTest {
        val (first, second) = givenTwoSteps()
        complete(first)
        complete(second)

        undo(first)

        assertThat(stateOf(second)).isEqualTo(OccurrenceState.DONE)
    }

    // The deferred question ---------------------------------------------------

    @Test
    fun `a skip made outside the app is one the app still owes a question about`() = runTest {
        val id = givenTwoSteps().first()

        skip(id, reason = null)

        assertThat(unexplained().first().map { it.occurrenceId }).containsExactly(id)
    }

    @Test
    fun `the question carries the name of the step, so the prompt can say what it is about`() = runTest {
        val id = givenTwoSteps().first()
        skip(id, reason = null)

        assertThat(unexplained().first().single().title).isEqualTo("Stand up")
    }

    @Test
    fun `a skip that was already explained is not asked about again`() = runTest {
        val id = givenTwoSteps().first()

        skip(id, reasonFor(id, SkipChip.FORGOT))

        assertThat(unexplained().first()).isEmpty()
    }

    /**
     * The refusal counts as an answer.
     *
     * "Skip without a reason" writes an empty row rather than nothing, and this
     * is what that row is for: an app that asks a second time about the same
     * bad morning is one people stop answering at all.
     */
    @Test
    fun `declining to give a reason still counts as having been asked`() = runTest {
        val id = givenTwoSteps().first()
        skip(id, reasonFor(id, chip = null))

        assertThat(unexplained().first()).isEmpty()
    }

    @Test
    fun `answering the deferred question records the reason`() = runTest {
        val id = givenTwoSteps().first()
        skip(id, reason = null)

        explain(id, SkipChip.NO_TIME)

        assertThat(measurements.skipReasonsFor(listOf(id)).single().chip).isEqualTo(SkipChip.NO_TIME)
        assertThat(unexplained().first()).isEmpty()
    }

    @Test
    fun `a step that was completed is never asked about`() = runTest {
        val id = givenTwoSteps().first()

        complete(id)

        assertThat(unexplained().first()).isEmpty()
    }

    private fun reasonFor(occurrenceId: Long, chip: SkipChip?) = com.buildorbreak.core.model.execution.SkipReason(
        id = 0,
        occurrenceId = occurrenceId,
        chip = chip,
        text = null,
        createdAt = time.now(),
    )

    /** The gateway is not what this is about, and a real one would need a device. */
    private class SilentNotifications : NotificationGateway {
        override suspend fun show(occurrence: Occurrence, item: Item, preview: CascadePreview?) = Unit

        override suspend fun dismiss(occurrenceId: Long) = Unit

        override suspend fun showMilestone(milestone: Milestone) = Unit

        override fun canPostNotifications(): Boolean = true

        override fun canUseFullScreenIntent(): Boolean = true
    }

    /**
     * The number goes with the settle it was logged against.
     *
     * Left behind, sixty minutes of study kept counting toward a duration
     * goal for a step that was later closed as missed.
     */
    @Test
    fun `undoing a completion takes the number logged with it`() = runTest {
        val id = givenTwoSteps().first()
        complete(id)
        measurements.upsert(
            Measurement(
                id = 0,
                itemId = occurrences.byId(id)!!.itemId,
                occurrenceId = id,
                date = time.today(),
                value = 60.0,
                kind = ValueKind.MINUTES,
            ),
        )

        undo(id)

        assertThat(measurements.measurements.value.filter { it.occurrenceId == id }).isEmpty()
    }
}
