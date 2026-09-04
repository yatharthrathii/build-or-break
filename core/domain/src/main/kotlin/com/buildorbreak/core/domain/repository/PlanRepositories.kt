package com.buildorbreak.core.domain.repository

import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.domain.error.DomainError.DataError
import com.buildorbreak.core.model.plan.Block
import com.buildorbreak.core.model.plan.DayTemplate
import com.buildorbreak.core.model.plan.Item
import com.buildorbreak.core.model.plan.Plan
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * The stored plan. architecture.md section 5.2.
 *
 * Reads return a [Flow], writes are `suspend` and return an [Outcome]. A
 * repository never contains business logic: it reads, writes and maps. An `if`
 * about the product inside one of these belongs in a domain service instead.
 */
interface PlanRepository {
    /** Only one plan is active at a time on the free tier. */
    fun observeActive(): Flow<Plan?>

    fun observeAll(): Flow<List<Plan>>

    suspend fun upsert(plan: Plan): Outcome<Long, DataError>

    suspend fun setActive(planId: Long): Outcome<Unit, DataError>
}

interface TemplateRepository {
    fun observeForPlan(planId: Long): Flow<List<DayTemplate>>

    /**
     * The template whose weekday mask covers [date], or the plan default.
     * Choosing between them is a lookup, not a decision, which is why it can
     * live here.
     */
    suspend fun defaultFor(planId: Long, date: LocalDate): DayTemplate?

    suspend fun upsert(template: DayTemplate): Outcome<Long, DataError>

    suspend fun delete(templateId: Long): Outcome<Unit, DataError>
}

interface ItemRepository {
    /** The single Today query. See architecture.md section 6.1. */
    fun observeForTemplate(templateId: Long): Flow<List<Item>>

    fun observeBlocksForTemplate(templateId: Long): Flow<List<Block>>

    suspend fun upsert(item: Item): Outcome<Long, DataError>

    suspend fun upsertBlock(block: Block): Outcome<Long, DataError>

    /** Archived rather than deleted, so past occurrences keep their meaning. */
    suspend fun archive(itemId: Long): Outcome<Unit, DataError>
}
