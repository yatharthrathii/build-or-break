package com.buildorbreak.app.feature.today

import com.buildorbreak.core.domain.usecase.ForgetTrackSessionUseCase
import com.buildorbreak.core.domain.usecase.ObserveTracksUseCase
import com.buildorbreak.core.domain.usecase.RecordTrackSessionUseCase
import com.buildorbreak.core.domain.usecase.TrackHead
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * What Today needs to know and do about syllabuses, in one handle.
 *
 * Three use cases that always travel together: where each syllabus stands,
 * writing a sitting down, and taking one back when the step is undone.
 * Listed one by one they pushed `DayWatch` past the point anyone could read.
 */
class TrackWatch @Inject constructor(
    private val observeTracks: ObserveTracksUseCase,
    private val record: RecordTrackSessionUseCase,
    private val forget: ForgetTrackSessionUseCase,
) {
    /** Every syllabus on the plan, keyed by id. */
    fun heads(): Flow<Map<Long, TrackHead>> = observeTracks.byId()

    suspend fun recordSession(
        occurrenceId: Long,
        unitId: Long,
        minutes: Int,
        finished: Boolean,
        leftOff: String,
    ) {
        record(occurrenceId, unitId, minutes, finished, leftOff)
    }

    suspend fun forgetSession(occurrenceId: Long) {
        forget(occurrenceId)
    }
}
