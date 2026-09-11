package com.buildorbreak.core.data.repository

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.data.dao.OccurrenceDao
import com.buildorbreak.core.data.entity.OccurrenceEntity
import com.buildorbreak.core.data.mapper.toModel
import com.buildorbreak.core.domain.error.DomainError.DataError
import com.buildorbreak.core.domain.repository.OccurrenceRepository
import com.buildorbreak.core.model.enums.OccurrenceState
import com.buildorbreak.core.model.enums.Salience
import com.buildorbreak.core.model.execution.Occurrence
import com.buildorbreak.core.model.resolved.ResolvedEntry
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class OccurrenceRepositoryImpl @Inject constructor(
    private val occurrences: OccurrenceDao,
    private val dispatchers: AppDispatchers,
) : OccurrenceRepository {

    override fun observeForDate(date: LocalDate): Flow<List<Occurrence>> =
        occurrences.observeForDate(date).map { rows -> rows.map { it.toModel() } }.flowOn(dispatchers.io)

    override suspend fun byId(id: Long): Occurrence? = withContext(dispatchers.io) {
        occurrences.byId(id)?.toModel()
    }

    /**
     * Writes one PENDING row per entry, ignoring anything already there.
     *
     * `TIMELINE` entries are skipped: they are never handed to the scheduler, so
     * a row for one would be a row nothing ever points at, and it would count
     * against the day in every adherence figure without ever having been asked
     * of the user.
     *
     * The insert ignores conflicts rather than replacing, which is what makes
     * this safe to call on every app open. Replacing would wipe the state of a
     * step somebody had already completed that morning.
     */
    override suspend fun materialise(entries: List<ResolvedEntry>, date: LocalDate): Outcome<Unit, DataError> =
        sqlOutcome(dispatchers.io) {
            val rows = entries
                .filter { it.salience != Salience.TIMELINE }
                .map { entry ->
                    OccurrenceEntity(
                        itemId = entry.item.id,
                        date = date,
                        plannedAt = entry.at,
                        scheduledAt = null,
                        firedAt = null,
                        settledAt = null,
                        state = OccurrenceState.PENDING.name,
                        shiftMinutes = 0,
                        snoozeCount = 0,
                        sequenceInDay = entry.sequenceInDay,
                    )
                }

            occurrences.insertIgnoringExisting(rows)
        }

    override suspend fun settle(id: Long, state: OccurrenceState, at: Instant): Outcome<Unit, DataError> =
        sqlOutcome(dispatchers.io) { occurrences.settle(id, state.name, at) }

    override suspend fun unsettle(id: Long): Outcome<Unit, DataError> =
        sqlOutcome(dispatchers.io) { occurrences.settle(id, OccurrenceState.PENDING.name, at = null) }

    /**
     * Returns the row as it now stands rather than Unit.
     *
     * A snooze immediately needs rescheduling and a fresh cascade preview, and
     * both need the new shift. Handing it straight back removes a second read
     * that could see a different value if anything else touched the row.
     */
    override suspend fun shift(id: Long, by: Duration): Outcome<Occurrence, DataError> {
        val shifted = sqlOutcome(dispatchers.io) {
            occurrences.shift(id, by.inWholeMinutes.toInt(), OccurrenceState.SNOOZED.name)
            occurrences.byId(id)?.toModel()
        }

        // A row that is not there is a reason, not a crash. The catch-up panel
        // can offer a move for a step whose row has not been written yet, and
        // the answer to that is "not found", reported, rather than a process
        // taken down from inside a ViewModel.
        return when (shifted) {
            is Outcome.Success -> shifted.value?.let { Outcome.Success(it) } ?: Outcome.Failure(DataError.NotFound)
            is Outcome.Failure -> shifted
        }
    }

    override suspend fun replan(id: Long, plannedAt: LocalDateTime): Outcome<Unit, DataError> =
        sqlOutcome(dispatchers.io) { occurrences.replan(id, plannedAt) }

    override suspend fun discard(ids: List<Long>): Outcome<Unit, DataError> = sqlOutcome(dispatchers.io) {
        if (ids.isNotEmpty()) occurrences.deleteOpen(ids, OPEN_STATES)
    }

    override suspend fun between(from: LocalDate, to: LocalDate): List<Occurrence> = withContext(dispatchers.io) {
        occurrences.between(from, to).map { it.toModel() }
    }

    private companion object {
        val OPEN_STATES = OccurrenceState.entries.filterNot { it.isSettled }.map { it.name }
    }
}
