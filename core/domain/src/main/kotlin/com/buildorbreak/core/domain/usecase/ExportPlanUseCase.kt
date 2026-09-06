package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.time.TimeProvider
import com.buildorbreak.core.domain.export.ExportBuilder
import com.buildorbreak.core.domain.export.ExportInput
import com.buildorbreak.core.domain.repository.DayCloseRepository
import com.buildorbreak.core.domain.repository.ItemRepository
import com.buildorbreak.core.domain.repository.OccurrenceRepository
import com.buildorbreak.core.domain.repository.PlanRepository
import com.buildorbreak.core.domain.repository.TemplateRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** How much history goes into the file. Ninety days is a quarter, and enough. */
private const val HISTORY_DAYS = 90L

/**
 * The plan and its history as one text, ready to share.
 *
 * The document is built by `ExportBuilder`, which is pure and tested; this
 * gathers what it needs. Returns null when there is no plan, because an export
 * of nothing is not a file anybody asked for.
 */
class ExportPlanUseCase @Inject constructor(
    private val plans: PlanRepository,
    private val templates: TemplateRepository,
    private val items: ItemRepository,
    private val occurrences: OccurrenceRepository,
    private val closes: DayCloseRepository,
    private val builder: ExportBuilder,
    private val time: TimeProvider,
    private val dispatchers: AppDispatchers,
) {

    suspend operator fun invoke(includeHistory: Boolean = true): String? = withContext(dispatchers.io) {
        val plan = plans.observeActive().first() ?: return@withContext null
        val planTemplates = templates.observeForPlan(plan.id).first()

        val today = time.today()
        val from = today.minusDays(HISTORY_DAYS)

        val input = ExportInput(
            plan = plan,
            templates = planTemplates,
            blocks = planTemplates.flatMap { items.observeBlocksForTemplate(it.id).first() },
            items = planTemplates.flatMap { items.observeForTemplate(it.id).first() },
            occurrences = if (includeHistory) occurrences.between(from, today) else emptyList(),
            closes = if (includeHistory) closes.observeRange(from, today).first() else emptyList(),
        )

        builder.buildJson(input, exportedAt = time.now(), includeHistory = includeHistory)
    }
}
