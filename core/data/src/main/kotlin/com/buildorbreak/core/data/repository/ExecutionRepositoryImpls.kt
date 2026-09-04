package com.buildorbreak.core.data.repository

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.data.dao.DayLogDao
import com.buildorbreak.core.data.dao.MeasurementDao
import com.buildorbreak.core.data.mapper.toEntity
import com.buildorbreak.core.data.mapper.toModel
import com.buildorbreak.core.data.mapper.toReading
import com.buildorbreak.core.domain.error.DomainError.DataError
import com.buildorbreak.core.domain.repository.DayLogRepository
import com.buildorbreak.core.domain.repository.MeasurementRepository
import com.buildorbreak.core.model.enums.DayMode
import com.buildorbreak.core.model.enums.ValueKind
import com.buildorbreak.core.model.execution.DayLog
import com.buildorbreak.core.model.execution.Measurement
import com.buildorbreak.core.model.execution.SkipReason
import com.buildorbreak.core.model.goal.Reading
import java.time.LocalDate
import javax.inject.Inject
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class MeasurementRepositoryImpl @Inject constructor(
    private val measurements: MeasurementDao,
    private val dispatchers: AppDispatchers,
) : MeasurementRepository {

    override fun observeForItem(itemId: Long): Flow<List<Measurement>> =
        measurements.observeForItem(itemId).map { rows -> rows.map { it.toModel() } }.flowOn(dispatchers.io)

    override suspend fun readings(kind: ValueKind, from: LocalDate, to: LocalDate): List<Reading> =
        withContext(dispatchers.io) {
            measurements.readings(kind.name, from, to).map { it.toReading() }
        }

    override suspend fun upsert(measurement: Measurement): Outcome<Unit, DataError> =
        sqlOutcome(dispatchers.io) { measurements.upsert(measurement.toEntity()) }

    override suspend fun recordSkipReason(reason: SkipReason): Outcome<Unit, DataError> =
        sqlOutcome(dispatchers.io) { measurements.upsertSkipReason(reason.toEntity()) }
}

class DayLogRepositoryImpl @Inject constructor(
    private val logs: DayLogDao,
    private val dispatchers: AppDispatchers,
) : DayLogRepository {

    override fun observe(date: LocalDate): Flow<DayLog?> =
        logs.observe(date).map { it?.toModel() }.flowOn(dispatchers.io)

    override suspend fun upsert(log: DayLog): Outcome<Unit, DataError> =
        sqlOutcome(dispatchers.io) { logs.upsert(log.toEntity()) }

    /**
     * A day that has been moved is a `SHIFTED` day, and writing both together is
     * what stops the two from disagreeing. A screen reading a ninety minute shift
     * on a day still marked `NORMAL` would have to decide which of them to
     * believe, and there is no right answer to that question.
     *
     * A shift back to zero returns the day to `NORMAL` for the same reason.
     */
    override suspend fun setShift(date: LocalDate, shift: Duration): Outcome<Unit, DataError> =
        sqlOutcome(dispatchers.io) {
            val minutes = shift.inWholeMinutes.toInt()
            val mode = if (minutes == 0) DayMode.NORMAL else DayMode.SHIFTED

            logs.setShift(date, minutes, mode.name)
        }
}
