package com.buildorbreak.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.buildorbreak.core.data.entity.DayTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {

    @Query("SELECT * FROM day_template WHERE plan_id = :planId ORDER BY sort_order, id")
    fun observeForPlan(planId: Long): Flow<List<DayTemplateEntity>>

    @Query("SELECT * FROM day_template WHERE id = :id")
    suspend fun byId(id: Long): DayTemplateEntity?

    /**
     * The template whose weekday mask covers this day, most specific first.
     *
     * A mask matching fewer days is the more deliberate choice: a rest day set
     * only for Sunday should win over a weekday template that also happens to
     * include it. The plan default is the fallback, and it is asked for
     * separately rather than folded in here so the two cases stay readable.
     */
    @Query(
        """
        SELECT * FROM day_template
        WHERE plan_id = :planId AND (weekdays & :dayBit) != 0
        ORDER BY sort_order, id
        LIMIT 1
        """,
    )
    suspend fun matching(planId: Long, dayBit: Int): DayTemplateEntity?

    @Query("SELECT * FROM day_template WHERE plan_id = :planId AND is_default = 1 LIMIT 1")
    suspend fun defaultFor(planId: Long): DayTemplateEntity?

    @Upsert
    suspend fun upsert(template: DayTemplateEntity): Long

    @Query("DELETE FROM day_template WHERE id = :id")
    suspend fun delete(id: Long)
}
