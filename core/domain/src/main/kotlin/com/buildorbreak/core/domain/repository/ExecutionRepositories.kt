package com.buildorbreak.core.domain.repository

import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.domain.error.DomainError.DataError
import com.buildorbreak.core.model.enums.OccurrenceState
import com.buildorbreak.core.model.enums.ValueKind
import com.buildorbreak.core.model.execution.DayLog
import com.buildorbreak.core.model.execution.Measurement
import com.buildorbreak.core.model.execution.Occurrence
import com.buildorbreak.core.model.execution.SkipReason
import com.buildorbreak.core.model.goal.Reading
import com.buildorbreak.core.model.resolved.ResolvedEntry
import java.time.Instant
import java.time.LocalDate
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow

/** What actually happened. architecture.md section 5.2. */
interface OccurrenceRepository {
    fun observeForDate(date: LocalDate): Flow<List<Occurrence>>

    /**
     * Creates the rows an alarm can point at.
     *
     * Takes resolved entries rather than items, which is a deliberate departure
     * from the signature sketched in architecture.md section 5.2. `plannedAt` is
     * defined on the model as what the resolver said at scheduling time, so the
     * resolver has to have run: a repository that took bare items would have to
     * work out the times itself, which is a second copy of the timeline engine
     * living in the data layer and free to disagree with the first.
     *
     * Idempotent. Calling it twice for the same date must not produce a second
     * set, because the reschedule pass runs on app open, on boot, on timezone
     * change and on every completion, sometimes twice in a second.
     */
    suspend fun materialise(entries: List<ResolvedEntry>, date: LocalDate): Outcome<Unit, DataError>

    suspend fun settle(id: Long, state: OccurrenceState, at: Instant): Outcome<Unit, DataError>

    suspend fun shift(id: Long, by: Duration): Outcome<Occurrence, DataError>

    /** Drives the reconcile pass: anything that should have fired and did not. */
    suspend fun pendingBefore(instant: Instant): List<Occurrence>
}

interface DayLogRepository {
    /** Which template ran, and how far the whole day was shifted. */
    fun observe(date: LocalDate): Flow<DayLog?>

    suspend fun upsert(log: DayLog): Outcome<Unit, DataError>

    suspend fun setShift(date: LocalDate, shift: Duration): Outcome<Unit, DataError>
}

interface MeasurementRepository {
    fun observeForItem(itemId: Long): Flow<List<Measurement>>

    /** The series a NUMBER goal smooths. Ordered by date, oldest first. */
    suspend fun readings(kind: ValueKind, from: LocalDate, to: LocalDate): List<Reading>

    suspend fun upsert(measurement: Measurement): Outcome<Unit, DataError>

    /** Always optional, always after the fact. Never required to settle a day. */
    suspend fun recordSkipReason(reason: SkipReason): Outcome<Unit, DataError>
}
