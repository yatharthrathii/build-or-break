package com.buildorbreak.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.buildorbreak.core.data.entity.BlockEntity
import com.buildorbreak.core.data.entity.ItemEntity
import java.time.Instant
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {

    /**
     * The single Today query. architecture.md section 6.1.
     *
     * Archived rows are excluded here rather than filtered by the caller,
     * because every caller wants the same thing and one that forgot would show
     * somebody a step they deleted three months ago.
     */
    @Query(
        """
        SELECT * FROM item
        WHERE template_id = :templateId AND archived_at IS NULL
        ORDER BY sort_order, id
        """,
    )
    fun observeForTemplate(templateId: Long): Flow<List<ItemEntity>>

    @Query("SELECT * FROM block WHERE template_id = :templateId ORDER BY sort_order, id")
    fun observeBlocksForTemplate(templateId: Long): Flow<List<BlockEntity>>

    @Query("SELECT * FROM item WHERE template_id = :templateId AND archived_at IS NULL ORDER BY sort_order, id")
    suspend fun forTemplate(templateId: Long): List<ItemEntity>

    @Query("SELECT * FROM item WHERE id = :id")
    suspend fun byId(id: Long): ItemEntity?

    /** Everything hanging off this one, for the reschedule pass after a change. */
    @Query("SELECT * FROM item WHERE anchor_parent_item_id = :parentId AND archived_at IS NULL")
    suspend fun childrenOf(parentId: Long): List<ItemEntity>

    @Upsert
    suspend fun upsert(item: ItemEntity): Long

    @Upsert
    suspend fun upsertBlock(block: BlockEntity): Long

    @Upsert
    suspend fun upsertAll(items: List<ItemEntity>)

    /**
     * Archived, never deleted. Occurrences point at items, and a completed step
     * that lost its title is a hole in the history rather than a tidy up.
     */
    @Query("UPDATE item SET archived_at = :at WHERE id = :id")
    suspend fun archive(id: Long, at: Instant)
}
