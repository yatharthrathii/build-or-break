package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.common.result.getOrNull
import com.buildorbreak.core.common.time.TimeProvider
import com.buildorbreak.core.domain.error.DomainError.DataError
import com.buildorbreak.core.domain.gateway.AlarmGateway
import com.buildorbreak.core.domain.gateway.WidgetGateway
import com.buildorbreak.core.domain.repository.ItemRepository
import com.buildorbreak.core.domain.repository.OccurrenceRepository
import com.buildorbreak.core.domain.repository.PlanRepository
import com.buildorbreak.core.domain.repository.TemplateRepository
import com.buildorbreak.core.model.enums.DayMode
import com.buildorbreak.core.model.plan.DayTemplate
import com.buildorbreak.core.model.plan.Plan
import com.buildorbreak.core.model.plan.Weekdays
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Every plan, oldest first. The active one is the one Today runs. */
class ObservePlansUseCase @Inject constructor(private val plans: PlanRepository) {

    operator fun invoke(): Flow<List<Plan>> = plans.observeAll()
}

/**
 * Makes a plan and starts running it.
 *
 * A plan with one empty day, like the blank first run, and made active at
 * once: somebody who adds "Exam season" is about to fill it in, and a plan
 * that sat behind the old one until they found the switch would look like
 * the add had failed.
 */
class AddPlanUseCase @Inject constructor(
    private val plans: PlanRepository,
    private val templates: TemplateRepository,
    private val switchPlan: SwitchPlanUseCase,
    private val time: TimeProvider,
    private val dispatchers: AppDispatchers,
) {

    suspend operator fun invoke(name: String, templateName: String): Outcome<Long, DataError> =
        withContext(dispatchers.io) {
            if (name.isBlank()) return@withContext Outcome.Failure(DataError.ConstraintViolation)

            val planId = plans.upsert(
                Plan(id = 0, name = name.trim(), isActive = false, zone = time.zone(), createdAt = time.now()),
            ).getOrNull() ?: return@withContext Outcome.Failure(DataError.WriteFailed)

            templates.upsert(
                DayTemplate(
                    id = 0,
                    planId = planId,
                    name = templateName,
                    weekdays = Weekdays.EveryDay,
                    isDefault = true,
                    mode = DayMode.NORMAL,
                    sortOrder = 0,
                ),
            )

            switchPlan(planId)

            Outcome.Success(planId)
        }
}

/**
 * Runs a different plan from now on.
 *
 * The reschedule afterwards is the whole point. The old plan's open rows for
 * today are orphans once the day resolves from the new one, and the pass
 * cancels their alarms and drops them; without it the phone would go on
 * ringing for a routine nobody is running.
 */
class SwitchPlanUseCase @Inject constructor(
    private val plans: PlanRepository,
    private val reschedule: RescheduleAllUseCase,
    private val widget: WidgetGateway,
    private val dispatchers: AppDispatchers,
) {

    suspend operator fun invoke(planId: Long): Outcome<Unit, DataError> = withContext(dispatchers.io) {
        val switched = plans.setActive(planId)

        reschedule()
        widget.refresh()

        switched
    }
}

class RenamePlanUseCase @Inject constructor(
    private val plans: PlanRepository,
    private val dispatchers: AppDispatchers,
) {

    suspend operator fun invoke(planId: Long, name: String): Outcome<Unit, DataError> = withContext(dispatchers.io) {
        if (name.isBlank()) return@withContext Outcome.Failure(DataError.ConstraintViolation)

        val plan = plans.observeAll().first().firstOrNull { it.id == planId }
            ?: return@withContext Outcome.Failure(DataError.NotFound)

        when (val written = plans.upsert(plan.copy(name = name.trim()))) {
            is Outcome.Success -> Outcome.Success(Unit)
            is Outcome.Failure -> written
        }
    }
}

/**
 * Deletes a plan, and refuses to delete the last one.
 *
 * Everything under it goes: days, steps, history, goals. The screen says so
 * before asking. The alarms are cancelled first, by row, because they live
 * in `AlarmManager` rather than in the database and a cascade cannot reach
 * them. If the plan being deleted was the one running, the oldest of the
 * rest takes over, so Today never resolves from nothing.
 */
class DeletePlanUseCase @Inject constructor(
    private val plans: PlanRepository,
    private val templates: TemplateRepository,
    private val items: ItemRepository,
    private val occurrences: OccurrenceRepository,
    private val alarms: AlarmGateway,
    private val switchPlan: SwitchPlanUseCase,
    private val time: TimeProvider,
    private val dispatchers: AppDispatchers,
) {

    suspend operator fun invoke(planId: Long): Outcome<Unit, DataError> = withContext(dispatchers.io) {
        val all = plans.observeAll().first()
        val doomed = all.firstOrNull { it.id == planId } ?: return@withContext Outcome.Failure(DataError.NotFound)
        val rest = all.filterNot { it.id == planId }
        if (rest.isEmpty()) return@withContext Outcome.Failure(DataError.ConstraintViolation)

        cancelAlarmsOf(planId)

        val deleted = plans.delete(planId)

        if (doomed.isActive) switchPlan(rest.first().id)

        deleted
    }

    private suspend fun cancelAlarmsOf(planId: Long) {
        val today = time.today()
        val itemIds = templates.observeForPlan(planId).first()
            .flatMap { items.allForTemplate(it.id) }
            .map { it.id }
            .toSet()

        occurrences.between(today.minusDays(1), today.plusDays(1))
            .filter { it.itemId in itemIds }
            .forEach { alarms.cancel(it.id) }
    }
}
