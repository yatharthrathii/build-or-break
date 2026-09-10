package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.domain.error.DomainError.DataError
import com.buildorbreak.core.domain.gateway.WidgetGateway
import com.buildorbreak.core.domain.repository.TemplateRepository
import com.buildorbreak.core.model.plan.DayTemplate
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Writes a template and puts the day right afterwards.
 *
 * A template's weekdays decide which plan runs on which day, so saving one can
 * change what today resolves to. The reschedule after the write is what keeps
 * the alarms honest about that.
 */
class SaveTemplateUseCase @Inject constructor(
    private val templates: TemplateRepository,
    private val reschedule: RescheduleAllUseCase,
    private val widget: WidgetGateway,
    private val dispatchers: AppDispatchers,
) {

    suspend operator fun invoke(template: DayTemplate): Outcome<Long, DataError> = withContext(dispatchers.io) {
        val written = templates.upsert(template)

        reschedule()
        widget.refresh()

        written
    }
}

/**
 * Removes a template, and refuses to remove the last one.
 *
 * Deleting cascades to the template's steps and their history, which the
 * screen says before asking. A plan with no template at all would leave Today
 * with nothing to resolve and no way back, so the last one stays.
 */
class DeleteTemplateUseCase @Inject constructor(
    private val templates: TemplateRepository,
    private val reschedule: RescheduleAllUseCase,
    private val widget: WidgetGateway,
    private val dispatchers: AppDispatchers,
) {

    suspend operator fun invoke(planId: Long, templateId: Long): Outcome<Unit, DataError> =
        withContext(dispatchers.io) {
            val remaining = templates.observeForPlan(planId).first().filterNot { it.id == templateId }
            if (remaining.isEmpty()) return@withContext Outcome.Failure(DataError.NotFound)

            // The default must survive. If the one being removed carried it,
            // hand it to the first that is left.
            if (remaining.none { it.isDefault }) {
                templates.upsert(remaining.first().copy(isDefault = true))
            }

            val deleted = templates.delete(templateId)

            reschedule()
            widget.refresh()

            deleted
        }
}
