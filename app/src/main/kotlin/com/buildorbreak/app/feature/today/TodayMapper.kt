package com.buildorbreak.app.feature.today

import com.buildorbreak.app.format.ClockFormat
import com.buildorbreak.core.common.time.TimeProvider
import com.buildorbreak.core.domain.goal.GoalSnapshot
import com.buildorbreak.core.domain.goal.Points
import com.buildorbreak.core.domain.goal.PointsTally
import com.buildorbreak.core.domain.review.CatchUpViewBuilder
import com.buildorbreak.core.domain.usecase.PlanContents
import com.buildorbreak.core.model.enums.DayMode
import com.buildorbreak.core.model.enums.DeliveryTier
import com.buildorbreak.core.model.enums.OccurrenceState
import com.buildorbreak.core.model.enums.Salience
import com.buildorbreak.core.model.enums.ValueKind
import com.buildorbreak.core.model.execution.Occurrence
import com.buildorbreak.core.model.plan.Anchor
import com.buildorbreak.core.model.resolved.BudgetWarning
import com.buildorbreak.core.model.resolved.ResolvedDay
import com.buildorbreak.core.model.resolved.ResolvedEntry
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject
import kotlinx.collections.immutable.toImmutableList

/**
 * Built per read rather than held in a constant.
 *
 * A formatter that captured the locale once would keep formatting in English
 * after somebody switched the phone to Hindi, and would keep doing it until the
 * process was killed. The allocation is nothing next to resolving a whole day.
 */
private val DATE: DateTimeFormatter
    get() = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

/** How early a step may be ticked off. Long enough for real life, short enough to mean something. */
private const val EARLY_GRACE_MINUTES = 15L

/**
 * Turns a resolved day into what the screen draws.
 *
 * Formatting only. Every number here already exists on `ResolvedDay` or an
 * `Occurrence`; this reads them off and turns times into strings, so a leaf
 * never needs a locale, a zone or a formatter to draw itself. Kept out of the
 * ViewModel so the ViewModel is left with collecting and calling.
 */
/**
 * The things about a day that are true of the whole day rather than of a step.
 *
 * Passed as one value so the mapper keeps a signature somebody can read, and
 * so adding the next one of these does not mean touching every caller.
 */
data class DayFacts(
    val runDays: Int,
    val consistency: Consistency?,
    val degradedTier: DeliveryTier?,
    val goal: GoalSnapshot? = null,
    val points: PointsTally? = null,
)

/** Zero to one, drawn as zero to a hundred. */
private const val PERCENT = 100

/** "1 Sep", for the line under the goal number. */
private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

