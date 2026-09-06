package com.buildorbreak.core.domain.fake

import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.domain.error.DomainError.AlarmError
import com.buildorbreak.core.domain.error.DomainError.DataError
import com.buildorbreak.core.domain.gateway.AlarmGateway
import com.buildorbreak.core.domain.gateway.NotificationGateway
import com.buildorbreak.core.domain.gateway.WidgetGateway
import com.buildorbreak.core.domain.repository.DayCloseRepository
import com.buildorbreak.core.domain.repository.DayLogRepository
import com.buildorbreak.core.domain.repository.ItemRepository
import com.buildorbreak.core.domain.repository.OccurrenceRepository
import com.buildorbreak.core.domain.repository.PlanRepository
import com.buildorbreak.core.domain.repository.ResetRepository
import com.buildorbreak.core.domain.repository.SettingsRepository
import com.buildorbreak.core.domain.repository.TemplateRepository
import com.buildorbreak.core.model.enums.DeliveryTier
import com.buildorbreak.core.model.enums.Milestone
import com.buildorbreak.core.model.enums.OccurrenceState
import com.buildorbreak.core.model.enums.ThemeMode
import com.buildorbreak.core.model.execution.DayLog
import com.buildorbreak.core.model.execution.Occurrence
import com.buildorbreak.core.model.goal.DayClose
import com.buildorbreak.core.model.plan.Block
import com.buildorbreak.core.model.plan.DayTemplate
import com.buildorbreak.core.model.plan.Item
import com.buildorbreak.core.model.plan.Plan
import com.buildorbreak.core.model.resolved.CascadePreview
import com.buildorbreak.core.model.resolved.ResolvedEntry
import java.time.Instant
import java.time.LocalDate
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In memory repositories, for testing the use cases.
 *
 * They live in the domain's own test source set rather than in `:core:testing`,
 * and that is forced rather than chosen: the interfaces are in `:core:domain`,
 * and `:core:domain` already uses `:core:testing` for fixtures, so putting them
 * there would be a dependency cycle Gradle refuses.
 *
 * Each one keeps a `MutableStateFlow` so a write is visible to a collector
 * immediately, which is what makes a use case that writes and then reads its own
 * result testable at all.
 */
class FakePlanRepository : PlanRepository {
    val plans = MutableStateFlow<List<Plan>>(emptyList())
    private var nextId = 1L

    override fun observeActive(): Flow<Plan?> = plans.map { list -> list.firstOrNull { it.isActive } }

    override fun observeAll(): Flow<List<Plan>> = plans

    override suspend fun upsert(plan: Plan): Outcome<Long, DataError> {
        val id = if (plan.id == 0L) nextId++ else plan.id
        plans.value = plans.value.filterNot { it.id == id } + plan.copy(id = id)

        return Outcome.Success(id)
    }

    override suspend fun setActive(planId: Long): Outcome<Unit, DataError> {
        plans.value = plans.value.map { it.copy(isActive = it.id == planId) }

        return Outcome.Success(Unit)
    }
}

class FakeTemplateRepository : TemplateRepository {
    val templates = MutableStateFlow<List<DayTemplate>>(emptyList())
    private var nextId = 1L

    override fun observeForPlan(planId: Long): Flow<List<DayTemplate>> =
        templates.map { list -> list.filter { it.planId == planId } }

    override suspend fun defaultFor(planId: Long, date: LocalDate): DayTemplate? =
        templates.value.firstOrNull { it.planId == planId && it.isDefault }

    override suspend fun upsert(template: DayTemplate): Outcome<Long, DataError> {
        val id = if (template.id == 0L) nextId++ else template.id
        templates.value = templates.value.filterNot { it.id == id } + template.copy(id = id)

        return Outcome.Success(id)
    }

    override suspend fun delete(templateId: Long): Outcome<Unit, DataError> {
        templates.value = templates.value.filterNot { it.id == templateId }

        return Outcome.Success(Unit)
    }
}

class FakeItemRepository : ItemRepository {
    val items = MutableStateFlow<List<Item>>(emptyList())
    private var nextId = 1L

    override fun observeForTemplate(templateId: Long): Flow<List<Item>> =
        items.map { list -> list.filter { it.templateId == templateId && !it.isArchived }.sortedBy { it.sortOrder } }

    override fun observeBlocksForTemplate(templateId: Long): Flow<List<Block>> = MutableStateFlow(emptyList())

    override suspend fun byId(itemId: Long): Item? = items.value.firstOrNull { it.id == itemId }

    override suspend fun upsert(item: Item): Outcome<Long, DataError> {
        val id = if (item.id == 0L) nextId++ else item.id
        items.value = items.value.filterNot { it.id == id } + item.copy(id = id)

        return Outcome.Success(id)
    }

    override suspend fun upsertBlock(block: Block): Outcome<Long, DataError> = Outcome.Success(block.id)

    override suspend fun archive(itemId: Long): Outcome<Unit, DataError> {
        items.value = items.value.map { if (it.id == itemId) it.copy(archivedAt = Instant.EPOCH) else it }

        return Outcome.Success(Unit)
    }
}

class FakeOccurrenceRepository : OccurrenceRepository {
    val occurrences = MutableStateFlow<List<Occurrence>>(emptyList())
    private var nextId = 1L

    override fun observeForDate(date: LocalDate): Flow<List<Occurrence>> =
        occurrences.map { list -> list.filter { it.date == date } }

