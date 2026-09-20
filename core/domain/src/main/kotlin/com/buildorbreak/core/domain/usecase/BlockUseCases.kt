package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.domain.error.DomainError.DataError
import com.buildorbreak.core.domain.gateway.WidgetGateway
import com.buildorbreak.core.domain.repository.ItemRepository
import com.buildorbreak.core.model.plan.Block
import javax.inject.Inject
import kotlinx.coroutines.withContext

/**
 * Writes a group and puts the day right afterwards.
 *
 * A group is five things between 08:00 and 08:30 delivered as one notification
 * rather than five alarms; rules.md section 1 rule 4 exists because the
 * alternative is a muted app inside a week. Its salience overrides the
 * salience of every step inside it, which is the whole mechanism, so a change
 * here changes what the phone will do tonight and has to reschedule.
 */
class SaveBlockUseCase @Inject constructor(
    private val items: ItemRepository,
    private val reschedule: RescheduleAllUseCase,
    private val widget: WidgetGateway,
    private val dispatchers: AppDispatchers,
) {

    suspend operator fun invoke(block: Block): Outcome<Long, DataError> = withContext(dispatchers.io) {
        val written = items.upsertBlock(block)

        reschedule()
        widget.refresh()

        written
    }
}

/**
 * Removes a group and leaves its steps where they were.
 *
 * Deleting a group is not deleting a routine. Every step inside it keeps its
 * own time, its own days and its own loudness and simply stops being grouped,
 * which is what somebody tidying up their plan means by it. A delete that took
 * five steps with it would be the single most expensive misunderstanding this
 * screen could produce.
 */
class DeleteBlockUseCase @Inject constructor(
    private val items: ItemRepository,
    private val reschedule: RescheduleAllUseCase,
    private val widget: WidgetGateway,
    private val dispatchers: AppDispatchers,
) {

    suspend operator fun invoke(templateId: Long, blockId: Long): Outcome<Unit, DataError> =
        withContext(dispatchers.io) {
            val removed = items.deleteBlock(templateId, blockId)

            reschedule()
            widget.refresh()

            removed
        }
}
