package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.common.result.getOrNull
import com.buildorbreak.core.domain.fake.FakeDayLogRepository
import com.buildorbreak.core.domain.fake.FakeItemRepository
import com.buildorbreak.core.domain.fake.FakeOccurrenceRepository
import com.buildorbreak.core.domain.fake.FakePlanRepository
import com.buildorbreak.core.domain.fake.FakeSettingsRepository
import com.buildorbreak.core.domain.fake.FakeTemplateRepository
import com.buildorbreak.core.domain.fake.RecordingAlarmGateway
import com.buildorbreak.core.domain.fake.RecordingWidgetGateway
import com.buildorbreak.core.domain.resolver.DefaultTimelineResolver
import com.buildorbreak.core.model.plan.Plan
import com.buildorbreak.core.testing.fixtures.ExecutionFixtures
import com.buildorbreak.core.testing.fixtures.PlanFixtures
import com.buildorbreak.core.testing.time.FakeTimeProvider
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

private val NOW: Instant = Instant.parse("2026-09-18T01:00:00Z")
private val TODAY: LocalDate = LocalDate.of(2026, 9, 18)

/** Two plans, and what switching, adding and deleting one does to the alarms. */
class PlanUseCasesTest {

    private val plans = FakePlanRepository()
    private val templates = FakeTemplateRepository()
    private val items = FakeItemRepository()
    private val occurrences = FakeOccurrenceRepository()
    private val alarms = RecordingAlarmGateway()
    private val widget = RecordingWidgetGateway()
    private val time = FakeTimeProvider(initial = NOW, currentZone = ExecutionFixtures.ZONE)

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
        dayLogs = FakeDayLogRepository(),
        settings = FakeSettingsRepository(),
        resolver = DefaultTimelineResolver(),
        time = time,
        dispatchers = dispatchers,
    )

    private val reschedule = RescheduleAllUseCase(observeToday, occurrences, alarms, time, dispatchers)
    private val switchPlan = SwitchPlanUseCase(plans, reschedule, widget, dispatchers)
    private val addPlan = AddPlanUseCase(plans, templates, switchPlan, time, dispatchers)
    private val rename = RenamePlanUseCase(plans, dispatchers)
    private val deletePlan =
        DeletePlanUseCase(plans, templates, items, occurrences, alarms, switchPlan, time, dispatchers)

    /** A plan with one day and one step at the given hour. */
    private suspend fun plan(
        id: Long,
        name: String,
        active: Boolean,
        hour: Int,
    ): Long {
        plans.upsert(
            Plan(id = id, name = name, isActive = active, zone = ExecutionFixtures.ZONE, createdAt = Instant.EPOCH),
        )
        templates.upsert(PlanFixtures.template(id = id, planId = id))
        items.upsert(PlanFixtures.item(id = id, templateId = id, anchor = PlanFixtures.fixedAt(hour)))

        return id
    }

    @Test
    fun `a new plan is active at once, with one day to write on`() = runTest {
        plan(id = 1, name = "Normal", active = true, hour = 8)

        val added = addPlan("Exam season", "Every day").getOrNull()

        assertThat(plans.observeActive().first()?.name).isEqualTo("Exam season")
        assertThat(templates.observeForPlan(added ?: 0).first().single().isDefault).isTrue()
        assertThat(widget.refreshes).isAtLeast(1)
    }

    @Test
    fun `a blank name is refused`() = runTest {
        assertThat(addPlan("   ", "Every day")).isInstanceOf(Outcome.Failure::class.java)
        assertThat(plans.plans.value).isEmpty()
    }

    @Test
    fun `switching plans drops the old day's alarms and sets the new day's`() = runTest {
        plan(id = 1, name = "Normal", active = true, hour = 8)
        plan(id = 2, name = "Exam season", active = false, hour = 20)
        reschedule(TODAY)
        val oldRow = occurrences.occurrences.value.single { it.itemId == 1L }

        switchPlan(2)

        assertThat(plans.observeActive().first()?.id).isEqualTo(2L)
        assertThat(alarms.cancelled).contains(oldRow.id)
        assertThat(occurrences.occurrences.value.map { it.itemId }).containsExactly(2L)
    }

    @Test
    fun `renaming keeps everything else about the plan`() = runTest {
        plan(id = 1, name = "Normal", active = true, hour = 8)

        rename(1, "  Weekdays ")

        val renamed = plans.plans.value.single()
        assertThat(renamed.name).isEqualTo("Weekdays")
        assertThat(renamed.isActive).isTrue()
    }

    @Test
    fun `the last plan cannot be deleted`() = runTest {
        plan(id = 1, name = "Normal", active = true, hour = 8)

        val outcome = deletePlan(1)

        assertThat(outcome).isInstanceOf(Outcome.Failure::class.java)
        assertThat(plans.plans.value).hasSize(1)
    }

    @Test
    fun `deleting the running plan hands over to the other one`() = runTest {
        plan(id = 1, name = "Normal", active = true, hour = 8)
        plan(id = 2, name = "Exam season", active = false, hour = 20)
        reschedule(TODAY)
        val oldRow = occurrences.occurrences.value.single { it.itemId == 1L }

        deletePlan(1)

        assertThat(plans.plans.value.map { it.id }).containsExactly(2L)
        assertThat(plans.observeActive().first()?.id).isEqualTo(2L)
        // Cancelled by row, because the cascade cannot reach AlarmManager.
        assertThat(alarms.cancelled).contains(oldRow.id)
    }

    @Test
    fun `deleting a plan that is not running changes nothing about today`() = runTest {
        plan(id = 1, name = "Normal", active = true, hour = 8)
        plan(id = 2, name = "Exam season", active = false, hour = 20)

        deletePlan(2)

        assertThat(plans.observeActive().first()?.id).isEqualTo(1L)
        assertThat(plans.plans.value).hasSize(1)
    }
}
