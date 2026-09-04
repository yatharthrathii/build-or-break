package com.buildorbreak.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.common.result.getOrNull
import com.buildorbreak.core.data.database.BuildOrBreakDatabase
import com.buildorbreak.core.data.entity.AnchorColumns
import com.buildorbreak.core.data.entity.DayTemplateEntity
import com.buildorbreak.core.data.entity.ItemEntity
import com.buildorbreak.core.data.entity.PlanEntity
import com.buildorbreak.core.model.enums.OccurrenceState
import com.buildorbreak.core.model.enums.Salience
import com.buildorbreak.core.model.plan.Item
import com.buildorbreak.core.model.resolved.ResolvedEntry
import com.buildorbreak.core.testing.coroutines.TestAppDispatchers
import com.buildorbreak.core.testing.fixtures.PlanFixtures.fixedAt
import com.buildorbreak.core.testing.fixtures.PlanFixtures.item
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The repository against a real database rather than a mocked DAO.
 *
 * Mocking the DAO here would test that the repository calls the method it calls,
 * which is not a property worth having. Everything asserted below is a promise
 * the rest of the app relies on and none of it can be observed without the real
 * SQL underneath.
 */
@RunWith(RobolectricTestRunner::class)
class OccurrenceRepositoryImplTest {

    private lateinit var database: BuildOrBreakDatabase
    private lateinit var repository: OccurrenceRepositoryImpl

    private val date: LocalDate = LocalDate.of(2026, 1, 5)
    private var templateId: Long = 0

    private companion object {
        const val EVERY_DAY = 0b111_1111
    }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BuildOrBreakDatabase::class.java,
        ).allowMainThreadQueries().build()

        repository = OccurrenceRepositoryImpl(database.occurrenceDao(), TestAppDispatchers())
    }

    @After
    fun tearDown() = database.close()

    /** Occurrences have a foreign key to items, so the plan has to exist first. */
    private suspend fun storedItem(id: Long, salience: Salience = Salience.NOTIFY): Item {
        if (templateId == 0L) templateId = storedTemplate()

        val rowId = database.itemDao().upsert(itemRow(id, salience))

        return item(id = rowId, salience = salience, anchor = fixedAt(6, 30))
    }

    private suspend fun storedTemplate(): Long {
        val planId = database.planDao().upsert(
            PlanEntity(name = "Weekday", isActive = true, zone = ZoneId.of("Asia/Kolkata"), createdAt = Instant.EPOCH),
        )

        return database.templateDao().upsert(
            DayTemplateEntity(
                planId = planId,
                name = "Weekday",
                weekdays = EVERY_DAY,
                isDefault = true,
                mode = "NORMAL",
                sortOrder = 0,
            ),
        )
    }

    private fun itemRow(id: Long, salience: Salience) = ItemEntity(
        id = id,
        templateId = templateId,
        blockId = null,
        kind = "DO",
        title = "Item $id",
        detail = null,
        durationMinutes = null,
        salience = salience.name,
        weekdays = EVERY_DAY,
        pinned = false,
        minimumTitle = null,
        minimumDurationMinutes = null,
        valueKind = "NONE",
        bundleUri = null,
        trackId = null,
        sortOrder = 0,
        archivedAt = null,
        anchor = AnchorColumns(type = "FIXED"),
    )

    private fun entryFor(
        item: Item,
        hour: Int = 6,
        minute: Int = 30,
        sequence: Int = 0,
    ) = ResolvedEntry(
        item = item,
        block = null,
        at = date.atTime(hour, minute),
        occurrence = null,
        sequenceInDay = sequence,
    )

    @Test
    fun `materialising a day writes one pending row per entry`() = runTest {
        val entries = listOf(entryFor(storedItem(1)), entryFor(storedItem(2), hour = 7))

        val outcome = repository.materialise(entries, date)

        assertThat(outcome).isInstanceOf(Outcome.Success::class.java)
        val stored = repository.observeForDate(date).first()
        assertThat(stored).hasSize(2)
        assertThat(stored.map { it.state }).containsExactly(OccurrenceState.PENDING, OccurrenceState.PENDING)
    }

    @Test
    fun `materialising the same day twice is a no op`() = runTest {
        val entries = listOf(entryFor(storedItem(1)))

        repository.materialise(entries, date)
        repository.materialise(entries, date)

        assertThat(repository.observeForDate(date).first()).hasSize(1)
    }

    @Test
    fun `timeline entries never get a row, because nothing ever points at one`() = runTest {
        val entries = listOf(
            entryFor(storedItem(1, Salience.TIMELINE)),
            entryFor(storedItem(2, Salience.NOTIFY), hour = 8),
        )

        repository.materialise(entries, date)

        assertThat(repository.observeForDate(date).first().map { it.itemId }).containsExactly(2L)
    }

    @Test
    fun `each repeat of an interval item gets its own row`() = runTest {
        val stored = storedItem(1)
        val entries = (0..3).map { entryFor(stored, hour = 11 + it, sequence = it) }

        repository.materialise(entries, date)

        assertThat(repository.observeForDate(date).first().map { it.sequenceInDay })
            .containsExactly(0, 1, 2, 3).inOrder()
    }

    @Test
    fun `settling a row records the state and the moment it happened`() = runTest {
        repository.materialise(listOf(entryFor(storedItem(1))), date)
        val id = repository.observeForDate(date).first().single().id

        repository.settle(id, OccurrenceState.DONE, Instant.ofEpochMilli(5_000))

        val settled = repository.observeForDate(date).first().single()
        assertThat(settled.state).isEqualTo(OccurrenceState.DONE)
        assertThat(settled.settledAt).isEqualTo(Instant.ofEpochMilli(5_000))
        assertThat(settled.isDone).isTrue()
    }

    @Test
    fun `a snooze hands back the row as it now stands`() = runTest {
        repository.materialise(listOf(entryFor(storedItem(1))), date)
        val id = repository.observeForDate(date).first().single().id

        val snoozed = repository.shift(id, 20.minutes).getOrNull()

        // Returned rather than re-read, so the reschedule that follows cannot see
        // a different value than the one it just wrote.
        assertThat(snoozed?.shiftMinutes).isEqualTo(20)
        assertThat(snoozed?.snoozeCount).isEqualTo(1)
        assertThat(snoozed?.effectiveAt).isEqualTo(date.atTime(6, 50))
    }

    @Test
    fun `nothing is reported as a missed alarm until it was actually scheduled`() = runTest {
        repository.materialise(listOf(entryFor(storedItem(1))), date)

        // A row exists and its time has passed, but no alarm was ever set for it,
        // so there is nothing for the reconcile pass to put right.
        assertThat(repository.pendingBefore(Instant.ofEpochMilli(9_999))).isEmpty()
    }

    @Test
    fun `an empty day is a success rather than an error`() = runTest {
        assertThat(repository.materialise(emptyList(), date)).isInstanceOf(Outcome.Success::class.java)
    }
}
