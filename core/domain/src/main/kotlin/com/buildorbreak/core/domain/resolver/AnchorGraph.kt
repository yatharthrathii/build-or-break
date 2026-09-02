package com.buildorbreak.core.domain.resolver

import com.buildorbreak.core.model.plan.Anchor
import com.buildorbreak.core.model.plan.Item

/**
 * The order in which items can be resolved, plus everything wrong with the plan.
 *
 * A `RELATIVE` item cannot be placed until its parent has been placed, so the
 * resolver needs a dependency order. Users edit plans, so that graph will
 * eventually contain a cycle or a dangling parent, and when it does the day
 * still has to render.
 */
data class AnchorGraph(
    /** Item ids in resolution order. A parent always appears before its child. */
    val order: List<Long>,
    /** Items whose `RELATIVE` anchor was dropped to break a cycle. */
    val brokenByCycle: Set<Long>,
    /** Items whose parent is not present in this template. */
    val missingParent: Set<Long>,
    /** Every cycle found, each as the item ids that form it. */
    val cycles: List<List<Long>>,
    /**
     * The parent each item should actually resolve from, after cut edges have
     * been removed. An item absent from this map resolves from its fallback.
     */
    val effectiveParents: Map<Long, Long> = emptyMap(),
) {
    val hasIssues: Boolean get() = brokenByCycle.isNotEmpty() || missingParent.isNotEmpty()

    /** True when this item cannot use its `RELATIVE` anchor and needs a fallback. */
    fun isDegraded(itemId: Long): Boolean = itemId in brokenByCycle || itemId in missingParent

    /** The parent this item should actually resolve from, or null if it is a root. */
    fun effectiveParentOf(itemId: Long): Long? = effectiveParents[itemId]
}

/**
 * Builds the dependency order for one day's items.
 *
 * A `RELATIVE` anchor points at exactly one parent, so this graph has an out
 * degree of at most one per node. That makes it a functional graph, and walking
 * parent pointers from any node either terminates at a root or closes a loop.
 * No general topological sort is needed.
 *
 * Two passes, deliberately:
 *
 * 1. Find cycles and decide which edges to cut
 * 2. Order the graph with those edges already removed
 *
 * Doing it in one pass looks tempting and is wrong: the order produced while
 * discovering a cycle depends on which node the walk started from, so the same
 * broken plan would resolve differently on different launches.
 *
 * **Cycle breaking is deterministic.** The largest id in the cycle loses its
 * `RELATIVE` anchor. Largest means most recently created, which is the edge the
 * user most likely just added.
 */
class AnchorGraphBuilder {

    fun build(items: List<Item>): AnchorGraph {
        val ids = items.map { it.id }
        val known = ids.toSet()

        val declaredParents: Map<Long, Long> = items.mapNotNull { item ->
            (item.anchor as? Anchor.Relative)?.let { item.id to it.parentItemId }
        }.toMap()

        val missingParent = declaredParents.filterValues { it !in known }.keys.toSet()

        val resolvableParents = declaredParents.filterKeys { it !in missingParent }

        val cycles = findCycles(ids, resolvableParents)
        val brokenByCycle = cycles.mapNotNull { it.maxOrNull() }.toSet()

        val effectiveParents = resolvableParents.filterKeys { it !in brokenByCycle }

        return AnchorGraph(
            order = orderBy(ids, effectiveParents),
            brokenByCycle = brokenByCycle,
            missingParent = missingParent,
            cycles = cycles,
            effectiveParents = effectiveParents,
        )
    }

    /**
     * Pass one. Walks parent pointers from every node, recording any loop.
     *
     * `settled` holds nodes whose chain has already been walked, so the whole
     * scan is linear rather than quadratic.
     */
    private fun findCycles(ids: List<Long>, parents: Map<Long, Long>): List<List<Long>> {
        val cycles = mutableListOf<List<Long>>()
        val settled = mutableSetOf<Long>()

        ids.forEach { start ->
            if (start in settled) return@forEach

            val path = mutableListOf<Long>()
            val onPath = mutableSetOf<Long>()
            var current: Long? = start

            while (current != null && current !in settled) {
                if (current in onPath) {
                    cycles += path.subList(path.indexOf(current), path.size).toList()
                    break
                }
                path += current
                onPath += current
                current = parents[current]
            }

            settled += path
        }

        return cycles
    }

    /**
     * Pass two. Depth first over parent edges, appending after the parent, so a
     * parent is always placed before its child.
     *
     * The graph is acyclic by this point, but [placing] still guards the
     * recursion so a bug upstream degrades into a wrong order rather than a
     * stack overflow on a user's phone.
     */
    private fun orderBy(ids: List<Long>, parents: Map<Long, Long>): List<Long> {
        val order = ArrayList<Long>(ids.size)
        val placed = HashSet<Long>(ids.size)
        val placing = HashSet<Long>()

        fun place(id: Long) {
            if (id in placed || id in placing) return
            placing += id
            parents[id]?.let(::place)
            placing -= id
            placed += id
            order += id
        }

        ids.forEach(::place)
        return order
    }
}
