package com.buildorbreak.core.data.demo

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.time.TimeProvider
import com.buildorbreak.core.data.database.BuildOrBreakDatabase
import com.buildorbreak.core.domain.goal.DayQualityClassifier
import com.buildorbreak.core.domain.repository.DayCloseRepository
import com.buildorbreak.core.domain.repository.MeasurementRepository
import com.buildorbreak.core.domain.repository.OccurrenceRepository
import com.buildorbreak.core.domain.repository.PlanRepository
import com.buildorbreak.core.domain.resolver.TimelineResolver
import com.buildorbreak.core.domain.usecase.ObserveTodayUseCase
import com.buildorbreak.core.model.enums.OccurrenceState
import com.buildorbreak.core.model.enums.SkipChip
import com.buildorbreak.core.model.execution.Occurrence
import com.buildorbreak.core.model.execution.SkipReason
import com.buildorbreak.core.model.goal.DayClose
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Twelve weeks of invented history, for looking at the review screens.
 *
 * **This file is temporary and is meant to be deleted.** Insights, streaks and
 * the weekly suggestion are all about patterns over weeks, and none of them can
 * be judged on a plan that is two days old. Rather than wait three months to
 * find out that a chart is unreadable, the history is invented once and thrown
 * away afterwards.
 *
 * Everything it writes is confined to dates before today, and [clear] removes
 * exactly that. Nothing here touches the plan, the templates or the steps, so
 * clearing it leaves the real routine exactly as it was.
 *
 * To remove the feature entirely: delete this file and the demo section in the
 * settings screen. Nothing else refers to it.
 */
