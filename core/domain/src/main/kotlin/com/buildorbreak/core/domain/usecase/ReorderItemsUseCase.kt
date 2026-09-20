package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.domain.error.DomainError.DataError
import com.buildorbreak.core.domain.gateway.WidgetGateway
import com.buildorbreak.core.domain.repository.ItemRepository
import javax.inject.Inject
import kotlinx.coroutines.withContext

/**
 * Writes the order the user put the steps in.
 *
 * Order only decides ties. The timeline is placed by time, and two steps at
 * the same minute land in the order given here, so this is the one place the
 * user's hand on the list reaches the resolver. The rescheduling pass runs
 * afterwards because a group's lead is chosen by that same order, and the
 * lead is the step that rings.
 */
class ReorderItemsUseCase @Inject constructor(
    private val items: ItemRepository,
    private val reschedule: RescheduleAllUseCase,
    private val widget: WidgetGateway,
    private val dispatchers: AppDispatchers,
) {

    /** [orderedIds] is every step on the template, top to bottom. */
    suspend operator fun invoke(orderedIds: List<Long>): Outcome<Unit, DataError> = withContext(dispatchers.io) {
        val written = items.reorder(orderedIds)

        reschedule()
        widget.refresh()

        written
    }
}
