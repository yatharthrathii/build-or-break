package com.buildorbreak.core.model.track

import com.buildorbreak.core.model.enums.TrackUnitState
import java.time.Instant

/**
 * An ordered syllabus that a timeline slot advances through.
 *
 * A shake is the same every day. Learning is not: day one is HTTP basics and day
 * thirty is deployment. Repetition and progression are different shapes, and
 * habit apps only model the first one.
 *
 * The app never writes the syllabus. The user pastes one in, exactly as
 * `prd.md` section 1 requires.
 */
data class Track(
    val id: Long,
    val planId: Long,
    val name: String,
    /** The pasted text, kept verbatim so the user can always see the original. */
    val sourceText: String?,
    val createdAt: Instant,
)

data class TrackUnit(
    val id: Long,
    val trackId: Long,
    val ordinal: Int,
    val title: String,
    val estimateMinutes: Int?,
    val state: TrackUnitState,
)

/**
 * One sitting.
 *
 * [leftOffNote] is the whole point. Where you stopped is the single biggest
 * friction in coming back to something the next evening, and one line written at
 * the end of a session saves five minutes at the start of the next one.
 */
data class TrackSession(
    val id: Long,
    val occurrenceId: Long,
    val trackUnitId: Long,
    val minutesSpent: Int,
    val completedUnit: Boolean,
    val leftOffNote: String?,
)