class DemoHistory @Inject constructor(
    private val sources: DemoSources,
    private val database: BuildOrBreakDatabase,
    private val time: TimeProvider,
    private val dispatchers: AppDispatchers,
) {

    private val today get() = sources.today
    private val resolver get() = sources.resolver
    private val plans get() = sources.plans
    private val occurrences get() = sources.occurrences
    private val measurements get() = sources.measurements
    private val closes get() = sources.closes
    private val quality get() = sources.quality

    /**
     * Writes [days] of plausible history ending yesterday.
     *
     * Plausible means two things, and the second is the one that matters.
     *
     * The **day** has a shape: some weeks go well, one goes badly, weekends are
     * worse than weekdays. That is enough to make the bar chart look like a
     * life rather than like noise.
     *
     * The **steps** have characters. Evenly scattered randomness gives every
     * step the same score, and a review screen fed that has no best step, no
     * worst step, no reason that comes up more than the others and nothing to
     * suggest. It renders, and it says nothing, which looks exactly like a
     * broken feature. So one step is kept religiously, one is always late, one
     * is crowded out on the same weekdays every week, one is simply forgotten,
     * and one has been quietly falling apart. See [Character].
     *
     * Seeded from the date and the step, so running it twice writes the same
     * history rather than two different ones.
     */
    suspend fun seed(days: Int = DEFAULT_DAYS): Int = withContext(dispatchers.io) {
        val planId = plans.observeActive().first()?.id ?: return@withContext 0
        val cast = mutableMapOf<Long, Character>()
        var written = 0

        var date = time.today().minusDays(days.toLong())
        while (date.isBefore(time.today())) {
            written += seedDay(date, planId, cast)
            date = date.plusDays(1)
        }

        written
    }

    /** Removes every invented day. Today and the real plan are untouched. */
    suspend fun clear(): Int = withContext(dispatchers.io) {
        val cutoff = time.today().toEpochDay()
        val db = database.openHelper.writableDatabase

        db.execSQL(
            "DELETE FROM skip_reason WHERE occurrence_id IN (SELECT id FROM occurrence WHERE date < ?)",
            arrayOf<Any>(cutoff),
        )
        db.execSQL("DELETE FROM occurrence WHERE date < ?", arrayOf<Any>(cutoff))
        // The closes go with them. Left behind they would keep every adherence
        // figure on the insights screen pointing at days that no longer exist.
        db.execSQL("DELETE FROM day_close WHERE date < ?", arrayOf<Any>(cutoff))

        database.invalidationTracker.refreshVersionsAsync()

        0
    }

    private suspend fun seedDay(date: LocalDate, planId: Long, cast: MutableMap<Long, Character>): Int {
        // The plan start cuts a day short, which is right for the real timeline
        // and wrong for invented history: the point is to fill weeks the plan
        // did not exist for.
        val input = today.inputFor(date)?.copy(date = date, startedAt = null) ?: return 0
        val entries = resolver.resolve(input).entries
        if (entries.isEmpty()) return 0

        occurrences.materialise(entries, date)

        val rows = occurrences.observeForDate(date).first()
        castOnce(cast, rows)
        rows.forEach { row -> settle(row, cast.getValue(row.itemId), date) }

        closeDay(date, planId)

        return rows.size
    }

    /**
     * Hands each step its part, once, and keeps it for the whole run.
     *
     * By position in the day rather than by id, so the first step of the
     * morning is the dependable one whichever plan this is run against.
     */
    private fun castOnce(cast: MutableMap<Long, Character>, rows: List<Occurrence>) {
        if (cast.isNotEmpty()) return

        rows.map { it.itemId }.distinct().sorted()
            .forEachIndexed { index, itemId -> cast[itemId] = ROLES[index % ROLES.size] }
    }

    private suspend fun settle(row: Occurrence, character: Character, date: LocalDate) {
        val random = Random(date.toEpochDay() * SEED_SPREAD + row.itemId)
        val outcome = outcomeFor(character, date, random)
        val at = doneAt(row.plannedAt, outcome.slipMinutes)

        occurrences.settle(row.id, outcome.state, at)

        outcome.chip?.let { chip ->
            measurements.recordSkipReason(
                SkipReason(id = 0, occurrenceId = row.id, chip = chip, text = null, createdAt = at),
            )
        }
    }

    /**
     * A close per invented day, which is not optional.
     *
     * Every adherence figure on the insights screen, the week over week
     * comparison and the whole choice of story are read from these rows and
     * from nothing else. History with no closes renders a screen that is
     * technically working and permanently says it has nothing to tell you.
     */
    private suspend fun closeDay(date: LocalDate, planId: Long) {
        val rows = occurrences.observeForDate(date).first()
        val done = rows.count { it.state == OccurrenceState.DONE }
        val minimum = rows.count { it.state == OccurrenceState.DONE_MINIMUM }

        closes.upsert(
            DayClose(
                date = date,
                planId = planId,
                itemsDone = done,
                itemsMinimum = minimum,
                itemsMissed = rows.count { it.state == OccurrenceState.MISSED || it.state == OccurrenceState.SKIPPED },
                itemsTotal = rows.size,
                quality = quality.classify(done, minimum, rows.size),
                closedAt = doneAt(date.atTime(END_OF_DAY_HOUR, 0), 0),
            ),
        )
    }

    /** What one step did on one day, and what it left behind. */
    private data class Outcome(val state: OccurrenceState, val slipMinutes: Int, val chip: SkipChip? = null)

    private fun outcomeFor(character: Character, date: LocalDate, random: Random): Outcome {
        val roll = random.nextInt(PERCENT)

        return when (character) {
            Character.RELIABLE -> reliable(roll, random)
            Character.LATE -> late(roll, random)
            Character.CROWDED -> crowded(date, random)
            Character.FORGOTTEN -> forgotten(roll, random)
            Character.FADING -> fading(date, roll, random)
            Character.ORDINARY -> ordinary(date, roll, random)
        }
    }

    private fun reliable(roll: Int, random: Random) =
        if (roll < RELIABLE_ODDS) done(random, early = 4, late = 5) else minimum(random)

    private fun late(roll: Int, random: Random): Outcome = when {
        roll < LATE_ODDS -> Outcome(OccurrenceState.DONE, LATE_FLOOR + random.nextInt(LATE_SPAN))
        roll < LATE_ODDS + MINIMUM_ODDS -> minimum(random)
        else -> Outcome(OccurrenceState.MISSED, 0)
    }

    /**
     * The one with a shape rather than a rate.
     *
     * Four evenings a week something else is already happening, every week,
     * which is what a weekday cluster looks like and the thing the pattern
     * detector exists to find.
     */
    private fun crowded(date: LocalDate, random: Random): Outcome = if (date.dayOfWeek in CROWDED_DAYS) {
        Outcome(OccurrenceState.SKIPPED, 0, SkipChip.WORK_CAME_UP)
    } else {
        done(random, early = 5, late = 11)
    }

    private fun forgotten(roll: Int, random: Random): Outcome = if (roll < FORGOTTEN_ODDS) {
        done(random, early = 5, late = 21)
    } else {
        Outcome(OccurrenceState.SKIPPED, 0, SkipChip.FORGOT)
    }

    private fun fading(date: LocalDate, roll: Int, random: Random): Outcome {
        val keep = fadingOdds(date)

        return when {
            roll < keep -> done(random, early = 5, late = 26)
            roll < keep + FADING_SKIP_ODDS -> Outcome(OccurrenceState.SKIPPED, 0, SkipChip.NOT_IN_MOOD)
            else -> Outcome(OccurrenceState.MISSED, 0)
        }
    }

    private fun ordinary(date: LocalDate, roll: Int, random: Random): Outcome {
        val keep = keepOddsFor(date)

        return when {
            roll < keep -> done(random, early = 11, late = 16)
            roll < keep + MINIMUM_ODDS -> minimum(random)
            roll < keep + MINIMUM_ODDS + SKIP_ODDS ->
                Outcome(OccurrenceState.SKIPPED, 0, ORDINARY_CHIPS[random.nextInt(ORDINARY_CHIPS.size)])

            else -> Outcome(OccurrenceState.MISSED, 0)
        }
    }

    private fun done(random: Random, early: Int, late: Int) =
        Outcome(OccurrenceState.DONE, random.nextInt(-early, late))

    private fun minimum(random: Random) = Outcome(OccurrenceState.DONE_MINIMUM, random.nextInt(0, MINIMUM_LATE))

    /**
     * A shape, not a flat probability.
     *
     * Good weeks, a bad one, a recovery, and weekends worse than weekdays
     * throughout. That is what makes the week over week comparison on the
     * insights screen show something rather than hovering at zero.
     */
    private fun keepOddsFor(date: LocalDate): Int {
        val base = when (weeksAgo(date)) {
            in 0..RECENT_WEEKS -> GOOD_ODDS
            BAD_WEEK -> BAD_ODDS
            else -> MIDDLING_ODDS
        }
        val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY

        return if (weekend) base - WEEKEND_DROP else base
    }

    /** Strong three months ago, poor now. One step quietly coming apart. */
    private fun fadingOdds(date: LocalDate): Int =
        FADING_FLOOR + (weeksAgo(date) * FADING_PER_WEEK).coerceAtMost(FADING_CEILING - FADING_FLOOR)

    private fun weeksAgo(date: LocalDate): Int =
        ((time.today().toEpochDay() - date.toEpochDay()) / DAYS_IN_WEEK).toInt()

    /**
     * Near the step time, late by a realistic few minutes.
     *
     * Against the planned time rather than a fixed hour. Settling a 06:40 step
     * at eight in the evening is what turned the average slip on the insights
     * screen into eight hundred minutes, which told nobody anything.
     */
    private fun doneAt(plannedAt: LocalDateTime, slipMinutes: Int) = plannedAt
        .plusMinutes(slipMinutes.toLong())
        .atZone(time.zone())
        .toInstant()

    /**
     * The part each step plays across the whole history.
     *
     * Every one of these exists to make one thing on the review screens have
     * something to show. Remove them and the screens still render; they simply
     * stop saying anything, which is harder to spot and worse.
     */
    private enum class Character {
        /** Almost never missed, almost never late. The win the review names. */
        RELIABLE,

        /** Kept, and always half an hour after it was meant to be. Fills the slip column. */
        LATE,

        /** Crowded out on the same weekdays every week. The weekday cluster. */
        CROWDED,

        /** Missed at random, and always because it was forgotten. The reminder story. */
        FORGOTTEN,

        /** Strong months ago, poor now. What makes a trend visible. */
        FADING,

        /** No story. Most of the day is this, or the day stops looking real. */
        ORDINARY,
    }

    private companion object {
        /** Twelve weeks. The month view reads four, and a trend needs several behind it. */
        const val DEFAULT_DAYS = 84
        const val PERCENT = 100
        const val DAYS_IN_WEEK = 7L
        const val SEED_SPREAD = 31L
        const val END_OF_DAY_HOUR = 23

        /**
         * The cast, in the order the day runs.
         *
         * Weighted towards ordinary on purpose. A day where every step has a
         * personality is not a day, it is a demo.
         */
        val ROLES = listOf(
            Character.RELIABLE,
            Character.ORDINARY,
            Character.LATE,
            Character.ORDINARY,
            Character.CROWDED,
            Character.ORDINARY,
            Character.FORGOTTEN,
            Character.FADING,
            Character.ORDINARY,
        )

        const val GOOD_ODDS = 78
        const val MIDDLING_ODDS = 66
        const val BAD_ODDS = 48
        const val RECENT_WEEKS = 1
        const val BAD_WEEK = 2
        const val WEEKEND_DROP = 9

        const val MINIMUM_ODDS = 7
        const val SKIP_ODDS = 8
        const val MINIMUM_LATE = 25

        const val RELIABLE_ODDS = 96

        const val LATE_ODDS = 88
        const val LATE_FLOOR = 22
        const val LATE_SPAN = 24

        /** Tuesday, Thursday, Saturday and Sunday. Something else is always on. */
        val CROWDED_DAYS = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

        const val FORGOTTEN_ODDS = 62

        const val FADING_FLOOR = 44
        const val FADING_CEILING = 92
        const val FADING_PER_WEEK = 5
        const val FADING_SKIP_ODDS = 30

        val ORDINARY_CHIPS = listOf(
            SkipChip.NO_TIME,
            SkipChip.WORK_CAME_UP,
            SkipChip.UNWELL,
            SkipChip.TRAVELLING,
        )
    }
}

/**
 * Everything the seeder reads and writes, in one injectable bag.
 *
 * They always travel together and none of them is interesting on its own here.
 * Injecting them one by one gave [DemoHistory] a constructor nobody could read,
 * and this is throwaway code that should not be the reason a rule gets relaxed.
 */
class DemoSources @Inject constructor(
    val today: ObserveTodayUseCase,
    val resolver: TimelineResolver,
    val plans: PlanRepository,
    val occurrences: OccurrenceRepository,
    val measurements: MeasurementRepository,
    val closes: DayCloseRepository,
    val quality: DayQualityClassifier,
)
