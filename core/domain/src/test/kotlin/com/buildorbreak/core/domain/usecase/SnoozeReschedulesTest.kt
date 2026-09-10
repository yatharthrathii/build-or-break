package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.domain.fake.FakeDayLogRepository
import com.buildorbreak.core.domain.fake.FakeItemRepository
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
import com.buildorbreak.core.model.execution.Occurrence
import com.buildorbreak.core.model.plan.Item
import com.buildorbreak.core.model.resolved.CascadePreview
import com.buildorbreak.core.testing.time.FakeTimeProvider
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneId
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * A snoozed step has to ring again, and the whole app has to agree on when.
 *
 * The failure this exists to prevent was silent and complete. A snooze wrote
 * its ten minutes onto the occurrence, the resolver kept drawing the step at
 * its original time, and the rescheduling pass then dropped it for being in
 * the past. The step never rang again and the screen never said so, which is
 * the worst shape a bug can take in an app somebody trusts to wake them.
 */
class SnoozeReschedulesTest {

    private val plans = FakePlanRepository()
    private val templates = FakeTemplateRepository()
    private val items = FakeItemRepository()
    private val occurrences = FakeOccurrenceRepository()
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

    private fun reschedule() = RescheduleAllUseCase(observeToday, occurrences, alarms, time, dispatchers)

    private val importPlan = ImportPlanUseCase(
        plans = plans,
        templates = templates,
        items = items,
        reschedule = reschedule(),
        time = time,
        dispatchers = dispatchers,
    )

    private val snooze = SnoozeItemUseCase(
        occurrences = occurrences,
        reschedule = reschedule(),
        notifications = SilentNotifications(),
        widget = object : WidgetGateway {
            override suspend fun refresh() = Unit
        },
        dispatchers = dispatchers,
    )

    private suspend fun givenADueStep(): Long {
        importPlan(PlanTextParser().parse("11:31 Stand up | alarm").items, templateName = "Weekday")

        return occurrences.observeForDate(time.today()).first().single().id
    }

    @Test
    fun `a snoozed step moves on the timeline rather than staying where it was`() = runTest {
        val id = givenADueStep()

        snooze(id, 10.minutes)

        val entry = observeToday(time.today()).first()?.entries?.single()
        assertThat(entry?.at?.toLocalTime().toString()).isEqualTo("11:41")
    }

    @Test
    fun `a snoozed step is scheduled again, at the time it was moved to`() = runTest {
        val id = givenADueStep()
        alarms.scheduledFor.clear()

        snooze(id, 10.minutes)

        val set = alarms.scheduledFor.filter { it.first == id }
        assertThat(set).isNotEmpty()
        assertThat(set.last().second.toLocalTime().toString()).isEqualTo("11:41")
    }

    @Test
    fun `snoozing twice keeps moving it, rather than settling on the first move`() = runTest {
        val id = givenADueStep()

        snooze(id, 10.minutes)
        snooze(id, 10.minutes)

        assertThat(observeToday(time.today()).first()?.entries?.single()?.at?.toLocalTime().toString())
            .isEqualTo("11:51")
    }

    /** The gateway is not what this is about, and a real one would need a device. */
    private class SilentNotifications : NotificationGateway {
        override suspend fun show(occurrence: Occurrence, item: Item, preview: CascadePreview?) = Unit

        override suspend fun dismiss(occurrenceId: Long) = Unit

        override suspend fun showMilestone(milestone: Milestone) = Unit

        override fun canPostNotifications(): Boolean = true

        override fun canUseFullScreenIntent(): Boolean = true
    }
}
