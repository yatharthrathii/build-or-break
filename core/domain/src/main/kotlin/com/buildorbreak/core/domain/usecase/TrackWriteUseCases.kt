package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.common.time.TimeProvider
import com.buildorbreak.core.domain.error.DomainError.DataError
import com.buildorbreak.core.domain.repository.ItemRepository
import com.buildorbreak.core.domain.repository.PlanRepository
import com.buildorbreak.core.domain.repository.TrackRepository
import com.buildorbreak.core.domain.repository.TrackSessionRepository
import com.buildorbreak.core.domain.track.TrackTextParser
import com.buildorbreak.core.model.enums.TrackUnitState
import com.buildorbreak.core.model.track.Track
import com.buildorbreak.core.model.track.TrackSession
import com.buildorbreak.core.model.track.TrackUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Writes a syllabus from its text.
 *
 * The text is kept as pasted and the parts are read out of it, one per line.
 * On an edit the parts are matched by position, so a part that is still on
 * the same line keeps the state it had: fixing a typo in part three must not
 * put somebody back to the start of it. Parts past the end of the new text
 * are dropped, sittings and all, which the screen says before it saves.
 */
class SaveTrackUseCase @Inject constructor(
    private val plans: PlanRepository,
    private val tracks: TrackRepository,
    private val parser: TrackTextParser,
    private val time: TimeProvider,
    private val dispatchers: AppDispatchers,
) {

    /** [trackId] null makes a new one on [planId], or on the active plan. */
    suspend operator fun invoke(
        trackId: Long?,
        name: String,
        text: String,
        planId: Long? = null,
    ): Outcome<Long, DataError> = withContext(dispatchers.io) {
        val parsed = parser.parse(text)
        if (parsed.isEmpty() || name.isBlank()) return@withContext Outcome.Failure(DataError.ConstraintViolation)

        val existing = trackId?.let { tracks.observeTrack(it).first() }
        val plan = planId ?: existing?.planId ?: plans.observeActive().first()?.id
            ?: return@withContext Outcome.Failure(DataError.NotFound)
        val before = trackId?.let { tracks.observeUnits(it).first() }.orEmpty()

        val units = parsed.mapIndexed { index, part ->
            val kept = before.getOrNull(index)

            TrackUnit(
                id = kept?.id ?: 0,
                trackId = trackId ?: 0,
                ordinal = index,
                title = part.title,
                estimateMinutes = part.estimateMinutes,
                state = kept?.state ?: TrackUnitState.PENDING,
            )
        }

        tracks.upsertTrack(
            Track(
                id = trackId ?: 0,
                planId = plan,
                name = name.trim(),
                sourceText = text,
                createdAt = existing?.createdAt ?: time.now(),
            ),
            units,
        )
    }
}

/**
 * One sitting, written down.
 *
 * Finishing the part moves the syllabus on; not finishing it leaves the part
 * in progress with the note beside it, so the next sitting opens on the same
 * part and the same line. Nothing here settles the step: that happened
 * already, and this is only what the sitting had to say about itself.
 */
class RecordTrackSessionUseCase @Inject constructor(
    private val tracks: TrackRepository,
    private val sessions: TrackSessionRepository,
    private val dispatchers: AppDispatchers,
) {

    suspend operator fun invoke(
        occurrenceId: Long,
        unitId: Long,
        minutes: Int,
        finished: Boolean,
        leftOff: String?,
    ): Outcome<Unit, DataError> = withContext(dispatchers.io) {
        val written = sessions.record(
            TrackSession(
                id = 0,
                occurrenceId = occurrenceId,
                trackUnitId = unitId,
                minutesSpent = minutes.coerceAtLeast(0),
                completedUnit = finished,
                leftOffNote = leftOff?.trim()?.takeIf { it.isNotEmpty() },
            ),
        )
        if (written is Outcome.Failure) return@withContext written

        tracks.setUnitState(unitId, if (finished) TrackUnitState.DONE else TrackUnitState.IN_PROGRESS)
    }
}

/**
 * Takes a sitting back, with the step it belonged to.
 *
 * Called by the undo on Today. A step put back to not done must not leave
 * its syllabus one part ahead, or the next sitting would open on work that
 * was never finished.
 */
class ForgetTrackSessionUseCase @Inject constructor(
    private val tracks: TrackRepository,
    private val sessions: TrackSessionRepository,
    private val dispatchers: AppDispatchers,
) {

    suspend operator fun invoke(occurrenceId: Long) = withContext(dispatchers.io) {
        sessions.forOccurrence(occurrenceId).forEach { sitting ->
            sessions.delete(sitting.id)

            val before = sessions.countForUnit(sitting.trackUnitId) > 0
            tracks.setUnitState(
                sitting.trackUnitId,
                if (before) TrackUnitState.IN_PROGRESS else TrackUnitState.PENDING,
            )
        }
    }
}

/** A part marked by hand: done, skipped, or put back to waiting. */
class SetTrackUnitStateUseCase @Inject constructor(
    private val tracks: TrackRepository,
    private val dispatchers: AppDispatchers,
) {

    suspend operator fun invoke(unitId: Long, state: TrackUnitState): Outcome<Unit, DataError> =
        withContext(dispatchers.io) { tracks.setUnitState(unitId, state) }
}

/**
 * Removes a syllabus and lets go of the steps that followed it.
 *
 * The steps stay on the plan as ordinary steps. Somebody deleting a course
 * outline has not asked for their evening study slot to disappear with it.
 */
class DeleteTrackUseCase @Inject constructor(
    private val tracks: TrackRepository,
    private val items: ItemRepository,
    private val dispatchers: AppDispatchers,
) {

    suspend operator fun invoke(trackId: Long): Outcome<Unit, DataError> = withContext(dispatchers.io) {
        items.detachTrack(trackId)

        tracks.delete(trackId)
    }
}
