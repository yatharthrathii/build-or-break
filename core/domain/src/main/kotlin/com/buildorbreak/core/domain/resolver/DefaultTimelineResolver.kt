package com.buildorbreak.core.domain.resolver

import com.buildorbreak.core.model.enums.Salience
import com.buildorbreak.core.model.plan.Anchor
import com.buildorbreak.core.model.plan.Item
import com.buildorbreak.core.model.resolved.BudgetWarning
import com.buildorbreak.core.model.resolved.ResolveIssue
import com.buildorbreak.core.model.resolved.ResolvedDay
import com.buildorbreak.core.model.resolved.ResolvedEntry
import java.time.LocalDate
import java.time.LocalTime
import kotlin.time.Duration

/** Where a `RELATIVE` orphan lands when the plan has no fixed item to start from. */
private val DEFAULT_DAY_START = LocalTime.of(6, 0)

/** The last minute of a day. A shift never rolls an item onto another date. */
private val LAST_MINUTE = LocalTime.of(23, 59)

private const val SECONDS_PER_MINUTE = 60

/**
 * The nine steps, wired together. architecture.md section 13 step 5.
 *
 * ```
 * 1. filter   drop archived items and anything not on this weekday
 * 2. shift    move the day, leaving pinned items where they are
 * 3. graph    order RELATIVE items, cut cycles, find dangling parents
 * 4. place    give every item one concrete moment
 * 5. expand   turn each INTERVAL item into its repeats
 * 6. attach   pair each placement with its block and its occurrence
 * 7. sort     by time, then deterministically
 * 8. budget   count the noise the day would make
 * 9. issues   report everything that had to be worked around
 * ```
 *
 * Every step is a pure function over the step before it. There is no IO, no
 * clock, no Android and no database anywhere in this file, which is why the
 * whole engine tests in milliseconds, and why a day that renders wrong can
 * always be reproduced from its inputs alone.
 *
 * The collaborators are constructor defaults rather than injected requirements.
 * Production wiring never varies, and a test that wants to isolate one stage can
 * still substitute it.
 */
