package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.common.result.getOrNull
import com.buildorbreak.core.domain.fake.FakeItemRepository
import com.buildorbreak.core.domain.fake.FakePlanRepository
import com.buildorbreak.core.domain.fake.FakeTrackRepository
import com.buildorbreak.core.domain.fake.FakeTrackSessionRepository
import com.buildorbreak.core.domain.track.TrackTextParser
import com.buildorbreak.core.model.enums.ItemKind
import com.buildorbreak.core.model.enums.TrackUnitState
import com.buildorbreak.core.model.plan.Plan
import com.buildorbreak.core.testing.fixtures.ExecutionFixtures
import com.buildorbreak.core.testing.fixtures.PlanFixtures
import com.buildorbreak.core.testing.time.FakeTimeProvider
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val OCCURRENCE = 7L
private const val SITTING_MINUTES = 40

/**
 * A syllabus from paste to progress.
 *
 * What is pinned down here is the part that has to be right or the feature
 * is worse than no feature: which part comes next, that an edit does not
 * lose anybody their place, and that an undone step takes its sitting back.
 */
class TrackUseCasesTest {

    private val plans = FakePlanRepository()
    private val tracks = FakeTrackRepository()
    private val sessions = FakeTrackSessionRepository(tracks)
    private val items = FakeItemRepository()
    private val time = FakeTimeProvider()

    private val dispatchers = object : AppDispatchers {
        override val default = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
        override val main = Dispatchers.Unconfined
    }

    private val save = SaveTrackUseCase(plans, tracks, TrackTextParser(), time, dispatchers)
    private val record = RecordTrackSessionUseCase(tracks, sessions, dispatchers)
    private val forget = ForgetTrackSessionUseCase(tracks, sessions, dispatchers)
    private val observe = ObserveTracksUseCase(plans, tracks, sessions, dispatchers)
    private val delete = DeleteTrackUseCase(tracks, items, dispatchers)

    @BeforeEach
    fun givenAPlan() = runTest {
        plans.upsert(
            Plan(id = 1, name = "Study", isActive = true, zone = ExecutionFixtures.ZONE, createdAt = Instant.EPOCH),
        )
    }

    private suspend fun course(): Long =
        save(trackId = null, name = "Backend", text = "HTTP\nREST\nAuth").getOrNull() ?: 0

    @Test
    fun `a pasted syllabus becomes parts in order, all waiting`() = runTest {
        val id = course()

        val units = tracks.observeUnits(id).first()
        assertThat(units.map { it.title }).containsExactly("HTTP", "REST", "Auth").inOrder()
        assertThat(units.map { it.state }).containsExactly(PENDING, PENDING, PENDING)
        assertThat(tracks.observeTrack(id).first()?.sourceText).isEqualTo("HTTP\nREST\nAuth")
    }

    @Test
    fun `nothing readable is refused rather than saved as an empty course`() = runTest {
        val outcome = save(trackId = null, name = "Backend", text = "Week 1:\n\n")

        assertThat(outcome).isInstanceOf(Outcome.Failure::class.java)
        assertThat(tracks.tracks.value).isEmpty()
    }

    @Test
    fun `the first open part is next, and it says where it stands`() = runTest {
        val id = course()
        val first = tracks.observeUnits(id).first().first()

        record(OCCURRENCE, first.id, SITTING_MINUTES, finished = true, leftOff = null)

        val head = observe().first().single()
        assertThat(head.next?.title).isEqualTo("REST")
        assertThat(head.position).isEqualTo(2)
        assertThat(head.finished).isEqualTo(1)
        assertThat(head.total).isEqualTo(3)
        assertThat(head.minutesSpent).isEqualTo(SITTING_MINUTES)
    }

    @Test
    fun `an unfinished sitting keeps the part and carries its note forward`() = runTest {
        val id = course()
        val first = tracks.observeUnits(id).first().first()

        record(OCCURRENCE, first.id, SITTING_MINUTES, finished = false, leftOff = "  page 42 ")

        val head = observe().first().single()
        assertThat(head.next?.title).isEqualTo("HTTP")
        assertThat(head.next?.state).isEqualTo(TrackUnitState.IN_PROGRESS)
        assertThat(head.leftOff).isEqualTo("page 42")
    }

    @Test
    fun `undoing the step takes the sitting back and reopens the part`() = runTest {
        val id = course()
        val first = tracks.observeUnits(id).first().first()
        record(OCCURRENCE, first.id, SITTING_MINUTES, finished = true, leftOff = null)

        forget(OCCURRENCE)

        val head = observe().first().single()
        assertThat(head.next?.title).isEqualTo("HTTP")
        assertThat(head.next?.state).isEqualTo(PENDING)
        assertThat(head.sessions).isEmpty()
    }

    @Test
    fun `a part with an earlier sitting goes back to in progress, not to the start`() = runTest {
        val id = course()
        val first = tracks.observeUnits(id).first().first()
        record(OCCURRENCE - 1, first.id, SITTING_MINUTES, finished = false, leftOff = "halfway")
        record(OCCURRENCE, first.id, SITTING_MINUTES, finished = true, leftOff = null)

        forget(OCCURRENCE)

        assertThat(observe().first().single().next?.state).isEqualTo(TrackUnitState.IN_PROGRESS)
    }

    @Test
    fun `editing the text keeps the place of every part still on its line`() = runTest {
        val id = course()
        val first = tracks.observeUnits(id).first().first()
        record(OCCURRENCE, first.id, SITTING_MINUTES, finished = true, leftOff = null)

        save(trackId = id, name = "Backend", text = "HTTP basics\nREST\nAuth\nDeploy")

        val units = tracks.observeUnits(id).first()
        assertThat(units.map { it.title }).containsExactly("HTTP basics", "REST", "Auth", "Deploy").inOrder()
        assertThat(units.first().state).isEqualTo(TrackUnitState.DONE)
        assertThat(units.first().id).isEqualTo(first.id)
        assertThat(observe().first().single().next?.title).isEqualTo("REST")
    }

    @Test
    fun `parts cut from the end of the text are gone`() = runTest {
        val id = course()

        save(trackId = id, name = "Backend", text = "HTTP")

        assertThat(tracks.observeUnits(id).first().map { it.title }).containsExactly("HTTP")
    }

    @Test
    fun `every part finished means nothing is next`() = runTest {
        val id = course()
        tracks.observeUnits(id).first().forEach { tracks.setUnitState(it.id, TrackUnitState.DONE) }

        val head = observe().first().single()
        assertThat(head.next).isNull()
        assertThat(head.isFinished).isTrue()
    }

    @Test
    fun `a skipped part is finished with, so the one after it is next`() = runTest {
        val id = course()
        tracks.setUnitState(tracks.observeUnits(id).first().first().id, TrackUnitState.SKIPPED)

        assertThat(observe().first().single().next?.title).isEqualTo("REST")
    }

    @Test
    fun `deleting a syllabus leaves its steps on the plan as ordinary steps`() = runTest {
        val id = course()
        items.upsert(PlanFixtures.item(id = 1, kind = ItemKind.TRACK_SESSION, trackId = id))

        delete(id)

        assertThat(tracks.tracks.value).isEmpty()
        val step = items.byId(1)
        assertThat(step?.trackId).isNull()
        assertThat(step?.kind).isEqualTo(ItemKind.DO)
    }

    private companion object {
        val PENDING = TrackUnitState.PENDING
    }
}
