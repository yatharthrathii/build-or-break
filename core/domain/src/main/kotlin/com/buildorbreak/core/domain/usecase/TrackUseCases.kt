package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.domain.repository.PlanRepository
import com.buildorbreak.core.domain.repository.TrackRepository
import com.buildorbreak.core.domain.repository.TrackSessionRepository
import com.buildorbreak.core.model.enums.TrackUnitState
import com.buildorbreak.core.model.track.Track
import com.buildorbreak.core.model.track.TrackSession
import com.buildorbreak.core.model.track.TrackUnit
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Where a syllabus stands.
 *
 * [next] is the part the next sitting opens on: the first one not yet done
 * or skipped, in order. [leftOff] is what the last sitting on that part
 * wrote down about where it stopped, which is the single line that saves
 * five minutes at the start of the next one.
 */
data class TrackHead(
    val track: Track,
    val units: List<TrackUnit>,
    val next: TrackUnit?,
    val leftOff: String?,
    /** Every sitting so far, oldest first. */
    val sessions: List<TrackSession> = emptyList(),
) {
    val total: Int get() = units.size

    val finished: Int get() = units.count { it.state == TrackUnitState.DONE }

    /** One based, for "part 3 of 30". Zero when there is nothing left. */
    val position: Int get() = next?.let { units.indexOf(it) + 1 } ?: 0

    /** Every part dealt with, and there was at least one. */
    val isFinished: Boolean get() = next == null && units.isNotEmpty()

    /** Minutes spent across every sitting. */
    val minutesSpent: Int get() = sessions.sumOf { it.minutesSpent }
}

/**
 * Every syllabus on the active plan, with where each one stands.
 *
 * Built from the units and the sittings rather than stored, like the day
 * itself. The one decision here, which part is next, is the first open one
 * in order; a part that was skipped is finished with, not waiting.
 */
class ObserveTracksUseCase @Inject constructor(
    private val plans: PlanRepository,
    private val tracks: TrackRepository,
    private val sessions: TrackSessionRepository,
    private val dispatchers: AppDispatchers,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<List<TrackHead>> = plans.observeActive()
        .flatMapLatest { plan -> if (plan == null) flowOf(emptyList()) else tracks.observeForPlan(plan.id) }
        .flatMapLatest { running ->
            if (running.isEmpty()) flowOf(emptyList()) else combine(running.map(::headOf)) { it.toList() }
        }
        .flowOn(dispatchers.default)

    /** One syllabus, or null once it has been deleted. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun one(trackId: Long): Flow<TrackHead?> = tracks.observeTrack(trackId)
        .flatMapLatest { track -> if (track == null) flowOf(null) else headOf(track) }
        .flowOn(dispatchers.default)

    /** The same heads keyed by track, for a screen that has steps and wants their syllabus. */
    fun byId(): Flow<Map<Long, TrackHead>> = invoke().map { heads -> heads.associateBy { it.track.id } }

    private fun headOf(track: Track): Flow<TrackHead> =
        combine(tracks.observeUnits(track.id), sessions.observeForTrack(track.id)) { units, sittings ->
            val next = units.firstOrNull {
                it.state == TrackUnitState.PENDING || it.state == TrackUnitState.IN_PROGRESS
            }

            TrackHead(
                track = track,
                units = units,
                next = next,
                leftOff = next?.let { unit -> sittings.lastOrNull { it.trackUnitId == unit.id }?.leftOffNote },
                sessions = sittings,
            )
        }
}
