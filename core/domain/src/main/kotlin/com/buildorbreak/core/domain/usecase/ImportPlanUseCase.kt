package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.common.result.getOrNull
import com.buildorbreak.core.common.time.TimeProvider
import com.buildorbreak.core.domain.error.DomainError.DataError
import com.buildorbreak.core.domain.parse.ParsedItem
import com.buildorbreak.core.domain.parse.PlanTextParser
import com.buildorbreak.core.domain.repository.ItemRepository
import com.buildorbreak.core.domain.repository.PlanRepository
import com.buildorbreak.core.domain.repository.TemplateRepository
import com.buildorbreak.core.model.enums.DayMode
import com.buildorbreak.core.model.enums.ItemKind
import com.buildorbreak.core.model.enums.Salience
import com.buildorbreak.core.model.enums.ValueKind
import com.buildorbreak.core.model.plan.Anchor
import com.buildorbreak.core.model.plan.DayTemplate
import com.buildorbreak.core.model.plan.Item
import com.buildorbreak.core.model.plan.MinimumVersion
import com.buildorbreak.core.model.plan.Plan
import com.buildorbreak.core.model.plan.Weekdays
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * A step that said nothing about how loud it should be.
 *
 * `NOTIFY` rather than `SILENT`, because somebody importing a routine wants to
 * be told when a step is due, and rather than `ALARM`, because a pasted plan
 * turning into eleven full screen alarms is how an app gets uninstalled on its
 * first morning. The salience budget will warn if even this is too much.
 */
private val DEFAULT_SALIENCE = Salience.NOTIFY

/**
 * Turns understood text into rows.
 *
 * The last step of the import path, after `PlanTextParser` and after the user has
 * seen what was understood and agreed to it. Nothing here re reads the text: by
 * this point the decisions have been made and the only job left is writing them
 * down in the right order.
 *
 * **Order is the whole difficulty.** A `RELATIVE` item points at a parent by id,
 * and the parser cannot know those ids because the rows do not exist yet. So it
 * writes `PlanTextParser.PARENT_UNRESOLVED` and this resolves it against the row
 * written immediately before, which is what "+15m" means on the line after
 * something. Doing that requires inserting one at a time and keeping the ids,
 * which is slower than a batch and is the reason this is a use case rather than
 * a repository call.
 */
class ImportPlanUseCase @Inject constructor(
    private val plans: PlanRepository,
    private val templates: TemplateRepository,
    private val items: ItemRepository,
    private val reschedule: RescheduleAllUseCase,
    private val time: TimeProvider,
    private val dispatchers: AppDispatchers,
) {

    /**
     * Writes the plan and returns the template the items landed on.
     *
     * An existing active plan is reused rather than replaced. Somebody importing
     * a second routine has not asked for their first one to be deleted, and a
     * routine app that quietly discards a month of history on an import is one
     * nobody imports into twice.
     */
    suspend operator fun invoke(
        parsed: List<ParsedItem>,
        templateName: String,
        planName: String = templateName,
    ): Outcome<Long, DataError> = withContext(dispatchers.io) {
        if (parsed.isEmpty()) return@withContext Outcome.Failure(DataError.NotFound)

        val planId = activePlanId(planName) ?: return@withContext Outcome.Failure(DataError.WriteFailed)
        val templateId =
            createTemplate(planId, templateName) ?: return@withContext Outcome.Failure(DataError.WriteFailed)

        writeItems(parsed, templateId)
        reschedule()

        Outcome.Success(templateId)
    }

    private suspend fun activePlanId(name: String): Long? {
        plans.observeActive().first()?.let { return it.id }

        val created = plans.upsert(
            Plan(id = 0, name = name, isActive = true, zone = time.zone(), createdAt = time.now()),
        ).getOrNull() ?: return null

        plans.setActive(created)

        return created
    }

    /**
     * Every imported template runs every day, and is the default.
     *
     * A pasted routine says nothing about weekdays, and guessing would be worse
     * than being obviously wrong: somebody who meant weekdays only can see the
     * template on Saturday and fix it, whereas a routine that silently does not
     * run is invisible until the morning it was needed.
     */
    private suspend fun createTemplate(planId: Long, name: String): Long? = templates.upsert(
        DayTemplate(
            id = 0,
            planId = planId,
            name = name,
            weekdays = Weekdays.EveryDay,
            isDefault = true,
            mode = DayMode.NORMAL,
            sortOrder = 0,
        ),
    ).getOrNull()

    /**
     * One at a time, so each row can point at the one before it.
     *
     * A line whose write fails is skipped rather than aborting the import. Ten of
     * eleven steps imported is a routine somebody can finish by hand; nothing
     * imported is a blank screen and a retype.
     */
    private suspend fun writeItems(parsed: List<ParsedItem>, templateId: Long) {
        var previousId: Long? = null

        parsed.forEachIndexed { index, source ->
            val anchor = resolveAnchor(source.anchor, previousId)
            val written = items.upsert(toItem(source, anchor, templateId, index)).getOrNull()

            if (written != null) previousId = written
        }
    }

    /**
     * Points a parsed offset at the row above it.
     *
     * With nothing above it, the offset becomes a fixed time at that offset from
     * midnight. That is visibly odd rather than silently broken, which is the
     * right failure: the parser already refuses an offset on the first line, so
     * reaching here means the row above failed to write.
     */
    private fun resolveAnchor(anchor: Anchor, previousId: Long?): Anchor {
        if (anchor !is Anchor.Relative || anchor.parentItemId != PlanTextParser.PARENT_UNRESOLVED) return anchor

        return previousId?.let { anchor.copy(parentItemId = it) }
            ?: Anchor.Fixed(java.time.LocalTime.MIDNIGHT.plusMinutes(anchor.offset.inWholeMinutes))
    }

    private fun toItem(
        source: ParsedItem,
        anchor: Anchor,
        templateId: Long,
        index: Int,
    ) = Item(
        id = 0,
        templateId = templateId,
        blockId = null,
        kind = ItemKind.DO,
        title = source.title,
        // The section heading the line sat under, kept as the detail line. It is
        // the only thing the text said about grouping and throwing it away would
        // lose the one hint the user gave.
        detail = source.section,
        anchor = anchor,
        duration = source.duration,
        salience = source.salience ?: DEFAULT_SALIENCE,
        weekdays = Weekdays.EveryDay,
        pinned = source.pinned,
        minimum = source.minimumTitle?.let { MinimumVersion(title = it) },
        valueKind = ValueKind.NONE,
        bundleUri = null,
        trackId = null,
        sortOrder = index,
        archivedAt = null,
    )
}
