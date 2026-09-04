package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.time.TimeProvider
import com.buildorbreak.core.domain.repository.DayLogRepository
import com.buildorbreak.core.domain.repository.ItemRepository
import com.buildorbreak.core.domain.repository.OccurrenceRepository
import com.buildorbreak.core.domain.repository.PlanRepository
import com.buildorbreak.core.domain.repository.TemplateRepository
import com.buildorbreak.core.domain.resolver.ResolveInput
import com.buildorbreak.core.domain.resolver.TimelineResolver
import com.buildorbreak.core.model.plan.DayTemplate
import com.buildorbreak.core.model.resolved.ResolvedDay
import java.time.LocalDate
import javax.inject.Inject
import kotlin.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn

/**
 * The whole day, recomputed whenever anything it depends on changes.
 *
 * architecture.md section 6.1. Because the resolver is pure and fast there is no
 * loading state after the first emission and no invalidation logic to get wrong:
 * an edit to any input re emits the entire day, correctly, every time.
 *
 * Emits null when there is no active plan or no template that covers today. That
 * is a real state, not an error. A fresh install has neither, and the Today
 * screen needs to be able to tell the difference between an empty day and no
 * plan at all.
 */
class ObserveTodayUseCase @Inject constructor(
    private val plans: PlanRepository,
    private val templates: TemplateRepository,
    private val items: ItemRepository,
    private val occurrences: OccurrenceRepository,
    private val dayLogs: DayLogRepository,
    private val resolver: TimelineResolver,
    private val time: TimeProvider,
    private val dispatchers: AppDispatchers,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(date: LocalDate = time.today()): Flow<ResolvedDay?> =
        combine(plans.observeActive(), dayLogs.observe(date)) { plan, log -> plan to log }
            .flatMapLatest { (plan, log) ->
                if (plan == null) {
                    flowOf(null)
                } else {
                    templates.observeForPlan(plan.id).flatMapLatest { available ->
                        val template = chooseTemplate(available, log?.templateId, date)

                        if (template == null) flowOf(null) else resolveWith(template, log, date)
                    }
                }
            }
            // The resolver is CPU work, not IO, and it runs on every emission.
            .flowOn(dispatchers.default)

    /**
     * What the user chose, then what the weekday says, then the plan default.
     *
     * The stored choice wins outright. Somebody who tapped "sick day" this
     * morning has said something more specific than any mask can, and having the
     * app quietly put the office template back at midday would be the app
     * overruling them.
     */
    private fun chooseTemplate(available: List<DayTemplate>, chosenId: Long?, date: LocalDate): DayTemplate? =
        available.firstOrNull { it.id == chosenId }
            ?: available.firstOrNull { date.dayOfWeek in it.weekdays }
            ?: available.firstOrNull { it.isDefault }

    private fun resolveWith(
        template: DayTemplate,
        log: com.buildorbreak.core.model.execution.DayLog?,
        date: LocalDate,
    ): Flow<ResolvedDay> = combine(
        items.observeForTemplate(template.id),
        items.observeBlocksForTemplate(template.id),
        occurrences.observeForDate(date),
    ) { todaysItems, blocks, todaysOccurrences ->
        resolver.resolve(
            ResolveInput(
                template = template,
                blocks = blocks,
                items = todaysItems,
                occurrences = todaysOccurrences,
                date = date,
                zone = time.zone(),
                dayShift = log?.dayShift ?: Duration.ZERO,
                mode = log?.mode ?: template.mode,
            ),
        )
    }
}