class DefaultTimelineResolver(
    private val graphBuilder: AnchorGraphBuilder = AnchorGraphBuilder(),
    private val anchors: AnchorResolver = AnchorResolver(),
    private val intervals: IntervalExpander = IntervalExpander(),
) : TimelineResolver {

    override fun resolve(input: ResolveInput): ResolvedDay {
        val applicable = applicableItems(input.items, input.date)
        val shifted = applyDayShift(applicable, input.dayShift)
        val graph = graphBuilder.build(shifted.items)
        val placements = placeAndExpand(shifted.items, graph, input)
        val entries = buildEntries(placements, shifted.items, input)

        return ResolvedDay(
            date = input.date,
            template = input.template,
            entries = entries,
            dayShift = input.dayShift,
            mode = input.mode,
            budgetWarning = budgetWarning(entries),
            issues = collectIssues(graph, shifted, placements),
        )
    }

    // 1. Filter --------------------------------------------------------------

    /**
     * An archived item is history, and an item that does not run today is not
     * part of today. Filtering first means every later stage only ever sees
     * items that genuinely belong to this date, so no stage has to ask again.
     */
    private fun applicableItems(items: List<Item>, date: LocalDate): List<Item> =
        items.filter { !it.isArchived && date.dayOfWeek in it.weekdays }

    // 2. Shift ---------------------------------------------------------------

    private data class Shifted(val items: List<Item>, val clamped: Set<Long>)

    /**
     * Woke up ninety minutes late, move the day, keep the gym slot where it is.
     *
     * The shift is applied to the anchors rather than to the resolved times,
     * because a `RELATIVE` item is already an offset from something that moved.
     * Shifting the anchor and then resolving lets a chain follow its parent
     * exactly once. Shifting the result would move every child twice.
     */
    private fun applyDayShift(items: List<Item>, shift: Duration): Shifted {
        if (shift == Duration.ZERO) return Shifted(items, emptySet())

        val minutes = shift.inWholeMinutes
        val clamped = mutableSetOf<Long>()

        val moved = items.map { item ->
            if (item.pinned) return@map item
            val anchor = shiftAnchor(item.anchor, minutes) { clamped += item.id }
            if (anchor == item.anchor) item else item.copy(anchor = anchor)
        }

        return Shifted(moved, clamped)
    }

    private fun shiftAnchor(anchor: Anchor, minutes: Long, onClamp: () -> Unit): Anchor = when (anchor) {
        is Anchor.Fixed -> anchor.copy(at = shiftTime(anchor.at, minutes, onClamp))

        is Anchor.Window -> anchor.copy(
            from = shiftTime(anchor.from, minutes, onClamp),
            to = shiftTime(anchor.to, minutes, onClamp),
        )

        is Anchor.Interval -> anchor.copy(
            from = shiftTime(anchor.from, minutes, onClamp),
            to = shiftTime(anchor.to, minutes, onClamp),
        )

        // Already an offset from something that moved. Leaving it alone is what
        // keeps "twenty minutes after the shake" true across a shift.
        is Anchor.Relative -> anchor
    }

    /**
     * Clamps rather than wraps.
     *
     * [LocalTime.plusMinutes] rolls 23:30 plus an hour round to 00:30, which
     * would silently move a late item to the start of the same morning. A day
     * that runs off its own end is a plan problem the user has to see.
     */
    private fun shiftTime(time: LocalTime, minutes: Long, onClamp: () -> Unit): LocalTime {
        val moved = time.toSecondOfDay() + minutes * SECONDS_PER_MINUTE

        return when {
            moved < 0 -> LocalTime.MIN.also { onClamp() }
            moved > LAST_MINUTE.toSecondOfDay() -> LAST_MINUTE.also { onClamp() }
            else -> LocalTime.ofSecondOfDay(moved)
        }
    }

    // 3, 4, 5. Graph, place, expand ------------------------------------------

    /**
     * Places every item in dependency order, then expands the intervals.
     *
     * Expansion runs after the whole pass rather than inside it on purpose: a
     * `RELATIVE` child of an `INTERVAL` item hangs off the first repeat, not off
     * whichever repeat happened to be produced last.
     */
    private fun placeAndExpand(items: List<Item>, graph: AnchorGraph, input: ResolveInput): List<Placement> {
        val byId = items.associateBy { it.id }
        val dayStart = dayStartOf(items)

        // Only the first repeat of an item can be a RELATIVE parent, so the
        // anchor pass reads sequence zero and ignores the rest.
        val anchorOccurrences = input.occurrences.filter { it.sequenceInDay == 0 }.associateBy { it.itemId }

        val placed = LinkedHashMap<Long, Placement>(items.size)
        graph.order.forEach { id ->
            val item = byId[id] ?: return@forEach
            placed[id] = anchors.resolve(
                item,
                AnchorContext(input.date, input.zone, dayStart, graph, placed, anchorOccurrences),
            )
        }

        return graph.order.mapNotNull { byId[it] }.flatMap { intervals.expand(it, placed.getValue(it.id)) }
    }

    /**
     * The earliest fixed thing in the day, which is the most defensible base for
     * an item whose own anchor was cut. A plan with nothing fixed in it falls
     * back to [DEFAULT_DAY_START] rather than to midnight, because an orphan at
     * 00:00 reads as a bug and an orphan at 06:00 reads as something to fix.
     */
    private fun dayStartOf(items: List<Item>): LocalTime =
        items.mapNotNull { (it.anchor as? Anchor.Fixed)?.at }.minOrNull() ?: DEFAULT_DAY_START

    // 6, 7. Attach and sort --------------------------------------------------

    private fun buildEntries(placements: List<Placement>, items: List<Item>, input: ResolveInput): List<ResolvedEntry> {
        val byId = items.associateBy { it.id }
        val blocksById = input.blocks.associateBy { it.id }
        val occurrences = input.occurrences.associateBy { OccurrenceKey(it.itemId, it.sequenceInDay) }

        return placements.mapNotNull { placement ->
            val item = byId[placement.itemId] ?: return@mapNotNull null

            ResolvedEntry(
                item = item,
                block = item.blockId?.let(blocksById::get),
                at = placement.at,
                occurrence = occurrences[OccurrenceKey(item.id, placement.sequenceInDay)],
                sequenceInDay = placement.sequenceInDay,
                degraded = placement.degraded,
            )
        }.sortedWith(ENTRY_ORDER)
    }

    private data class OccurrenceKey(val itemId: Long, val sequenceInDay: Int)

    // 8. Budget --------------------------------------------------------------

    /**
     * rules.md section 1: three alarms and ten notifications in a day.
     *
     * Counted on the resolved entries rather than on the plan, because one
     * `INTERVAL` row can be eight notifications and the plan does not look noisy
     * until it has been expanded. Exceeding the budget is a warning on Today,
     * never a refusal to resolve.
     */
    private fun budgetWarning(entries: List<ResolvedEntry>): BudgetWarning? {
        val scheduled = entries.filter { it.salience != Salience.TIMELINE }
        val alarms = scheduled.count { it.salience == Salience.ALARM }
        val notifications = scheduled.count { it.salience == Salience.NOTIFY }

        val overBudget = alarms > BudgetWarning.MAX_ALARMS || notifications > BudgetWarning.MAX_NOTIFY
        return if (overBudget) BudgetWarning(alarms, notifications) else null
    }

    // 9. Issues --------------------------------------------------------------

    /**
     * Everything the resolver worked around, so the plan editor can offer to fix
     * it. The day still renders: a broken anchor produces a degraded entry and a
     * note, never an empty screen.
     */
    private fun collectIssues(graph: AnchorGraph, shifted: Shifted, placements: List<Placement>): List<ResolveIssue> {
        val byId = shifted.items.associateBy { it.id }
        val issues = mutableListOf<ResolveIssue>()

        graph.cycles.forEach { issues += ResolveIssue.AnchorCycle(it) }

        graph.missingParent.sorted().forEach { id ->
            val parent = (byId[id]?.anchor as? Anchor.Relative)?.parentItemId ?: return@forEach
            issues += ResolveIssue.MissingParent(id, parent)
        }

        val clampedIds = placements.filter { it.clamped }.map { it.itemId } + shifted.clamped
        clampedIds.distinct().sorted().forEach { issues += ResolveIssue.ClampedToMidnight(it) }

        return issues
    }

    private companion object {
        /**
         * Time first, then the order the user gave, then the id, then the repeat.
         *
         * The tail of that list is not decoration. Two items at the same minute
         * must land in the same order on every resolve, or the Today list will
         * reshuffle itself between recompositions for no visible reason.
         */
        val ENTRY_ORDER: Comparator<ResolvedEntry> = compareBy(
            { it.at },
            { it.item.sortOrder },
            { it.item.id },
            { it.sequenceInDay },
        )
    }
}
