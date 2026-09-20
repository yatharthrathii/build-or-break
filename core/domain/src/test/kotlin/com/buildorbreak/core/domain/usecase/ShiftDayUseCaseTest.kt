package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.domain.fake.FakeDayLogRepository
import com.buildorbreak.core.domain.fake.FakeItemRepository
import com.buildorbreak.core.domain.fake.FakeOccurrenceRepository
import com.buildorbreak.core.domain.fake.FakePlanRepository
import com.buildorbreak.core.domain.fake.FakeSettingsRepository
import com.buildorbreak.core.domain.fake.FakeTemplateRepository
import com.buildorbreak.core.domain.fake.RecordingAlarmGateway
import com.buildorbreak.core.domain.gateway.WidgetGateway
import com.buildorbreak.core.domain.parse.PlanTextParser
import com.buildorbreak.core.domain.resolver.DefaultTimelineResolver
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
 * Woke up ninety minutes late, on a day nothing has been written for yet.
 *
 * This is the ordinary case and it used to be the broken one. Most days never
 * get a `day_log` row: the template comes from the weekday and there is nothing
 * to store. So the first shift of the morning had no row to update, the update
 * matched nothing, and the button reported success while moving no steps at all.
 */
class ShiftDayUseCaseTest {

    private val plans = FakePlanRepository()
    private val templates = FakeTemplateRepository()
    private val items = FakeItemRepository()
    private val occurrences = FakeOccurrenceRepository()
    private val dayLogs = FakeDayLogRepository()
    private val alarms = RecordingAlarmGateway()

    private val time = FakeTimeProvider(
        initial = Instant.parse("2026-01-05T00:30:00Z"),
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

    private val importPlan = ImportPlanUseCase(
        plans = plans,
        templates = templates,
        items = items,
        reschedule = RescheduleAllUseCase(observeToday, occurrences, alarms, time, dispatchers),
        time = time,
        dispatchers = dispatchers,
    )

    private val shiftDay = ShiftDayUseCase(
        dayLogs = dayLogs,
        today = observeToday,
        reschedule = RescheduleAllUseCase(observeToday, occurrences, alarms, time, dispatchers),
        widget = object : WidgetGateway {
            override suspend fun refresh() = Unit
        },
        time = time,
        dispatchers = dispatchers,
    )

    private suspend fun givenAPlan() {
        importPlan(PlanTextParser().parse("07:30 Gym\n09:00 Study\n21:00 Read").items, templateName = "Weekday")
    }

    @Test
    fun `shifting a day nothing has been written for still moves it`() = runTest {
        givenAPlan()

        shiftDay(90.minutes)

        val day = observeToday(time.today()).first()
        assertThat(day?.dayShift).isEqualTo(90.minutes)
        assertThat(day?.entries?.map { it.at.toLocalTime().toString() })
            .containsExactly("09:00", "10:30", "22:30").inOrder()
    }

    @Test
    fun `the shift is recorded against the template the day was already running`() = runTest {
        givenAPlan()

        shiftDay(90.minutes)

        val log = dayLogs.observe(time.today()).first()
        assertThat(log?.templateId).isEqualTo(templates.templates.value.single().id)
        assertThat(log?.planId).isEqualTo(plans.plans.value.single().id)
        assertThat(log?.dayShiftMinutes).isEqualTo(90)
    }

    @Test
    fun `shifting back to zero puts the day where it was`() = runTest {
        givenAPlan()

        shiftDay(90.minutes)
        shiftDay(0.minutes)

        val day = observeToday(time.today()).first()
        assertThat(day?.dayShift).isEqualTo(0.minutes)
        assertThat(day?.entries?.map { it.at.toLocalTime().toString() })
            .containsExactly("07:30", "09:00", "21:00").inOrder()
    }

    @Test
    fun `a second shift replaces the first rather than adding to it`() = runTest {
        givenAPlan()

        shiftDay(90.minutes)
        shiftDay(30.minutes)

        assertThat(observeToday(time.today()).first()?.dayShift).isEqualTo(30.minutes)
    }
}
