package com.buildorbreak.core.domain.resolver

import com.buildorbreak.core.model.enums.Salience
import com.buildorbreak.core.model.resolved.BudgetWarning
import com.buildorbreak.core.model.resolved.ResolvedEntry

/**
 * How much noise a day would actually make.
 *
 * rules.md section 1: three alarms and ten notifications. The number is not
 * arbitrary. An app that fires more than that is an app people mute inside a
 * week, and a muted routine app has no product left.
 *
 * Counted on resolved entries rather than on the plan, because one `INTERVAL`
 * row can be eleven notifications and the plan does not look noisy until it has
 * been expanded. This is why the check cannot live in the plan editor alone.
 *
 * Exceeding the budget is always a warning, never a refusal. The day still
 * resolves and every alarm is still scheduled: silently dropping the fourth
 * alarm would be a routine app that skips part of your routine.
 */
class SalienceBudget {

    fun evaluate(entries: List<ResolvedEntry>): BudgetWarning? {
        val counted = count(entries)

        val overBudget = counted.alarms > BudgetWarning.MAX_ALARMS ||
            counted.notifications > BudgetWarning.MAX_NOTIFY

        return if (overBudget) BudgetWarning(counted.alarms, counted.notifications) else null
    }

    /**
     * The effective salience is the block's when there is one, because a block
     * is delivered as a single notification for the whole group.
     */
    private fun count(entries: List<ResolvedEntry>): Counted {
        // TIMELINE entries are never handed to the scheduler, so they cost the
        // user nothing and must not push a quiet day over the limit.
        val scheduled = entries.filter { it.salience != Salience.TIMELINE }

        return Counted(
            alarms = scheduled.count { it.salience == Salience.ALARM },
            notifications = scheduled.count { it.salience == Salience.NOTIFY },
        )
    }

    private data class Counted(val alarms: Int, val notifications: Int)
}
