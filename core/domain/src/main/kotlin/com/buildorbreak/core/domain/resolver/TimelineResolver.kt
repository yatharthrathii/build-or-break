package com.buildorbreak.core.domain.resolver

import com.buildorbreak.core.model.enums.DayMode
import com.buildorbreak.core.model.execution.Occurrence
import com.buildorbreak.core.model.plan.Block
import com.buildorbreak.core.model.plan.DayTemplate
import com.buildorbreak.core.model.plan.Item
import com.buildorbreak.core.model.resolved.ResolvedDay
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Duration

/**
 * The whole day, computed from the plan and what has happened so far.
 *
 * architecture.md section 1: this result is never stored. It is recomputed on
 * every read, which is what makes it impossible for a saved schedule to drift
 * out of agreement with the plan it came from.
 *
 * A `fun interface` because the whole contract is one pure call, and because a
 * test that needs a fixed day can substitute a lambda instead of a class.
 */
fun interface TimelineResolver {
    fun resolve(input: ResolveInput): ResolvedDay
}

/**
 * Everything one day depends on.
 *
 * This is deliberately a snapshot rather than a set of repositories. The
 * resolver cannot read anything it was not handed, so it cannot be slow, cannot
 * fail, and cannot behave differently on a second call with the same inputs.
 */
data class ResolveInput(
    val template: DayTemplate,
    val blocks: List<Block>,
    val items: List<Item>,
    val occurrences: List<Occurrence>,
    val date: LocalDate,
    val zone: ZoneId,
    /** How far the whole day was moved. Pinned items ignore it. */
    val dayShift: Duration,
    val mode: DayMode,
)
