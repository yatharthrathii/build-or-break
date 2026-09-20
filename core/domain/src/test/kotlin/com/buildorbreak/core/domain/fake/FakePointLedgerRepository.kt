package com.buildorbreak.core.domain.fake

import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.domain.error.DomainError.DataError
import com.buildorbreak.core.domain.repository.PointLedgerRepository
import com.buildorbreak.core.model.enums.PointReason
import com.buildorbreak.core.model.goal.PointEntry
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** The point ledger, in memory, newest first like the real one. */
class FakePointLedgerRepository : PointLedgerRepository {

    val entries = MutableStateFlow<List<PointEntry>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<PointEntry>> =
        entries.map { list -> list.sortedByDescending { it.at } }

    override fun observeGranted(): Flow<Int> =
        entries.map { list -> list.filter { it.delta > 0 }.sumOf { it.delta } }

    override fun observeSpent(): Flow<Int> =
        entries.map { list -> -list.filter { it.delta < 0 }.sumOf { it.delta } }

    override fun observeDatesFor(reason: PointReason): Flow<List<LocalDate>> =
        entries.map { list -> list.filter { it.reason == reason }.map { it.date } }

    override suspend fun countFor(reason: PointReason, date: LocalDate): Int =
        entries.value.count { it.reason == reason && it.date == date }

    override suspend fun add(entry: PointEntry): Outcome<Unit, DataError> {
        entries.value = entries.value + entry.copy(id = nextId++)

        return Outcome.Success(Unit)
    }
}
