package com.buildorbreak.core.domain.goal

import com.buildorbreak.core.model.enums.DayQuality
import com.buildorbreak.core.model.enums.Milestone
import com.buildorbreak.core.model.enums.MilestoneCategory
import com.buildorbreak.core.model.goal.DayClose
import com.buildorbreak.core.model.goal.MilestoneAward
import java.time.LocalDate

/** A goal is a quarter done at twenty five percent, and so on. */
private const val QUARTER = 0.25f
private const val HALF = 0.5f
private const val THREE_QUARTERS = 0.75f
private const val COMPLETE = 1f

/** Seven consecutive closed days is the first week. */
private const val FIRST_WEEK_DAYS = 7

/** `Milestone.ITEM_THIRTY_DAY_RUN` is exactly what its name says. */
private const val LONG_RUN_DAYS = 30

/**
 * How many of the last thirty days went well.
 *
 * This is the number the app shows instead of a consecutive day streak. A
 * streak is a reward that turns into a punishment the moment it breaks, and the
 * person this app is for is the person who already misses things. One bad day
 * takes this from twenty four to twenty three, and tomorrow it can go back up.
 * Nothing is ever wiped.
 */
private const val CONSISTENCY_WINDOW_DAYS = 30

/**
 * Everything needed to decide whether today earned anything.
 *
 * Passed in whole rather than fetched, so the decision is a pure function of
 * facts that can be written down in a test. Nothing here is optional for
 * convenience: each field exists because one of the nine milestones needs it.
 */
data class MilestoneContext(
    val date: LocalDate,
    val today: DayClose,
    /** Every close before today, oldest first. */
    val history: List<DayClose>,
    /** Zero to one, or null when no goal is active. */
    val goalPercent: Float? = null,
    val goalId: Long? = null,
    /** The item currently on the longest unbroken run, if any. */
    val longestRun: ItemRun? = null,
    /** Proof of what has already fired. The rows are the anti repeat mechanism. */
    val awarded: List<MilestoneAward> = emptyList(),
)

/** One item, and how many days in a row it has been done. */
data class ItemRun(val itemId: Long, val days: Int)

/**
 * How steady the last thirty days have been, without a streak to break.
 *
 * [goodDays] out of [consideredDays]. Both are returned rather than a bare
 * percentage so the UI can say "24 of the last 30" and be believed.
 */
data class ConsistencyScore(val goodDays: Int, val consideredDays: Int) {
    val fraction: Float get() = if (consideredDays == 0) 0f else goodDays.toFloat() / consideredDays
}

/**
 * At most one milestone a day, or nothing at all.
 *
 * architecture.md section 5.1: a POOR day always returns null, and that is
 * enforced here in the domain so no screen can accidentally congratulate
 * somebody on a day that went badly. An app that cheers at the wrong moment is
 * an app that gets muted, and this rule is cheaper to keep in one place than to
 * remember on every screen.
 */
interface MilestoneEvaluator {
    fun evaluate(context: MilestoneContext): Milestone?

    /** The thirty day figure shown in place of a streak. */
    fun consistency(history: List<DayClose>, on: LocalDate): ConsistencyScore
}

class DefaultMilestoneEvaluator : MilestoneEvaluator {

    /**
     * Four suppression rules, applied in this order:
     *
     * 1. A POOR day earns nothing, whatever else is true
     * 2. A milestone that has already fired never fires again
     * 3. Nothing from the same category as yesterday, so two goal milestones
     *    cannot land back to back and turn praise into noise
     * 4. At most one a day, taking the rarest of whatever qualifies
     *
     * Rule three is the one that is easy to leave out and expensive to miss.
     * Crossing a quarter and a half of a goal on consecutive days is common,
     * and being congratulated twice in two days trains somebody to ignore the
     * third time, which is the one that mattered.
     */
    override fun evaluate(context: MilestoneContext): Milestone? {
        // 1. A bad day is not the moment for this.
        if (context.today.quality == DayQuality.POOR) return null

        val alreadyFired = context.awarded.map { it.milestone }.toSet()
        val yesterdaysCategory = categoryAwardedOn(context, context.date.minusDays(1))

        return earned(context)
            // 2. Once in the lifetime of an install.
            .filterNot { it in alreadyFired }
            // 3. Not the same kind of praise two days running.
            .filterNot { it.category == yesterdaysCategory }
            // 4. The rarest one, so a first time is never buried under a routine one.
            .minByOrNull(::rarity)
    }

    override fun consistency(history: List<DayClose>, on: LocalDate): ConsistencyScore {
        val earliest = on.minusDays(CONSISTENCY_WINDOW_DAYS - 1L)
        val window = history.filter { it.date >= earliest && it.date <= on }

        return ConsistencyScore(
            goodDays = window.count { it.quality != DayQuality.POOR },
            consideredDays = window.size,
        )
    }

    /**
     * Everything today genuinely qualifies for, before any suppression.
     *
     * Deliberately separate from the filtering above. What was achieved and what
     * is worth saying are different questions, and mixing them is how a rule
     * change quietly breaks an unrelated milestone.
     */
    private fun earned(context: MilestoneContext): List<Milestone> = buildList {
        val today = context.today

        if (today.itemsDone + today.itemsMinimum > 0 && context.history.none { it.itemsDone > 0 }) {
            add(Milestone.FIRST_COMPLETION)
        }

        if (today.isFullDay && context.history.none { it.isFullDay }) {
            add(Milestone.FIRST_FULL_DAY)
        }

        if (closedDaysIncludingToday(context) == FIRST_WEEK_DAYS) {
            add(Milestone.FIRST_WEEK)
        }

        context.goalPercent?.let { percent ->
            when {
                percent >= COMPLETE -> add(Milestone.GOAL_REACHED)
                percent >= THREE_QUARTERS -> add(Milestone.GOAL_THREE_QUARTERS)
                percent >= HALF -> add(Milestone.GOAL_HALF)
                percent >= QUARTER -> add(Milestone.GOAL_QUARTER)
            }
        }

        if (isBestWeek(context)) add(Milestone.BEST_WEEK)

        if ((context.longestRun?.days ?: 0) >= LONG_RUN_DAYS) add(Milestone.ITEM_THIRTY_DAY_RUN)
    }

    private fun closedDaysIncludingToday(context: MilestoneContext): Int = context.history.size + 1

    /**
     * The best seven days so far, and only once there is something to compare
     * against. Calling the very first week the best week is technically true and
     * completely meaningless, and `FIRST_WEEK` already covers that moment.
     */
    private fun isBestWeek(context: MilestoneContext): Boolean {
        val closes = context.history + context.today
        if (closes.size < FIRST_WEEK_DAYS * 2) return false

        val thisWeek = closes.takeLast(FIRST_WEEK_DAYS).map { it.adherence }.average()
        val earlier = closes.dropLast(FIRST_WEEK_DAYS)

        return earlier.windowed(FIRST_WEEK_DAYS).all { week -> thisWeek > week.map { it.adherence }.average() }
    }

    private fun categoryAwardedOn(context: MilestoneContext, date: LocalDate): MilestoneCategory? =
        context.awarded.firstOrNull { it.awardedOn == date }?.milestone?.category

    /**
     * Lower is rarer. A first time happens once in the life of an install, a
     * goal boundary a handful of times, and a run or a best week can recur, so
     * when two land on the same day the rarer one is the one worth saying.
     */
    private fun rarity(milestone: Milestone): Int = when (milestone.category) {
        MilestoneCategory.FIRST -> 0
        MilestoneCategory.GOAL -> 1
        MilestoneCategory.STREAK -> 2
    }
}
