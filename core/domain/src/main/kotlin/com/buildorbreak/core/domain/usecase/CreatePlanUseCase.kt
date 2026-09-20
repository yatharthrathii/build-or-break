package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.common.result.getOrNull
import com.buildorbreak.core.common.time.TimeProvider
import com.buildorbreak.core.domain.error.DomainError.DataError
import com.buildorbreak.core.domain.repository.PlanRepository
import com.buildorbreak.core.domain.repository.TemplateRepository
import com.buildorbreak.core.model.enums.DayMode
import com.buildorbreak.core.model.plan.DayTemplate
import com.buildorbreak.core.model.plan.Plan
import com.buildorbreak.core.model.plan.Weekdays
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * A plan with nothing on it yet.
 *
 * The third way in from the first run: no sample, no paste, just an empty
 * template the editor can put a first step on. The editor needs a template to
 * write onto, so this creates the plan and its default template together and
 * returns the template id.
 *
 * An existing active plan is reused rather than replaced, for the same reason
 * the import does: somebody who taps "write it myself" on a second run has not
 * asked for their history to be deleted.
 */
class CreatePlanUseCase @Inject constructor(
    private val plans: PlanRepository,
    private val templates: TemplateRepository,
    private val time: TimeProvider,
    private val dispatchers: AppDispatchers,
) {

    suspend operator fun invoke(planName: String, templateName: String): Outcome<Long, DataError> =
        withContext(dispatchers.io) {
            val existing = plans.observeActive().first()

            if (existing != null) {
                templates.observeForPlan(existing.id).first().firstOrNull()?.let {
                    return@withContext Outcome.Success(it.id)
                }
            }

            val planId = existing?.id ?: createPlan(planName)
                ?: return@withContext Outcome.Failure(DataError.WriteFailed)

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
        }

    private suspend fun createPlan(name: String): Long? {
        val created = plans.upsert(
            Plan(id = 0, name = name, isActive = true, zone = time.zone(), createdAt = time.now()),
        ).getOrNull() ?: return null

        plans.setActive(created)

        return created
    }
}
