package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.domain.error.DomainError.DataError
import com.buildorbreak.core.domain.gateway.WidgetGateway
import com.buildorbreak.core.domain.repository.ItemRepository
import com.buildorbreak.core.domain.repository.PlanRepository
import com.buildorbreak.core.domain.repository.TemplateRepository
import com.buildorbreak.core.model.plan.Block
import com.buildorbreak.core.model.plan.DayTemplate
import com.buildorbreak.core.model.plan.Item
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

/**
 * Writes one item and puts the day right afterwards.
 *
 * A use case rather than a direct repository call, and architecture.md section
 * 5.4 is strict about when that is justified: this is three calls, not one.
 * Editing an item changes the resolved day, which changes every alarm derived
 * from it, and a save that did not reschedule would leave the phone ringing at
 * the old time until something else happened to trigger a pass.
 */
class SaveItemUseCase @Inject constructor(
    private val items: ItemRepository,
    private val reschedule: RescheduleAllUseCase,
    private val widget: WidgetGateway,
    private val dispatchers: AppDispatchers,
) {

    suspend operator fun invoke(item: Item): Outcome<Long, DataError> = withContext(dispatchers.io) {
        val written = items.upsert(item)

        reschedule()
        widget.refresh()

        written
    }
}

/**
 * Takes an item off the plan without taking it out of the past.
 *
 * Archived, never deleted. Occurrences point at items, so a removed row would
 * leave holes in every figure built on the history: a month where somebody kept
 * a habit would quietly become a month where nothing happened.
 */
class ArchiveItemUseCase @Inject constructor(
    private val items: ItemRepository,
    private val reschedule: RescheduleAllUseCase,
    private val widget: WidgetGateway,
    private val dispatchers: AppDispatchers,
) {

    suspend operator fun invoke(itemId: Long): Outcome<Unit, DataError> = withContext(dispatchers.io) {
        val archived = items.archive(itemId)

        reschedule()
        widget.refresh()

        archived
    }
}

/**
 * The plan as it is being edited, rather than as it resolves today.
 *
 * Deliberately not `ObserveTodayUseCase`. The editor shows every item on the
 * template including the ones that do not run today, because an editor that hid
 * the Saturday steps on a Tuesday would be an editor somebody could not use to
 * fix Saturday.
 */
class ObservePlanUseCase @Inject constructor(
    private val plans: PlanRepository,
    private val templates: TemplateRepository,
    private val items: ItemRepository,
) {

    /**
     * [templateId] picks which template is being edited. Null, or an id that no
     * longer exists, falls back to the default so a deleted template never
     * leaves the editor pointing at nothing.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(templateId: Long? = null): Flow<PlanContents> = plans.observeActive().flatMapLatest { plan ->
        if (plan == null) return@flatMapLatest flowOf(PlanContents.None)

        templates.observeForPlan(plan.id).flatMapLatest { available ->
            val template = available.firstOrNull { it.id == templateId }
                ?: chosen(available)
                ?: return@flatMapLatest flowOf(PlanContents.None)

            combine(
                items.observeForTemplate(template.id),
                items.observeBlocksForTemplate(template.id),
            ) { list, blocks ->
                PlanContents.Loaded(
                    planId = plan.id,
                    planName = plan.name,
                    template = template,
                    templates = available,
                    items = list,
                    blocks = blocks,
                )
            }
        }
    }

    /** One item, for an editor opening on an id it was handed. */
    suspend fun itemById(itemId: Long): Item? = items.byId(itemId)

    /** The template a new item should be written onto. */
    suspend fun defaultTemplateId(): Long? {
        val plan = plans.observeActive().first() ?: return null

        return chosen(templates.observeForPlan(plan.id).first())?.id
    }

    /**
     * The default template, or the only one there is.
     *
     * Falling back to the first rather than to nothing matters on an imported
     * plan: if the default flag were somehow lost, an editor showing an empty
     * screen would look like the import failed.
     */
    private fun chosen(available: List<DayTemplate>): DayTemplate? =
        available.firstOrNull { it.isDefault } ?: available.firstOrNull()
}

/**
 * Either there is a plan to edit or there is not.
 *
 * A sealed result rather than a nullable list, because "no plan yet" and "a plan
 * with no steps" need different screens and a null list cannot tell them apart.
 */
sealed interface PlanContents {
    data object None : PlanContents

    data class Loaded(
        val planId: Long,
        val planName: String,
        /** The template being edited. */
        val template: DayTemplate,
        /** Every template on the plan, for the tabs. */
        val templates: List<DayTemplate>,
        val items: List<Item>,
        /**
         * The groups on this template, in their own order.
         *
         * Carried alongside the items rather than nested inside them because
         * a group exists whether or not anything is in it: somebody who makes
         * "Morning routine" and then goes looking for it must find it, not an
         * empty screen and the impression that the app dropped it.
         */
        val blocks: List<Block> = emptyList(),
    ) : PlanContents
}
