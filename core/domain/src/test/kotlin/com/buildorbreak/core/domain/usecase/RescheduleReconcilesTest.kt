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
import com.buildorbreak.core.model.enums.DayMode
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
 * The alarm is set from the plan as it is now, not from the plan as it was.
 *
 * An occurrence row is written the first time a step is seen, with the time
 * the resolver gave it then. Everything that moves a step afterwards moves the
 * entry on screen, and for a long time none of it moved the row the alarm was
 * set from. The screen said 09:00 and the phone rang at 07:30. Every test here
 * asserts on what the alarm gateway was actually asked for, because that is
 * the number the user hears.
 */
class RescheduleReconcilesTest {

    private val plans = FakePlanRepository()
    private val templates = FakeTemplateRepository()
    private val items = FakeItemRepository()
    private val occurrences = FakeOccurrenceRepository()
    private val dayLogs = FakeDayLogRepository()
    private val alarms = RecordingAlarmGateway()

    // 00:30 UTC is 06:00 in Kolkata on Monday 5 January.
    private val time = FakeTimeProvider(
        initial = Instant.parse("2026-01-05T00:30:00Z"),
        currentZone = ZoneId.of("Asia/Kolkata"),
    )

    private val dispatchers = object : AppDispatchers {
        override val default = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
        override val main = Dispatchers.Unconfined
    }

    private val widget = object : WidgetGateway {
        override suspend fun refresh() = Unit
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

    private val importPlan = ImportPlanUseCase(
        plans = plans,
        templates = templates,
        items = items,
        reschedule = reschedule,
        time = time,
        dispatchers = dispatchers,
    )

    private val shiftDay = ShiftDayUseCase(
        dayLogs = dayLogs,
        today = observeToday,
        reschedule = reschedule,
        widget = widget,
        time = time,
        dispatchers = dispatchers,
    )

    private val snooze = SnoozeItemUseCase(
        occurrences = occurrences,
        reschedule = reschedule,
        notifications = SilentNotifications(),
        widget = widget,
        time = time,
        dispatchers = dispatchers,
    )

    private val archive = ArchiveItemUseCase(items, reschedule, widget, dispatchers)

    private val switchTemplate = SwitchDayTemplateUseCase(dayLogs, reschedule, widget, time, dispatchers)

    private suspend fun givenAPlan(text: String = "07:30 Gym | alarm") {
        importPlan(PlanTextParser().parse(text).items, templateName = "Weekday")
    }

    private suspend fun theOnlyRow(): Occurrence = occurrences.observeForDate(time.today()).first().single()

    private fun lastAlarmFor(id: Long): String? =
        alarms.scheduledFor.lastOrNull { it.first == id }?.second?.toLocalTime()?.toString()

    @Test
    fun `a day shifted after its alarm was set moves the alarm with it`() = runTest {
        givenAPlan()
        val id = theOnlyRow().id
        assertThat(lastAlarmFor(id)).isEqualTo("07:30")

        shiftDay(90.minutes)

        assertThat(lastAlarmFor(id)).isEqualTo("09:00")
    }

    @Test
    fun `the row itself is brought to the plan, so every reader agrees`() = runTest {
        givenAPlan()

        shiftDay(90.minutes)

        assertThat(theOnlyRow().effectiveAt.toLocalTime().toString()).isEqualTo("09:00")
    }

    @Test
    fun `a step archived after its alarm was set is cancelled and its row removed`() = runTest {
        givenAPlan()
        val row = theOnlyRow()

        archive(row.itemId)

        assertThat(alarms.cancelled).contains(row.id)
        assertThat(occurrences.observeForDate(time.today()).first()).isEmpty()
    }

    @Test
    fun `a settled row is history and survives the plan changing under it`() = runTest {
        givenAPlan()
        val row = theOnlyRow()
        occurrences.settle(row.id, com.buildorbreak.core.model.enums.OccurrenceState.DONE, time.now())

        archive(row.itemId)

        assertThat(occurrences.observeForDate(time.today()).first().map { it.id }).containsExactly(row.id)
    }

    @Test
    fun `a snooze on an alarm that rang late still lands in the future`() = runTest {
        givenAPlan("06:01 Stand up | alarm")
        val id = theOnlyRow().id
        // 06:25. The alarm fired at 06:01, was left ringing, and the snooze is
        // tapped twenty four minutes later.
        time.advanceByMinutes(25)

        snooze(id, 10.minutes)

        assertThat(lastAlarmFor(id)).isEqualTo("06:35")
        assertThat(observeToday(time.today()).first()?.entries?.single()?.at?.toLocalTime().toString())
            .isEqualTo("06:35")
    }

    @Test
    fun `a snooze on a step not yet due moves it by exactly what was asked`() = runTest {
        givenAPlan("06:30 Stand up | alarm")
        val id = theOnlyRow().id

        snooze(id, 10.minutes)

        assertThat(lastAlarmFor(id)).isEqualTo("06:40")
    }

    @Test
    fun `a snooze at five to midnight stays inside the day`() = runTest {
        // 18:20 UTC is 23:50 in Kolkata.
        time.setTo(Instant.parse("2026-01-05T18:20:00Z"))
        givenAPlan("23:55 Lights out | alarm")
        val id = theOnlyRow().id

        snooze(id, 10.minutes)

        assertThat(lastAlarmFor(id)).isEqualTo("23:59")
        assertThat(theOnlyRow().date).isEqualTo(time.today())
    }

    @Test
    fun `a sick day declared after a late start keeps the late start`() = runTest {
        givenAPlan()
        shiftDay(90.minutes)
        val log = dayLogs.observe(time.today()).first()

        switchTemplate(planId = log!!.planId, templateId = log.templateId, mode = DayMode.REDUCED)

        assertThat(observeToday(time.today()).first()?.dayShift).isEqualTo(90.minutes)
        assertThat(observeToday(time.today()).first()?.mode).isEqualTo(DayMode.REDUCED)
    }

    @Test
    fun `running the pass twice changes nothing the second time`() = runTest {
        givenAPlan()
        shiftDay(90.minutes)
        val before = occurrences.observeForDate(time.today()).first()
        val alarmsBefore = alarms.scheduledFor.size

        reschedule()

        assertThat(occurrences.observeForDate(time.today()).first()).isEqualTo(before)
        assertThat(
            alarms.scheduledFor.drop(alarmsBefore).map {
                it.second
            },
        ).containsExactly(before.single().effectiveAt)
    }

    private class SilentNotifications : NotificationGateway {
        override suspend fun show(occurrence: Occurrence, item: Item, preview: CascadePreview?) = Unit
        override suspend fun dismiss(occurrenceId: Long) = Unit
        override suspend fun showMilestone(milestone: Milestone) = Unit
        override fun canPostNotifications(): Boolean = true
        override fun canUseFullScreenIntent(): Boolean = true
    }
}
