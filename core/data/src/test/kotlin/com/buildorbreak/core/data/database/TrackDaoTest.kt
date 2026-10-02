package com.buildorbreak.core.data.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.buildorbreak.core.data.entity.PlanEntity
import com.buildorbreak.core.data.entity.TrackEntity
import com.buildorbreak.core.data.entity.TrackUnitEntity
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A syllabus written twice.
 *
 * The second write is the one that bit: Room's upsert answers with minus
 * one when it updated rather than inserted, and the parts were briefly
 * written against a track that did not exist. Against a real in memory Room,
 * because a fake upsert hands back whatever the test expects.
 */
@RunWith(RobolectricTestRunner::class)
class TrackDaoTest {

    private lateinit var database: BuildOrBreakDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BuildOrBreakDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    private fun unit(
        trackId: Long,
        ordinal: Int,
        title: String,
        id: Long = 0,
    ) = TrackUnitEntity(
        id = id,
        trackId = trackId,
        ordinal = ordinal,
        title = title,
        estimateMinutes = null,
        state = "PENDING",
    )

    private suspend fun course(): Long {
        val planId = database.planDao().upsert(
            PlanEntity(name = "Study", isActive = true, zone = ZoneId.of("Asia/Kolkata"), createdAt = Instant.EPOCH),
        )

        return database.trackDao().upsertWithUnits(
            TrackEntity(planId = planId, name = "Backend", sourceText = "REST", createdAt = Instant.EPOCH),
            listOf(unit(0, 0, "REST")),
        )
    }

    @Test
    fun `writing an existing syllabus again keeps its id and replaces its parts`() = runTest {
        val id = course()
        val kept = database.trackDao().observeUnits(id).first().single()

        val again = database.trackDao().upsertWithUnits(
            TrackEntity(
                id = id,
                planId = 1,
                name = "Backend",
                sourceText = "HTTP\nREST\nAuth",
                createdAt = Instant.EPOCH,
            ),
            listOf(unit(id, 0, "HTTP", id = kept.id), unit(id, 1, "REST"), unit(id, 2, "Auth")),
        )

        assertThat(again).isEqualTo(id)
        val units = database.trackDao().observeUnits(id).first()
        assertThat(units.map { it.title }).containsExactly("HTTP", "REST", "Auth").inOrder()
        assertThat(units.first().id).isEqualTo(kept.id)
    }

    @Test
    fun `parts not on the new list are dropped, whatever position they held`() = runTest {
        val id = course()
        database.trackDao().upsertWithUnits(
            TrackEntity(id = id, planId = 1, name = "Backend", sourceText = "A\nB", createdAt = Instant.EPOCH),
            listOf(unit(id, 0, "A"), unit(id, 1, "B")),
        )

        database.trackDao().upsertWithUnits(
            TrackEntity(id = id, planId = 1, name = "Backend", sourceText = "A", createdAt = Instant.EPOCH),
            listOf(unit(id, 0, "A")),
        )

        assertThat(database.trackDao().observeUnits(id).first().map { it.title }).containsExactly("A")
    }
}
