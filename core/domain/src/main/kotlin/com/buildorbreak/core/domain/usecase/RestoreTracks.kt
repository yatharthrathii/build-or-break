package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.result.getOrNull
import com.buildorbreak.core.common.time.TimeProvider
import com.buildorbreak.core.domain.export.ExportTrack
import com.buildorbreak.core.model.enums.TrackUnitState
import com.buildorbreak.core.model.track.Track
import com.buildorbreak.core.model.track.TrackSession
import com.buildorbreak.core.model.track.TrackUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * The syllabuses, put back.
 *
 * Apart from `RestoreBackupUseCase` because that class already does the
 * most delicate thing in the app and did not need a fourth table in it.
 * Written before the steps, because a step points at the syllabus it
 * follows and needs the new id to point at.
 *
 * The sittings come back without the occurrence they belonged to, which is
 * not in the file. They stand on their part alone: the minutes and the
 * note are what the next sitting wants, and the undo that would have
 * needed the occurrence is long past.
 */
class RestoreTracks @Inject constructor(
    private val sources: BackupSources,
    private val time: TimeProvider,
) {

    /** Returns old track id to new track id. */
    suspend fun write(fromFile: List<ExportTrack>, planId: Long): Map<Long, Long> {
        val trackIds = mutableMapOf<Long, Long>()

        fromFile.forEach { source ->
            val units = source.units.sortedBy { it.ordinal }.mapIndexed { index, unit ->
                TrackUnit(
                    id = 0,
                    trackId = 0,
                    ordinal = index,
                    title = unit.title,
                    estimateMinutes = unit.estimateMinutes,
                    state = enumOrNull<TrackUnitState>(unit.state) ?: TrackUnitState.PENDING,
                )
            }
            if (units.isEmpty()) return@forEach

            val track = Track(
                id = 0,
                planId = planId,
                name = source.name,
                sourceText = source.sourceText,
                createdAt = source.createdAt.toInstantOrNull() ?: time.now(),
            )
            val written = sources.tracks.tracks.upsertTrack(track, units).getOrNull() ?: return@forEach

            trackIds[source.id] = written
            writeSessions(source, written)
        }

        return trackIds
    }

    private suspend fun writeSessions(source: ExportTrack, trackId: Long) {
        val byOrdinal = sources.tracks.tracks.observeUnits(trackId).first().associateBy { it.ordinal }

        source.sessions.forEach { sitting ->
            val unit = byOrdinal[sitting.unitOrdinal] ?: return@forEach

            sources.tracks.sessions.record(
                TrackSession(
                    id = 0,
                    occurrenceId = 0,
                    trackUnitId = unit.id,
                    minutesSpent = sitting.minutesSpent,
                    completedUnit = sitting.finished,
                    leftOffNote = sitting.leftOff,
                ),
            )
        }
    }
}
