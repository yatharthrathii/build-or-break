package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.time.TimeProvider
import com.buildorbreak.core.domain.export.ExportBuilder
import com.buildorbreak.core.domain.export.ExportInput
import com.buildorbreak.core.domain.repository.DayCloseRepository
import com.buildorbreak.core.domain.repository.GoalRepository
import com.buildorbreak.core.domain.repository.ItemRepository
import com.buildorbreak.core.domain.repository.MeasurementRepository
import com.buildorbreak.core.domain.repository.MilestoneRepository
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
    private val goals: GoalRepository,
    private val measurements: MeasurementRepository,
    private val milestones: MilestoneRepository,
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

        // The goal goes with the routine, not with the history. Somebody
        // sharing a routine is sharing what it is for; what they are not
        // sharing is the weights they have been standing on a scale to get.
        val goal = goals.observeActive(plan.id).first()

        // Archived steps included. The history points at them, and a file
        // without them restores those days as empty.
        val planItems = planTemplates.flatMap { items.allForTemplate(it.id) }

        val input = ExportInput(
            plan = plan,
            templates = planTemplates,
            blocks = planTemplates.flatMap { items.observeBlocksForTemplate(it.id).first() },
            items = planItems,
            goals = listOfNotNull(goal),
            occurrences = if (includeHistory) occurrences.between(from, today) else emptyList(),
            // Every number against every step, not only the goal's own series.
            // A reading is a thing the user typed, and a backup that dropped
            // the ones the current goal does not read would lose them for good
            // the day that goal is retired.
            measurements = if (includeHistory) {
                planItems.flatMap { measurements.observeForItem(it.id).first() }
            } else {
                emptyList()
            },
            closes = if (includeHistory) closes.observeRange(from, today).first() else emptyList(),
            // Not trimmed to the ninety days. A badge earned in the first week
            // is the record that stops it being awarded again, and one aged
            // out of the file would come back as a brand new banner.
            milestones = if (includeHistory) milestones.awarded() else emptyList(),
        )

        builder.buildJson(input, exportedAt = time.now(), includeHistory = includeHistory)
    }
}
