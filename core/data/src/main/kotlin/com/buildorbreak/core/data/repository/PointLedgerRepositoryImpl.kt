package com.buildorbreak.core.data.repository

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.data.dao.PointEntryDao
import com.buildorbreak.core.data.entity.PointEntryEntity
import com.buildorbreak.core.domain.error.DomainError.DataError
import com.buildorbreak.core.domain.repository.PointLedgerRepository
import com.buildorbreak.core.model.enums.PointReason
import com.buildorbreak.core.model.goal.PointEntry
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class PointLedgerRepositoryImpl @Inject constructor(
    private val entries: PointEntryDao,
    private val dispatchers: AppDispatchers,
) : PointLedgerRepository {

    override fun observeAll(): Flow<List<PointEntry>> = entries.observeAll()
        .map { rows -> rows.mapNotNull { it.toModelOrNull() } }
        .flowOn(dispatchers.io)

    override fun observeGranted(): Flow<Int> = entries.observeGranted().map { it ?: 0 }.flowOn(dispatchers.io)

    /** Stored negative, reported positive, because "spent" is a size and not a direction. */
    override fun observeSpent(): Flow<Int> = entries.observeSpent().map { -(it ?: 0) }.flowOn(dispatchers.io)

    override fun observeDatesFor(reason: PointReason): Flow<List<LocalDate>> =
        entries.observeDatesFor(reason.name).flowOn(dispatchers.io)

    override suspend fun countFor(reason: PointReason, date: LocalDate): Int =
        withContext(dispatchers.io) { entries.countFor(reason.name, date) }

    override suspend fun add(entry: PointEntry): Outcome<Unit, DataError> =
        sqlOutcome(dispatchers.io) { entries.insert(entry.toEntity()) }
}

private fun PointEntry.toEntity() = PointEntryEntity(
    id = if (id == 0L) 0 else id,
    at = at,
    date = date,
    delta = delta,
    reason = reason.name,
)

/**
 * Null rather than a guess when the reason is one this build does not know.
 *
 * A row written by a later version and read back by an older one is the only
 * way this happens, and dropping it from the list is better than showing a
 * spend the app cannot name.
 */
private fun PointEntryEntity.toModelOrNull(): PointEntry? {
    val known = PointReason.entries.firstOrNull { it.name == reason } ?: return null

    return PointEntry(id = id, at = at, date = date, delta = delta, reason = known)
}
