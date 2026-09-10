package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.domain.fake.FakeDayLogRepository
import com.buildorbreak.core.domain.fake.FakeItemRepository
import com.buildorbreak.core.domain.fake.FakeOccurrenceRepository
import com.buildorbreak.core.domain.fake.FakePlanRepository
import com.buildorbreak.core.domain.fake.FakeSettingsRepository
import com.buildorbreak.core.domain.fake.FakeTemplateRepository
import com.buildorbreak.core.domain.fake.RecordingAlarmGateway
import com.buildorbreak.core.domain.parse.PlanTextParser
import com.buildorbreak.core.domain.resolver.DefaultTimelineResolver
import com.buildorbreak.core.model.enums.Salience
import com.buildorbreak.core.model.plan.Anchor
import com.buildorbreak.core.testing.time.FakeTimeProvider
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Pasted text all the way through to a resolved day.
 *
 * The one test that crosses the whole domain: parser, import, repositories,
 * resolver. Everything else in this module is unit tested in isolation, and this
 * is here because the seams between them are where an import quietly produces a
 * plan nobody asked for.
 */
class ImportPlanUseCaseTest {

    private val plans = FakePlanRepository()
    private val templates = FakeTemplateRepository()
    private val items = FakeItemRepository()
    private val occurrences = FakeOccurrenceRepository()
    private val dayLogs = FakeDayLogRepository()
    private val alarms = RecordingAlarmGateway()

    // 05:00 UTC is 10:30 in Kolkata, so the morning steps have passed and the
    // evening ones have not. That split is what the scheduling assertion needs.
    private val time = FakeTimeProvider(
        initial = Instant.parse("2026-01-05T05:00:00Z"),
        currentZone = ZoneId.of("Asia/Kolkata"),
    )

    /**
     * Unconfined rather than the shared `TestAppDispatchers`.
     *
     * That fixture builds its own `TestCoroutineScheduler`, and `runTest` builds
     * another; a `withContext` across the two throws. Nothing here waits on
     * time, so running everything inline on the calling thread is both simpler
     * and closer to what these assertions are actually about.
     */
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

    private suspend fun import(text: String) = importPlan(PlanTextParser().parse(text).items, templateName = "Weekday")

    @Test
    fun `a pasted routine becomes a plan, a template and its steps`() = runTest {
        import("06:30 Wake up\n07:00 Medicine\n21:00 Read")

        assertThat(plans.plans.value).hasSize(1)
        assertThat(templates.templates.value).hasSize(1)
        assertThat(items.items.value.map { it.title }).containsExactly("Wake up", "Medicine", "Read")
    }

    @Test
    fun `an offset is pointed at the step written before it`() = runTest {
        import("06:30 Wake up\n+10m Drink water")

        val water = items.items.value.first { it.title == "Drink water" }
        val wakeUp = items.items.value.first { it.title == "Wake up" }

        // The parser could not know this id because the row did not exist yet.
        val anchor = water.anchor as Anchor.Relative
        assertThat(anchor.parentItemId).isEqualTo(wakeUp.id)
        assertThat(anchor.offset).isEqualTo(10.minutes)
    }

    @Test
    fun `a chain of offsets links each step to the one above it`() = runTest {
        import("06:30 Wake up\n+10m Drink water\n+20m Medicine")

        val byTitle = items.items.value.associateBy { it.title }
        val water = byTitle.getValue("Drink water")
        val medicine = byTitle.getValue("Medicine").anchor as Anchor.Relative

        assertThat(medicine.parentItemId).isEqualTo(water.id)
    }

    @Test
    fun `the order lines were written in is the order they are stored in`() = runTest {
        import("21:00 Read\n06:30 Wake up\n12:00 Lunch")

        // Sorted by the plan, not by the clock. The resolver sorts by time; the
        // editor shows what the user wrote.
        assertThat(items.items.value.sortedBy { it.sortOrder }.map { it.title })
            .containsExactly("Read", "Wake up", "Lunch").inOrder()
    }

    @Test
    fun `every anchor kind survives the import`() = runTest {
        import(
            """
            06:30 Wake up
            07:30-09:30 Study block
            every 45m 11:00-15:00 Stand up
            """.trimIndent(),
        )

        val byTitle = items.items.value.associateBy { it.title }
        assertThat(byTitle.getValue("Wake up").anchor).isEqualTo(Anchor.Fixed(LocalTime.of(6, 30)))
        assertThat(byTitle.getValue("Study block").anchor)
            .isEqualTo(Anchor.Window(LocalTime.of(7, 30), LocalTime.of(9, 30)))
        assertThat(byTitle.getValue("Stand up").anchor)
            .isEqualTo(Anchor.Interval(45.minutes, LocalTime.of(11, 0), LocalTime.of(15, 0)))
    }

    @Test
    fun `a step that said nothing about volume is a reminder, not an alarm`() = runTest {
        import("06:30 Wake up")

        // Eleven full screen alarms on the first morning is how an app gets
        // uninstalled. The budget warning will say if even this is too much.
        assertThat(items.items.value.single().salience).isEqualTo(Salience.NOTIFY)
    }

    @Test
    fun `a step that did say so keeps what it said`() = runTest {
        import("06:30 | Wake up | alarm")

        assertThat(items.items.value.single().salience).isEqualTo(Salience.ALARM)
    }

    @Test
    fun `pinned and the smaller version both come through`() = runTest {
        import("18:00 | Gym class | pinned\n07:30 | Study | min: fifteen minutes")

        val byTitle = items.items.value.associateBy { it.title }
        assertThat(byTitle.getValue("Gym class").pinned).isTrue()
        assertThat(byTitle.getValue("Study").minimum?.title).isEqualTo("fifteen minutes")
    }

    @Test
    fun `importing nothing is refused rather than creating an empty plan`() = runTest {
        importPlan(emptyList(), templateName = "Weekday")

        assertThat(plans.plans.value).isEmpty()
        assertThat(templates.templates.value).isEmpty()
    }

    @Test
    fun `a second import adds a template rather than replacing the plan`() = runTest {
        import("06:30 Wake up")
        import("09:00 Weekend start")

        // Somebody importing a second routine has not asked for their history to
        // be deleted.
        assertThat(plans.plans.value).hasSize(1)
        assertThat(templates.templates.value).hasSize(2)
    }

    @Test
    fun `the imported day resolves and schedules straight away`() = runTest {
        import("06:30 Wake up\n+30m Medicine\n21:00 Read")

        val today = observeToday(time.today()).first()
        val tomorrow = observeToday(time.today().plusDays(1)).first()

        // Imported in the afternoon, so today is only what is still ahead. The
        // morning had not been planned yet when it happened.
        assertThat(today?.entries?.map { it.item.title }).containsExactly("Read")
        assertThat(tomorrow?.entries?.map { it.item.title }).containsExactly("Wake up", "Medicine", "Read").inOrder()
        assertThat(tomorrow?.entryFor(items.items.value.first { it.title == "Medicine" }.id)?.at)
            .isEqualTo(time.today().plusDays(1).atTime(7, 0))

        assertThat(alarms.scheduled).isNotEmpty()
    }
}
