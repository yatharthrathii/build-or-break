package com.buildorbreak.app.feature.plan

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex

/** A row lifted by the finger sits above the ones it passes. */
private const val LIFTED = 1f

/**
 * The finger on the handle, and what it has done to the order so far.
 *
 * Only a tie moves. Two steps at the same minute have no order the clock can
 * decide, so the hand decides; a step at 08:00 dragged under one at 09:00 is
 * a different question, and the answer to that one lives in the editor as a
 * time. The rows outside the tie ignore the handle, which is why the handle
 * on those rows is drawn faint.
 *
 * The order is changed as the finger moves rather than on release, so the
 * other rows step aside while the lifted one passes and the list never has
 * to jump at the end.
 */
@Stable
class ReorderState {

    /** The step under the finger, or null. */
    var draggedId: Long? by mutableStateOf(null)
        private set

    /** How far the lifted row is from its slot, in pixels. */
    var offset: Float by mutableFloatStateOf(0f)
        private set

    /** The tie, in the order it currently stands. Empty when nothing is lifted. */
    var order: List<Long> by mutableStateOf(emptyList())
        private set

    private val heights = mutableMapOf<Long, Int>()

    fun measured(id: Long, heightPx: Int) {
        heights[id] = heightPx
    }

    fun start(id: Long, tie: List<Long>) {
        draggedId = id
        order = tie
        offset = 0f
    }

    fun drag(dy: Float) {
        val id = draggedId ?: return
        offset += dy

        val index = order.indexOf(id)
        val below = order.getOrNull(index + 1)
        val above = order.getOrNull(index - 1)

        // Past the middle of the neighbour, the neighbour steps aside and the
        // lifted row's slot moves with it, so the offset is measured from the
        // new slot rather than the old one.
        when {
            below != null && offset > heightOf(below) / 2f -> {
                order = order.swapped(index, index + 1)
                offset -= heightOf(below)
            }

            above != null && offset < -heightOf(above) / 2f -> {
                order = order.swapped(index, index - 1)
                offset += heightOf(above)
            }
        }
    }

    /** Puts the row down. Returns the tie in its new order, or null if nothing was lifted. */
    fun end(): List<Long>? {
        val result = order.takeIf { draggedId != null }

        draggedId = null
        order = emptyList()
        offset = 0f

        return result
    }

    private fun heightOf(id: Long): Int = heights[id] ?: 0

    private fun List<Long>.swapped(a: Int, b: Int): List<Long> = toMutableList().also { list ->
        val held = list[a]
        list[a] = list[b]
        list[b] = held
    }
}

@Composable
fun rememberReorderState(): ReorderState = remember { ReorderState() }

/**
 * The handle. A drag on it lifts the row; the rest of the row still scrolls
 * the list and still opens the editor.
 *
 * [tie] is the ids this row may swap places with, in their current order.
 * A row with nobody to swap with gets no gesture at all.
 *
 * The gesture is keyed on the row alone. The tie's order changes under the
 * finger as rows step aside, and keying on it restarted the detector mid
 * drag: the row swapped once on screen, the gesture was cancelled, and
 * nothing was ever written. The latest tie is read through a holder instead.
 */
@Composable
fun Modifier.reorderHandle(
    state: ReorderState,
    id: Long,
    tie: List<Long>,
    onReordered: (List<Long>) -> Unit,
): Modifier {
    val latestTie by rememberUpdatedState(tie)
    val latestReordered by rememberUpdatedState(onReordered)

    if (tie.size < 2) return this

    return pointerInput(id) {
        detectDragGestures(
            onDragStart = { state.start(id, latestTie) },
            onDrag = { change, dragged ->
                change.consume()
                state.drag(dragged.y)
            },
            onDragEnd = { state.end()?.let(latestReordered) },
            onDragCancel = { state.end() },
        )
    }
}

/** Lifts the row by the finger's offset while it is held, and measures it for the ones it passes. */
fun Modifier.reorderRow(state: ReorderState, id: Long): Modifier = this
    .onSizeChanged { state.measured(id, it.height) }
    .zIndex(if (state.draggedId == id) LIFTED else 0f)
    .graphicsLayer { translationY = if (state.draggedId == id) state.offset else 0f }

/**
 * The rows of one section in the order they are drawn right now.
 *
 * While a tie in this section is lifted, the lifted order wins; otherwise the
 * plan's own order stands.
 */
fun ReorderState.arrange(rows: List<PlanItemRow>): List<PlanItemRow> {
    if (draggedId == null || rows.none { it.id == draggedId }) return rows

    val position = order.withIndex().associate { (index, id) -> id to index }
    val first = rows.indexOfFirst { it.id in position }
    if (first < 0) return rows

    val tied = rows.filter { it.id in position }.sortedBy { position.getValue(it.id) }
    val rest = rows.filterNot { it.id in position }

    return rest.take(first) + tied + rest.drop(first)
}
