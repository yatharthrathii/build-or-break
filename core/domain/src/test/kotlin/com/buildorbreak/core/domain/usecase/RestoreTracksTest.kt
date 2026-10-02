package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.domain.export.ExportBuilder
import com.buildorbreak.core.domain.export.ExportInput
import com.buildorbreak.core.domain.fake.FakeDayCloseRepository
import com.buildorbreak.core.domain.fake.FakeGoalRepository
import com.buildorbreak.core.domain.fake.FakeItemRepository
import com.buildorbreak.core.domain.fake.FakeMeasurementRepository
import com.buildorbreak.core.domain.fake.FakeMilestoneRepository
import com.buildorbreak.core.domain.fake.FakeOccurrenceRepository
import com.buildorbreak.core.domain.fake.FakePlanRepository
import com.buildorbreak.core.domain.fake.FakeTemplateRepository
import com.buildorbreak.core.domain.fake.FakeTrackRepository
import com.buildorbreak.core.domain.fake.FakeTrackSessionRepository
import com.buildorbreak.core.model.enums.TrackUnitState
import com.buildorbreak.core.model.plan.Plan
import com.buildorbreak.core.model.track.Track
import com.buildorbreak.core.model.track.TrackSession
import com.buildorbreak.core.model.track.TrackUnit
import com.buildorbreak.core.testing.fixtures.ExecutionFixtures
import com.buildorbreak.core.testing.fixtures.PlanFixtures
import com.buildorbreak.core.testing.time.FakeTimeProvider
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

private const val OLD_TRACK_ID = 40L
private const val MINUTES = 35

/**
 * A syllabus through the file and back.
 *
 * The parts travel by position and the sittings by the part they were on,
 * because every id in the file belongs to a database that no longer exists.
 */
class RestoreTracksTest {

    private val tracks = FakeTrackRepository()
    private val sessions = FakeTrackSessionRepository(tracks)
    private val builder = ExportBuilder()
    private val time = FakeTimeProvider()

    private val sources = BackupSources(
        plans = FakePlanRepository(),
        templates = FakeTemplateRepository(),
        items = FakeItemRepository(),
        goals = FakeGoalRepository(),
        occurrences = FakeOccurrenceRepository(),
        measurements = FakeMeasurementRepository(),
        milestones = FakeMilestoneRepository(),
        closes = FakeDayCloseRepository(),
        tracks = TrackSources(tracks, sessions),
    )

    private val restore = RestoreTracks(sources, time)

    private val plan = Plan(
        id = PlanFixtures.PLAN_ID,
        name = "Study",
        isActive = true,
        zone = ExecutionFixtures.ZONE,
        createdAt = Instant.EPOCH,
    )

    private val track = Track(OLD_TRACK_ID, PlanFixtures.PLAN_ID, "Backend", "HTTP\nREST", Instant.EPOCH)
    private val units = listOf(
        TrackUnit(
            id = 100,
            trackId = OLD_TRACK_ID,
            ordinal = 0,
            title = "HTTP",
            estimateMinutes = 30,
            state = TrackUnitState.DONE,
        ),
        TrackUnit(
            id = 101,
            trackId = OLD_TRACK_ID,
            ordinal = 1,
            title = "REST",
            estimateMinutes = null,
            state = TrackUnitState.IN_PROGRESS,
        ),
    )
    private val sittings = listOf(
        TrackSession(
            id = 1,
            occurrenceId = 9,
            trackUnitId = 100,
            minutesSpent = MINUTES,
            completedUnit = true,
            leftOffNote = null,
        ),
        TrackSession(
            id = 2,
            occurrenceId = 10,
            trackUnitId = 101,
            minutesSpent = MINUTES,
            completedUnit = false,
            leftOffNote = "page 12",
        ),
    )

    private fun document(withHistory: Boolean = true) = builder.build(
        ExportInput(
            plan = plan,
            templates = listOf(PlanFixtures.template()),
            items = listOf(PlanFixtures.item(id = 1, trackId = OLD_TRACK_ID, bundleUri = "https://example.org/notes")),
            tracks = listOf(track),
            trackUnits = mapOf(OLD_TRACK_ID to units),
            trackSessions = mapOf(OLD_TRACK_ID to sittings),
        ),
        exportedAt = Instant.EPOCH,
        includeHistory = withHistory,
    )

    @Test
    fun `the file carries the parts, their states and the sittings by position`() {
        val exported = document().tracks.single()

        assertThat(exported.units.map { it.title }).containsExactly("HTTP", "REST").inOrder()
        assertThat(exported.units.map { it.state }).containsExactly("DONE", "IN_PROGRESS").inOrder()
        assertThat(exported.sessions.map { it.unitOrdinal }).containsExactly(0, 1).inOrder()
        assertThat(exported.sessions.last().leftOff).isEqualTo("page 12")
    }

    @Test
    fun `a step keeps its link and the syllabus it follows`() {
        val item = document().templates.single().items.single()

        assertThat(item.bundleUri).isEqualTo("https://example.org/notes")
        assertThat(item.trackId).isEqualTo(OLD_TRACK_ID)
    }

    @Test
    fun `shared without history, a syllabus starts from the top`() {
        val exported = document(withHistory = false).tracks.single()

        assertThat(exported.units.map { it.state }).containsExactly("PENDING", "PENDING")
        assertThat(exported.sessions).isEmpty()
    }

    @Test
    fun `restored, the parts come back in order with new ids and the sittings on them`() = runTest {
        val ids = restore.write(document().tracks, planId = 2)

        val newId = ids.getValue(OLD_TRACK_ID)
        assertThat(newId).isNotEqualTo(OLD_TRACK_ID)
        assertThat(tracks.observeTrack(newId).first()?.planId).isEqualTo(2L)

        val restored = tracks.observeUnits(newId).first()
        assertThat(restored.map { it.title }).containsExactly("HTTP", "REST").inOrder()
        assertThat(restored.map { it.state }).containsExactly(TrackUnitState.DONE, TrackUnitState.IN_PROGRESS).inOrder()

        val notes = sessions.observeForTrack(newId).first()
        assertThat(notes).hasSize(2)
        assertThat(notes.last().trackUnitId).isEqualTo(restored.last().id)
        assertThat(notes.last().leftOffNote).isEqualTo("page 12")
    }
}
