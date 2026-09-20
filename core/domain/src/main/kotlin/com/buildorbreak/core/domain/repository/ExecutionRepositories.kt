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
import java.time.LocalDateTime
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow

/** What actually happened. architecture.md section 5.2. */
interface OccurrenceRepository {
    fun observeForDate(date: LocalDate): Flow<List<Occurrence>>

    /**
     * One row by id, whatever date it belongs to.
     *
     * The alarm receiver and the alarm screen look a step up by the id the
     * alarm carried, and a step snoozed at five to midnight belongs to a date
     * that is no longer today by the time it rings.
     */
    suspend fun byId(id: Long): Occurrence?

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

    /**
     * Returns a settled row to PENDING.
     *
     * Only ever called from an undo the user asked for. Nothing schedules
     * itself off the back of this: the reschedule pass that follows decides
     * whether the step still has a future, which on an overdue step it does
     * not, and that is correct.
     */
    suspend fun unsettle(id: Long): Outcome<Unit, DataError>

    suspend fun shift(id: Long, by: Duration): Outcome<Occurrence, DataError>

    /**
     * Brings a row's planned time back in line with what the resolver says now.
     *
     * The row was written with the time the resolver gave at first sight, and
     * the plan has moved since: the day was shifted, the item was edited, a
     * parent step happened late. The alarm is set from the row, so a row that
     * is not kept in step is an alarm at the wrong minute. This is the
     * "reconciled on every resolve" that architecture.md section 1 promises.
     */
    suspend fun replan(id: Long, plannedAt: LocalDateTime): Outcome<Unit, DataError>

    /**
     * Removes rows the plan no longer has a place for, if they are still open.
     *
     * A row for a step that was archived, moved to another weekday, or left
     * behind by a template switch is a step the user was never asked for.
     * Left in place it would ring anyway and then count as missed at the
     * close. Settled rows are never touched: those are history.
     */
    suspend fun discard(ids: List<Long>): Outcome<Unit, DataError>

    /**
     * Every occurrence in a date range, oldest first. Both ends inclusive.
     *
     * A one shot read rather than a flow, because the screens that need a range
     * (insights, export) read it once when they open. Observing four weeks of
     * rows would re emit the whole set on every completion for no reader.
     */
    suspend fun between(from: LocalDate, to: LocalDate): List<Occurrence>

    /**
     * Writes back rows that already happened, from a backup.
     *
     * Separate from [materialise] because that one creates the future: it
     * takes what the resolver said, forces every row to PENDING and drops
     * anything not scheduled. A restore is the opposite job. The rows are
     * settled history, their state is the whole point of keeping them, and
     * the timeline-only ones are part of what the day looked like.
     *
     * Ids are assigned fresh. The ones in the file belonged to a database
     * that no longer exists, and nothing outside the file refers to them.
     */
    suspend fun restore(rows: List<Occurrence>): Outcome<Unit, DataError>
}

interface DayLogRepository {
    /** Which template ran, and how far the whole day was shifted. */
    fun observe(date: LocalDate): Flow<DayLog?>

    suspend fun upsert(log: DayLog): Outcome<Unit, DataError>

    suspend fun setShift(date: LocalDate, shift: Duration): Outcome<Unit, DataError>
}

interface MeasurementRepository {
    fun observeForItem(itemId: Long): Flow<List<Measurement>>

    /**
     * The series a NUMBER goal smooths. Ordered by date, oldest first.
     *
     * [itemId] narrows it to one step. Two steps that both record a weight
     * are two series, and a goal that follows one of them must not have the
     * other averaged into it.
     */
    suspend fun readings(
        kind: ValueKind,
        from: LocalDate,
        to: LocalDate,
        itemId: Long? = null,
    ): List<Reading>

    /** The same series, watched. Today's weigh in appears the moment it is typed. */
    fun observeReadings(
        kind: ValueKind,
        from: LocalDate,
        to: LocalDate,
        itemId: Long? = null,
    ): Flow<List<Reading>>

    /**
     * The whole series, newest first, with the id each row was written under.
     *
     * [observeReadings] deliberately drops the id, because the average does
     * not care which row a number came from. A screen that lets somebody
     * correct a weigh in does: it has to write back to the same row rather
     * than add a second one for the same morning.
     */
    fun observeSeries(kind: ValueKind, itemId: Long? = null): Flow<List<Measurement>>

    suspend fun upsert(measurement: Measurement): Outcome<Unit, DataError>

    /** Removes one reading. Used when a number was typed that never happened. */
    suspend fun delete(measurementId: Long): Outcome<Unit, DataError>

    /** Always optional, always after the fact. Never required to settle a day. */
    suspend fun recordSkipReason(reason: SkipReason): Outcome<Unit, DataError>

    /** Removes whatever was said about one skip. Used when the skip itself is undone. */
    suspend fun clearSkipReason(occurrenceId: Long): Outcome<Unit, DataError>

    /**
     * Removes the number logged against one settle.
     *
     * Used when that settle is undone, and before a fresh number is written
     * for the same settle: a weigh in typed as 720 is corrected by typing 72,
     * not by having both of them averaged.
     */
    suspend fun clearMeasurementFor(occurrenceId: Long): Outcome<Unit, DataError>

    /**
     * The reasons given for a set of skips.
     *
     * Read by occurrence rather than by date because the reason has no date of
     * its own: it belongs to the skip, and the skip already knows which day it
     * was. Two sources of truth for one date is one too many.
     */
    suspend fun skipReasonsFor(occurrenceIds: List<Long>): List<SkipReason>
}
