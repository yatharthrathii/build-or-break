package com.buildorbreak.core.domain.repository

import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.domain.error.DomainError.DataError
import com.buildorbreak.core.model.enums.PointReason
import com.buildorbreak.core.model.goal.PointEntry
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * Every point that moved for a reason the daily close does not know about.
 *
 * The close already decides what a day was worth, and that arithmetic is
 * never written down. This is the other half: what was granted on top and
 * what was taken off, each with the day it belongs to.
 */
interface PointLedgerRepository {

    /** The whole ledger, newest first. Small by nature: a few rows a week at most. */
    fun observeAll(): Flow<List<PointEntry>>

    /** Everything granted outside the daily close, and everything spent, as positives. */
    fun observeGranted(): Flow<Int>

    fun observeSpent(): Flow<Int>

    /** The days covered by a bought freeze, so the run can be worked out. */
    fun observeDatesFor(reason: PointReason): Flow<List<LocalDate>>

    /** How many entries of one reason exist on a date. The daily ad cap reads this. */
    suspend fun countFor(reason: PointReason, date: LocalDate): Int

    suspend fun add(entry: PointEntry): Outcome<Unit, DataError>
}
