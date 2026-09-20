package com.buildorbreak.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/**
 * One movement of points the daily close cannot account for.
 *
 * Deliberately not a balance. A stored total is a number that can drift from
 * the rows it was added up from, and there is no way to tell which of the two
 * is wrong afterwards. Every row here is a fact with a date on it, and the
 * balance is worked out from them each time.
 *
 * Indexed on date because the streak asks "was this day frozen" once per day
 * of the run, and on reason because the ad grant asks "how many today".
 */
@Entity(
    tableName = "point_entry",
    indices = [Index("date"), Index("reason")],
)
data class PointEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Instant,
    /** The day the entry is about, which is not always the day it was written. */
    val date: LocalDate,
    @ColumnInfo(name = "delta") val delta: Int,
    val reason: String,
)
