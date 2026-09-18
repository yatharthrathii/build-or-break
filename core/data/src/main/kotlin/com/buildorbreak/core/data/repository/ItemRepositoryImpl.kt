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
import kotlinx.coroutines.withContext

class ItemRepositoryImpl @Inject constructor(
    private val items: ItemDao,
    private val time: TimeProvider,
    private val dispatchers: AppDispatchers,
) : ItemRepository {

    override fun observeForTemplate(templateId: Long): Flow<List<Item>> =
        items.observeForTemplate(templateId).map { rows -> rows.map { it.toModel() } }.flowOn(dispatchers.io)

    override fun observeBlocksForTemplate(templateId: Long): Flow<List<Block>> =
        items.observeBlocksForTemplate(templateId).map { rows -> rows.map { it.toModel() } }.flowOn(dispatchers.io)

    override suspend fun allForTemplate(templateId: Long): List<Item> = withContext(dispatchers.io) {
        items.allForTemplate(templateId).map { it.toModel() }
    }

    override suspend fun byId(itemId: Long): Item? = withContext(dispatchers.io) { items.byId(itemId)?.toModel() }

    override suspend fun upsert(item: Item): Outcome<Long, DataError> =
        sqlOutcome(dispatchers.io) { items.upsert(item.toEntity()) }

    override suspend fun upsertBlock(block: Block): Outcome<Long, DataError> =
        sqlOutcome(dispatchers.io) { items.upsertBlock(block.toEntity()) }

    /**
     * Unlinks first, deletes second, in that order and never the other way.
     *
     * If the delete failed the steps are merely ungrouped, which is what the
     * user asked for and is recoverable. If the unlink failed after the delete
     * the steps would carry the id of a group that no longer exists, and an
     * archived step brought back later would come back inside a group nobody
     * can see. Archived steps are unlinked too, for the same reason.
     */
    override suspend fun deleteBlock(templateId: Long, blockId: Long): Outcome<Unit, DataError> =
        sqlOutcome(dispatchers.io) {
            items.unlinkBlock(blockId)
            items.deleteBlock(blockId)
        }

    /**
     * The archive time comes from the injected clock rather than from
     * `Instant.now`, which detekt fails the build over. It is not pedantry here:
     * a test that archives an item and then asserts what the day looks like has
     * to be able to control which side of midnight that happened on.
     */
    override suspend fun archive(itemId: Long): Outcome<Unit, DataError> =
        sqlOutcome(dispatchers.io) { items.archive(itemId, time.now()) }

    // One write per row rather than one transaction. A reorder cut off half
    // way leaves some rows renumbered and the rest as they were, which is a
    // list in almost the order the user gave, and the next drag rewrites all
    // of them anyway.
    override suspend fun reorder(orderedIds: List<Long>): Outcome<Unit, DataError> = sqlOutcome(dispatchers.io) {
        orderedIds.forEachIndexed { index, id -> items.setSortOrder(id, index) }
    }
}
