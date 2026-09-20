package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.domain.fake.FakeDayLogRepository
import com.buildorbreak.core.domain.fake.FakeItemRepository
import com.buildorbreak.core.domain.fake.FakeOccurrenceRepository
import com.buildorbreak.core.domain.fake.FakePlanRepository
import com.buildorbreak.core.domain.fake.FakeSettingsRepository
import com.buildorbreak.core.domain.fake.FakeTemplateRepository
import com.buildorbreak.core.domain.fake.RecordingAlarmGateway
import com.buildorbreak.core.domain.fake.RecordingWidgetGateway
import com.buildorbreak.core.domain.resolver.DefaultTimelineResolver
import com.buildorbreak.core.model.enums.Salience
import com.buildorbreak.core.model.plan.Anchor
import com.buildorbreak.core.model.plan.MinimumVersion
import com.buildorbreak.core.model.review.ReviewAnswer
import com.buildorbreak.core.testing.fixtures.ExecutionFixtures
import com.buildorbreak.core.testing.fixtures.PlanFixtures
import com.buildorbreak.core.testing.fixtures.PlanFixtures.item
import com.buildorbreak.core.testing.time.FakeTimeProvider
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ApplyReviewAnswerUseCaseTest {

    private val items = FakeItemRepository()
    private val occurrences = FakeOccurrenceRepository()
    private val settings = FakeSettingsRepository()
    private val time = FakeTimeProvider(currentZone = ExecutionFixtures.ZONE)
    private val weekStart: LocalDate = LocalDate.of(2026, 8, 31)

    private val dispatchers = object : AppDispatchers {
        override val default = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
        override val main = Dispatchers.Unconfined
    }

    private val observeToday = ObserveTodayUseCase(
        plans = FakePlanRepository(),
        templates = FakeTemplateRepository(),
        items = items,
        occurrences = occurrences,
        dayLogs = FakeDayLogRepository(),
        settings = settings,
        resolver = DefaultTimelineResolver(),
        time = time,
        dispatchers = dispatchers,
    )
    private val reschedule = RescheduleAllUseCase(observeToday, occurrences, RecordingAlarmGateway(), time, dispatchers)
    private val widget = RecordingWidgetGateway()

    private val apply = ApplyReviewAnswerUseCase(
        items = items,
        occurrences = occurrences,
        settings = settings,
        saveItem = SaveItemUseCase(items, reschedule, widget, dispatchers),
        archiveItem = ArchiveItemUseCase(items, reschedule, widget, dispatchers),
        time = time,
        dispatchers = dispatchers,
    )

    private suspend fun seed(anchor: Anchor = PlanFixtures.fixedAt(21), salience: Salience = Salience.NOTIFY): Long =
        items.upsert(
            item(id = 0, title = "Read", anchor = anchor, salience = salience, minimum = MinimumVersion("Two pages")),
        )
            .let { (it as com.buildorbreak.core.common.result.Outcome.Success).value }

    @Test
    fun `widen turns a fixed time into a window around it`() = runTest {
        val id = seed()

        apply(id, ReviewAnswer.WIDEN_WINDOW, weekStart)

        assertThat(items.byId(id)?.anchor).isEqualTo(Anchor.Window(LocalTime.of(20, 30), LocalTime.of(22, 0)))
    }

    @Test
    fun `move follows the median of when it actually happened`() = runTest {
        val id = seed()
        val today = time.today()
        // Done forty minutes late on three recent days.
        occurrences.occurrences.value = (1L..3L).map { daysAgo ->
            ExecutionFixtures.doneLate(itemId = id, date = today.minusDays(daysAgo), minutesLate = 40, id = daysAgo)
        }

        apply(id, ReviewAnswer.MOVE_TIME, weekStart)

        assertThat(items.byId(id)?.anchor).isEqualTo(Anchor.Fixed(LocalTime.of(21, 40)))
    }

    @Test
    fun `louder is one step up and never past alarm`() = runTest {
        val id = seed(salience = Salience.SILENT)

        apply(id, ReviewAnswer.RAISE_SALIENCE, weekStart)
        assertThat(items.byId(id)?.salience).isEqualTo(Salience.NOTIFY)

        apply(id, ReviewAnswer.RAISE_SALIENCE, weekStart)
        apply(id, ReviewAnswer.RAISE_SALIENCE, weekStart)
        assertThat(items.byId(id)?.salience).isEqualTo(Salience.ALARM)
    }

    @Test
    fun `use minimum makes the smaller version the step`() = runTest {
        val id = seed()

        apply(id, ReviewAnswer.USE_MINIMUM, weekStart)

        val saved = items.byId(id)
        assertThat(saved?.title).isEqualTo("Two pages")
        assertThat(saved?.minimum).isNull()
    }

    @Test
    fun `remove archives the step`() = runTest {
        val id = seed()

        apply(id, ReviewAnswer.REMOVE_ITEM, weekStart)

        assertThat(items.byId(id)?.isArchived).isTrue()
    }

    @Test
    fun `every answer closes the question for the week`() = runTest {
        val id = seed()

        apply(id, ReviewAnswer.LEAVE_IT, weekStart)

        assertThat(settings.dismissedReviewWeek.first()).isEqualTo(weekStart)
        assertThat(items.byId(id)?.anchor).isEqualTo(PlanFixtures.fixedAt(21))
    }
}