class TodayMapper @Inject constructor(
    private val time: TimeProvider,
    private val clock: ClockFormat,
    private val catchUp: CatchUpViewBuilder,
) {

    /**
     * [ahead] is what the user has just done and the database has not yet
     * said: an occurrence in it is treated as settled that way.
     */
    fun toUiState(
        day: ResolvedDay,
        now: LocalDateTime,
        ahead: Map<Long, OccurrenceState>,
        plan: PlanContents.Loaded?,
        facts: DayFacts,
    ): TodayUiState {
        val titles = day.entries.associate { it.item.id to it.item.title }
        val states = day.entries.associate { it.item.id to it.sequenceInDay to stateOf(it, ahead) }
        val settled = { entry: ResolvedEntry -> states[entry.item.id to entry.sequenceInDay]?.isSettled == true }
        // The first open step, even one whose time has passed. A step that was
        // done and not tapped is still the thing to settle, and the card is
        // the only place with a Done button.
        // A timeline note has no row, no alarm and no Done button. Left in
        // here it sat on the card for the rest of the evening with every
        // button disabled, and the day could never be finished around it.
        val next = day.entries.firstOrNull { !settled(it) && it.salience != Salience.TIMELINE }

        return TodayUiState(
            header = DayHeader(
                dateLine = day.date.format(DATE),
                templateName = day.template.name,
                clock = clock.format(now),
                doneCount = day.entries.count { states[it.item.id to it.sequenceInDay]?.isDone == true },
                total = day.entries.count { it.salience != Salience.TIMELINE },
            ),
            runDays = facts.runDays,
            consistency = facts.consistency,
            shiftMinutes = day.dayShift.inWholeMinutes.toInt(),
            movedCount = day.entries.count { !it.item.pinned && !settled(it) },
            next = next?.let { toNextUp(it, titles, now) },
            entries = day.entries.map { toEntry(it, titles, stateOf(it, ahead)) }.toImmutableList(),
            nowIndex = next?.let { day.entries.indexOf(it) } ?: -1,
            budget = day.budgetWarning?.let(::toBudgetNotice),
            // The step on the card is left out: it has its own Done button
            // right there, and offering it twice reads as the app having
            // lost count of its own day.
            catchUp = catchUpFor(day, now, next?.occurrence?.id ?: 0),
            degradedTier = facts.degradedTier,
            hasPlan = true,
            isReduced = day.mode == DayMode.REDUCED,
            reducedCount = day.entries.count { it.reduced },
            goal = facts.goal?.let(::toGoalHero),
            points = facts.points?.let { pointsFor(day, states.values, it) },
            startsTomorrow = day.entries.isEmpty() && day.hiddenBeforeStart > 0,
            templates = plan?.templates.orEmpty().map { DayChoice(it.id, it.name) }.toImmutableList(),
            currentTemplateId = day.template.id,
            planId = plan?.planId ?: 0,
        )
    }

    /**
     * The bank plus today, live.
     *
     * Today's figure is worked out from the same states the ring uses, so a
     * tap moves both at once. It is not written anywhere: the close writes
     * the row at midnight and the bank picks it up from there.
     */
    private fun pointsFor(day: ResolvedDay, states: Collection<OccurrenceState?>, tally: PointsTally) = PointsUi(
        banked = tally.banked,
        today = Points.of(
            done = states.count { it == OccurrenceState.DONE },
            minimum = states.count { it == OccurrenceState.DONE_MINIMUM },
            total = day.entries.count { it.salience != Salience.TIMELINE },
        ),
    )

    private fun catchUpFor(day: ResolvedDay, now: LocalDateTime, exclude: Long): CatchUpPanel? =
        catchUp.build(day, now, exclude)?.let { view ->
            val plannedAt = day.entries.associate { (it.item.id to it.sequenceInDay) to it.at }

            CatchUpPanel(
                steps = view.steps.map { step ->
                    CatchUpRow(
                        occurrenceId = step.occurrenceId,
                        itemId = step.itemId,
                        sequenceInDay = step.sequenceInDay,
                        title = step.title,
                        time = clock.format(step.at),
                        minutes = step.minutes,
                        moveByMinutes = plannedAt[step.itemId to step.sequenceInDay]
                            ?.let { ChronoUnit.MINUTES.between(it, step.at).toInt() }
                            ?: 0,
                        useMinimum = step.useMinimum,
                    )
                }.toImmutableList(),
                outOfTime = view.outOfTime.toImmutableList(),
                alsoMissed = view.alsoMissed.toImmutableList(),
            )
        }

    private fun toGoalHero(goal: GoalSnapshot) = GoalHeroUi(
        title = goal.goal.title,
        kind = goal.goal.kind,
        valueKind = goal.goal.valueKind,
        current = goal.current,
        target = goal.goal.targetValue,
        startValue = goal.goal.startValue,
        startDate = goal.goal.startDate.format(SHORT_DATE),
        changeSinceStart = goal.changeSinceStart,
        todayReading = goal.todayReading,
        percent = (goal.percent * PERCENT).toInt(),
        pacePercent = (goal.paceFraction * PERCENT).toInt(),
        standing = goal.standing,
        daysLeft = goal.daysLeft,
        daysElapsed = goal.daysElapsed,
        isFinished = goal.isFinished,
        hasData = goal.hasData,
        trail = goal.trail.toImmutableList(),
    )

    /** The smaller version's title on a sick day, the step's own otherwise. */
    private fun titleOf(entry: ResolvedEntry): String =
        if (entry.reduced) entry.item.minimum?.title ?: entry.item.title else entry.item.title

    private fun toNextUp(entry: ResolvedEntry, titles: Map<Long, String>, now: LocalDateTime) = NextUp(
        occurrenceId = entry.occurrence?.id ?: 0,
        itemId = entry.item.id,
        time = clock.format(entry.at),
        title = titleOf(entry),
        kind = kindOf(entry, titles),
        note = noteOf(entry),
        durationMinutes = entry.item.duration?.inWholeMinutes?.toInt(),
        hasMinimum = entry.item.hasMinimum && !entry.reduced,
        detail = entry.item.detail?.takeIf { it.isNotBlank() },
        measure = entry.item.valueKind.takeIf { it != ValueKind.NONE },
        isAlarm = entry.item.salience == Salience.ALARM,
        isReduced = entry.reduced,
        isOverdue = entry.at < now,
        // A few minutes of grace, because somebody who finishes the walk at
        // 06:58 for a 07:00 step should not be told to wait.
        hasArrived = !entry.at.minusMinutes(EARLY_GRACE_MINUTES).isAfter(now),
    )

    private fun toEntry(entry: ResolvedEntry, titles: Map<Long, String>, state: OccurrenceState?) = TimelineEntry(
        occurrenceId = entry.occurrence?.id ?: 0,
        itemId = entry.item.id,
        time = clock.format(entry.at),
        title = titleOf(entry),
        kind = kindOf(entry, titles),
        note = noteOf(entry),
        isDone = state?.isDone == true,
        isMissed = state?.isSettled == true && state.isDone != true,
        sequence = entry.sequenceInDay,
        isReduced = entry.reduced,
        isAlarm = entry.item.salience == Salience.ALARM,
    )

    /** The tap wins over the row until the row catches up. */
    private fun stateOf(entry: ResolvedEntry, ahead: Map<Long, OccurrenceState>): OccurrenceState? =
        entry.occurrence?.let { ahead[it.id] ?: it.state }

    private fun kindOf(entry: ResolvedEntry, titles: Map<Long, String>): EntryKind =
        when (val anchor = entry.item.anchor) {
            is Anchor.Fixed -> EntryKind.Fixed

            is Anchor.Relative -> EntryKind.After(
                parentTitle = titles[anchor.parentItemId].orEmpty(),
                offsetMinutes = anchor.offset.inWholeMinutes.toInt(),
            )

            is Anchor.Window -> EntryKind.Window(from = clock.format(anchor.from), to = clock.format(anchor.to))

            is Anchor.Interval -> EntryKind.Every(anchor.every.inWholeMinutes.toInt())
        }

    /**
     * The fact that matters most, in a fixed order.
     *
     * What happened beats what was planned: a done row says when it was done,
     * not that it was once moved. Below that, a move is more useful than a
     * pin, and a pin more useful than a window's end.
     */
    private fun noteOf(entry: ResolvedEntry): EntryNote? {
        val occurrence = entry.occurrence

        return if (occurrence?.isSettled == true) settledNote(entry, occurrence) else openNote(entry)
    }

    private fun settledNote(entry: ResolvedEntry, occurrence: Occurrence): EntryNote {
        val settledAt = occurrence.settledAt?.let { LocalDateTime.ofInstant(it, time.zone()) }

        return when {
            occurrence.isDone && settledAt != null -> EntryNote.DoneAt(
                time = clock.format(settledAt),
                overMinutes = ChronoUnit.MINUTES.between(entry.at, settledAt).toInt(),
            )

            occurrence.state == OccurrenceState.SKIPPED -> EntryNote.Skipped
            else -> EntryNote.Missed
        }
    }

    private fun openNote(entry: ResolvedEntry): EntryNote? {
        val occurrence = entry.occurrence
        val moved = occurrence?.let { ChronoUnit.MINUTES.between(it.plannedAt, entry.at).toInt() } ?: 0
        val snoozes = occurrence?.snoozeCount ?: 0
        val anchor = entry.item.anchor

        return when {
            entry.degraded -> EntryNote.Degraded
            entry.reduced -> EntryNote.Reduced
            moved != 0 && !entry.item.pinned -> EntryNote.Moved(moved)
            entry.item.pinned -> EntryNote.Pinned
            snoozes > 0 -> EntryNote.Snoozed(snoozes)
            anchor is Anchor.Window -> EntryNote.Ends(clock.format(anchor.to))
            else -> null
        }
    }

    private fun toBudgetNotice(warning: BudgetWarning) = BudgetNotice(
        alarms = warning.alarmCount,
        notifications = warning.notifyCount,
        alarmsOverBudget = warning.alarmCount > BudgetWarning.MAX_ALARMS,
    )
}