    /** Ignores anything already there, the same way the real insert does. */
    override suspend fun materialise(entries: List<ResolvedEntry>, date: LocalDate): Outcome<Unit, DataError> {
        val existing = occurrences.value.map { it.itemId to it.sequenceInDay }.toSet()

        val fresh = entries
            .filterNot { (it.item.id to it.sequenceInDay) in existing }
            .map { entry ->
                Occurrence(
                    id = nextId++,
                    itemId = entry.item.id,
                    date = date,
                    plannedAt = entry.at,
                    scheduledAt = null,
                    firedAt = null,
                    settledAt = null,
                    state = OccurrenceState.PENDING,
                    sequenceInDay = entry.sequenceInDay,
                )
            }

        occurrences.value = occurrences.value + fresh

        return Outcome.Success(Unit)
    }

    override suspend fun settle(id: Long, state: OccurrenceState, at: Instant): Outcome<Unit, DataError> {
        occurrences.value = occurrences.value.map { if (it.id == id) it.copy(state = state, settledAt = at) else it }

        return Outcome.Success(Unit)
    }

    override suspend fun shift(id: Long, by: Duration): Outcome<Occurrence, DataError> {
        occurrences.value = occurrences.value.map {
            if (it.id == id) {
                it.copy(shiftMinutes = it.shiftMinutes + by.inWholeMinutes.toInt(), snoozeCount = it.snoozeCount + 1)
            } else {
                it
            }
        }

        return occurrences.value.firstOrNull { it.id == id }
            ?.let { Outcome.Success(it) }
            ?: Outcome.Failure(DataError.NotFound)
    }

    override suspend fun pendingBefore(instant: Instant): List<Occurrence> = emptyList()

    override suspend fun between(from: LocalDate, to: LocalDate): List<Occurrence> =
        occurrences.value.filter { it.date in from..to }.sortedWith(compareBy({ it.date }, { it.plannedAt }))
}

class FakeDayLogRepository : DayLogRepository {
    val logs = MutableStateFlow<List<DayLog>>(emptyList())

    override fun observe(date: LocalDate): Flow<DayLog?> = logs.map { list -> list.firstOrNull { it.date == date } }

    override suspend fun upsert(log: DayLog): Outcome<Unit, DataError> {
        logs.value = logs.value.filterNot { it.date == log.date } + log

        return Outcome.Success(Unit)
    }

    override suspend fun setShift(date: LocalDate, shift: Duration): Outcome<Unit, DataError> {
        logs.value = logs.value.map {
            if (it.date == date) it.copy(dayShiftMinutes = shift.inWholeMinutes.toInt()) else it
        }

        return Outcome.Success(Unit)
    }
}

/**
 * Records what it was asked to do rather than doing it.
 *
 * This is the whole reason the gateways are interfaces the domain owns: the
 * scheduling flow can be asserted on without a device, a permission or an alarm.
 */
class RecordingAlarmGateway(private val tier: DeliveryTier = DeliveryTier.FULL_SCREEN_ALARM) : AlarmGateway {
    val scheduled = mutableListOf<Long>()
    val cancelled = mutableListOf<Long>()

    override fun currentTier(): DeliveryTier = tier

    override suspend fun schedule(occurrence: Occurrence, item: Item): Outcome<Unit, AlarmError> {
        scheduled += occurrence.id

        return Outcome.Success(Unit)
    }

    override suspend fun cancel(occurrenceId: Long) {
        cancelled += occurrenceId
    }

    override suspend fun cancelAll() {
        cancelled += scheduled
        scheduled.clear()
    }
}

class RecordingNotificationGateway : NotificationGateway {
    val shown = mutableListOf<Long>()
    val dismissed = mutableListOf<Long>()
    val milestones = mutableListOf<Milestone>()

    override suspend fun show(occurrence: Occurrence, item: Item, preview: CascadePreview?) {
        shown += occurrence.id
    }

    override suspend fun dismiss(occurrenceId: Long) {
        dismissed += occurrenceId
    }

    override suspend fun showMilestone(milestone: Milestone) {
        milestones += milestone
    }

    override fun canPostNotifications(): Boolean = true

    override fun canUseFullScreenIntent(): Boolean = true
}

class RecordingWidgetGateway : WidgetGateway {
    var refreshes = 0
        private set

    override suspend fun refresh() {
        refreshes++
    }
}

class FakeDayCloseRepository : DayCloseRepository {
    val closes = MutableStateFlow<List<DayClose>>(emptyList())

    override fun observeRange(from: LocalDate, to: LocalDate): Flow<List<DayClose>> =
        closes.map { list -> list.filter { it.date in from..to }.sortedBy { it.date } }

    override suspend fun upsert(close: DayClose): Outcome<Unit, DataError> {
        closes.value = closes.value.filterNot { it.date == close.date } + close

        return Outcome.Success(Unit)
    }

    override suspend fun lastClosedDate(): LocalDate? = closes.value.maxOfOrNull { it.date }
}

class FakeSettingsRepository : SettingsRepository {
    private val onboarding = MutableStateFlow(false)
    private val theme = MutableStateFlow(ThemeMode.SYSTEM)
    private val dismissed = MutableStateFlow<LocalDate?>(null)

    override val onboardingComplete: Flow<Boolean> = onboarding
    override val themeMode: Flow<ThemeMode> = theme
    override val dismissedReviewWeek: Flow<LocalDate?> = dismissed

    override suspend fun setOnboardingComplete(complete: Boolean) {
        onboarding.value = complete
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        theme.value = mode
    }

    override suspend fun setDismissedReviewWeek(week: LocalDate) {
        dismissed.value = week
    }
}

class RecordingResetRepository : ResetRepository {
    var wipes = 0
        private set

    override suspend fun wipeEverything(): Outcome<Unit, DataError> {
        wipes++

        return Outcome.Success(Unit)
    }
}
