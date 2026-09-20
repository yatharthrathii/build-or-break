package com.buildorbreak.core.data.repository

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.data.dao.PlanDao
import com.buildorbreak.core.data.dao.TemplateDao
import com.buildorbreak.core.data.mapper.toEntity
import com.buildorbreak.core.data.mapper.toModel
import com.buildorbreak.core.domain.error.DomainError.DataError
import com.buildorbreak.core.domain.repository.PlanRepository
import com.buildorbreak.core.domain.repository.TemplateRepository
import com.buildorbreak.core.model.plan.DayTemplate
import com.buildorbreak.core.model.plan.Plan
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * A repository reads, writes and maps. Nothing else.
 *
 * architecture.md section 5.2 is explicit about this: an `if` about the product
 * inside one of these belongs in a domain service. The one thing that looks like
 * a decision below, picking a template for a date, is a lookup with a documented
 * order rather than a judgement, and it is written in SQL so it stays one query.
 */
class PlanRepositoryImpl @Inject constructor(
    private val plans: PlanDao,
    private val dispatchers: AppDispatchers,
) : PlanRepository {

    override fun observeActive(): Flow<Plan?> = plans.observeActive().map { it?.toModel() }.flowOn(dispatchers.io)

    override fun observeAll(): Flow<List<Plan>> =
        plans.observeAll().map { rows -> rows.map { it.toModel() } }.flowOn(dispatchers.io)

    override suspend fun upsert(plan: Plan): Outcome<Long, DataError> =
        sqlOutcome(dispatchers.io) { plans.upsert(plan.toEntity()) }

    override suspend fun setActive(planId: Long): Outcome<Unit, DataError> =
        sqlOutcome(dispatchers.io) { plans.setActive(planId) }
}

class TemplateRepositoryImpl @Inject constructor(
    private val templates: TemplateDao,
    private val dispatchers: AppDispatchers,
) : TemplateRepository {

    override fun observeForPlan(planId: Long): Flow<List<DayTemplate>> =
        templates.observeForPlan(planId).map { rows -> rows.map { it.toModel() } }.flowOn(dispatchers.io)

    /**
     * The template whose weekday mask covers the date, or the plan default.
     *
     * The bit is computed here rather than in SQL because the mask layout is a
     * model decision: Monday is bit zero, matching `DayOfWeek.getValue` minus
     * one. Spelling that out in a query string would put the same rule in two
     * places and let them drift.
     */
    override suspend fun defaultFor(planId: Long, date: LocalDate): DayTemplate? {
        val dayBit = 1 shl (date.dayOfWeek.value - 1)

        return (templates.matching(planId, dayBit) ?: templates.defaultFor(planId))?.toModel()
    }

    override suspend fun upsert(template: DayTemplate): Outcome<Long, DataError> =
        sqlOutcome(dispatchers.io) { templates.upsert(template.toEntity()) }

    override suspend fun delete(templateId: Long): Outcome<Unit, DataError> =
        sqlOutcome(dispatchers.io) { templates.delete(templateId) }
}
