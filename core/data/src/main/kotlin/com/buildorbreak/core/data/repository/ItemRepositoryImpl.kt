package com.buildorbreak.core.data.repository

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.common.time.TimeProvider
import com.buildorbreak.core.data.dao.ItemDao
import com.buildorbreak.core.data.mapper.toEntity
import com.buildorbreak.core.data.mapper.toModel
import com.buildorbreak.core.domain.error.DomainError.DataError
import com.buildorbreak.core.domain.repository.ItemRepository
import com.buildorbreak.core.model.plan.Block
import com.buildorbreak.core.model.plan.Item
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class ItemRepositoryImpl @Inject constructor(
    private val items: ItemDao,
    private val time: TimeProvider,
    private val dispatchers: AppDispatchers,
) : ItemRepository {

    override fun observeForTemplate(templateId: Long): Flow<List<Item>> =
        items.observeForTemplate(templateId).map { rows -> rows.map { it.toModel() } }.flowOn(dispatchers.io)

    override fun observeBlocksForTemplate(templateId: Long): Flow<List<Block>> =
        items.observeBlocksForTemplate(templateId).map { rows -> rows.map { it.toModel() } }.flowOn(dispatchers.io)

    override suspend fun upsert(item: Item): Outcome<Long, DataError> =
        sqlOutcome(dispatchers.io) { items.upsert(item.toEntity()) }

    override suspend fun upsertBlock(block: Block): Outcome<Long, DataError> =
        sqlOutcome(dispatchers.io) { items.upsertBlock(block.toEntity()) }

    /**
     * The archive time comes from the injected clock rather than from
     * `Instant.now`, which detekt fails the build over. It is not pedantry here:
     * a test that archives an item and then asserts what the day looks like has
     * to be able to control which side of midnight that happened on.
     */
    override suspend fun archive(itemId: Long): Outcome<Unit, DataError> =
        sqlOutcome(dispatchers.io) { items.archive(itemId, time.now()) }
}
