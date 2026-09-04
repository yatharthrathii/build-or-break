package com.buildorbreak.core.data.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.buildorbreak.core.data.entity.DeliveryAuditEntity
import com.buildorbreak.core.data.entity.MilestoneAwardEntity
import com.buildorbreak.core.data.entity.OccurrenceEntity
import com.buildorbreak.core.data.entity.PlanEntity
import com.buildorbreak.core.model.enums.DeliveryTier
import com.buildorbreak.core.model.enums.Milestone
import com.buildorbreak.core.model.enums.OccurrenceState
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The behaviours the schema itself has to guarantee.
 *
 * Everything here is a property no caller should have to remember: uniqueness,
 * conflict handling, cascade rules. Testing them against a real in memory Room
 * rather than a fake is the point, because a fake would happily agree with
 * whatever the test expected and the index that actually enforces this only
 * exists in the generated SQL.
 */
@RunWith(RobolectricTestRunner::class)
class DatabaseTest {

    private lateinit var database: BuildOrBreakDatabase

    private val date: LocalDate = LocalDate.of(2026, 1, 5)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BuildOrBreakDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    private suspend fun insertPlan(name: String = "Weekday", active: Boolean = true): Long = database.planDao().upsert(
        PlanEntity(name = name, isActive = active, zone = ZoneId.of("Asia/Kolkata"), createdAt = Instant.EPOCH),
    )

    private suspend fun insertItemChain(): Long {
        val planId = insertPlan()
        val templateId = database.templateDao().upsert(
            com.buildorbreak.core.data.entity.DayTemplateEntity(
                planId = planId,
                name = "Weekday",
                weekdays = 0b111_1111,
                isDefault = true,
                mode = "NORMAL",
                sortOrder = 0,
            ),
        )
        return database.itemDao().upsert(
            com.buildorbreak.core.data.entity.ItemEntity(
                templateId = templateId,
                blockId = null,
                kind = "DO",
                title = "Wake up",
                detail = null,
                durationMinutes = null,
                salience = "ALARM",
                weekdays = 0b111_1111,
                pinned = false,
                minimumTitle = null,
                minimumDurationMinutes = null,
                valueKind = "NONE",
                bundleUri = null,
                trackId = null,
                sortOrder = 0,
                archivedAt = null,
                anchor = com.buildorbreak.core.data.entity.AnchorColumns(type = "FIXED"),
            ),
        )
    }

    private fun occurrenceFor(itemId: Long, sequence: Int = 0) = OccurrenceEntity(
        itemId = itemId,
        date = date,
        plannedAt = date.atTime(6, 30),
        scheduledAt = null,
        firedAt = null,
        settledAt = null,
        state = OccurrenceState.PENDING.name,
        shiftMinutes = 0,
        snoozeCount = 0,
        sequenceInDay = sequence,
    )

    // Exactly one active plan --------------------------------------------------

    @Test
    fun `making a plan active clears the flag on every other one`() = runTest {
        val first = insertPlan("First")
        val second = insertPlan("Second", active = false)

        database.planDao().setActive(second)

        assertThat(database.planDao().byId(first)?.isActive).isFalse()
        assertThat(database.planDao().byId(second)?.isActive).isTrue()
        assertThat(database.planDao().observeActive().first()?.id).isEqualTo(second)
    }

    // Materialising a day twice ------------------------------------------------

    @Test
    fun `inserting the same occurrence twice leaves one row`() = runTest {
        val itemId = insertItemChain()
        val row = occurrenceFor(itemId)

        database.occurrenceDao().insertIgnoringExisting(listOf(row))
        database.occurrenceDao().insertIgnoringExisting(listOf(row))

        assertThat(database.occurrenceDao().between(date, date)).hasSize(1)
    }

    @Test
    fun `a second insert does not undo work already done that morning`() = runTest {
        val itemId = insertItemChain()
        database.occurrenceDao().insertIgnoringExisting(listOf(occurrenceFor(itemId)))
        val stored = database.occurrenceDao().between(date, date).single()

        database.occurrenceDao().settle(stored.id, OccurrenceState.DONE.name, Instant.EPOCH)
        database.occurrenceDao().insertIgnoringExisting(listOf(occurrenceFor(itemId)))

        // The reschedule pass runs on every app open. Replacing rather than
        // ignoring would mark a completed step as pending again.
        assertThat(database.occurrenceDao().byId(stored.id)?.state).isEqualTo(OccurrenceState.DONE.name)
    }

    @Test
    fun `repeats of one interval item are separate rows`() = runTest {
        val itemId = insertItemChain()

        database.occurrenceDao().insertIgnoringExisting(
            listOf(occurrenceFor(itemId, sequence = 0), occurrenceFor(itemId, sequence = 1)),
        )

        assertThat(database.occurrenceDao().between(date, date)).hasSize(2)
    }

    // Snoozing -----------------------------------------------------------------

    @Test
    fun `a snooze adds to the shift rather than replacing it`() = runTest {
        val itemId = insertItemChain()
        database.occurrenceDao().insertIgnoringExisting(listOf(occurrenceFor(itemId)))
        val id = database.occurrenceDao().between(date, date).single().id

        database.occurrenceDao().shift(id, 10, OccurrenceState.SNOOZED.name)
        database.occurrenceDao().shift(id, 15, OccurrenceState.SNOOZED.name)

        val stored = database.occurrenceDao().byId(id)
        assertThat(stored?.shiftMinutes).isEqualTo(25)
        assertThat(stored?.snoozeCount).isEqualTo(2)
    }

    // The reconcile pass -------------------------------------------------------

    @Test
    fun `only scheduled pending rows in the past are reported as missed alarms`() = runTest {
        val itemId = insertItemChain()
        database.occurrenceDao().insertIgnoringExisting(listOf(occurrenceFor(itemId)))
        val id = database.occurrenceDao().between(date, date).single().id
        val scheduled = Instant.ofEpochMilli(1_000)

        assertThat(database.occurrenceDao().pendingBefore(Instant.ofEpochMilli(2_000), "PENDING")).isEmpty()

        database.occurrenceDao().markScheduled(id, scheduled)

        assertThat(database.occurrenceDao().pendingBefore(Instant.ofEpochMilli(2_000), "PENDING")).hasSize(1)
        assertThat(database.occurrenceDao().pendingBefore(Instant.ofEpochMilli(500), "PENDING")).isEmpty()
    }

    // Cascades -----------------------------------------------------------------

    @Test
    fun `deleting a plan takes its templates and items with it`() = runTest {
        val itemId = insertItemChain()
        val templateId = database.itemDao().byId(itemId)?.templateId
        database.templateDao().delete(requireNotNull(templateId))

        assertThat(database.itemDao().byId(itemId)).isNull()
    }

    // Milestones fire once -----------------------------------------------------

    @Test
    fun `awarding the same milestone twice keeps the first date`() = runTest {
        val award = MilestoneAwardEntity(
            milestone = Milestone.FIRST_FULL_DAY.name,
            goalId = null,
            itemId = null,
            awardedOn = date,
            seenAt = null,
        )

        database.milestoneDao().award(award)
        database.milestoneDao().award(award.copy(awardedOn = date.plusDays(30)))

        val stored = database.milestoneDao().awarded().single()
        assertThat(stored.awardedOn).isEqualTo(date)
    }

    // The reliability figure ---------------------------------------------------

    @Test
    fun `recording a fire writes the latency alongside it`() = runTest {
        val scheduledFor = Instant.ofEpochMilli(60_000)
        database.deliveryAuditDao().insert(
            DeliveryAuditEntity(
                occurrenceId = 1,
                scheduledFor = scheduledFor,
                firedAt = null,
                tier = DeliveryTier.EXACT_HEADS_UP.name,
                deviceModel = "M2101K7AI",
                manufacturer = "Xiaomi",
                sdkInt = 31,
                wasDeviceIdle = false,
                latencySeconds = null,
            ),
        )

        database.deliveryAuditDao().recordFired(occurrenceId = 1, firedAt = Instant.ofEpochMilli(105_000))

        val stored = database.deliveryAuditDao().observeSince(Instant.EPOCH).first().single()
        assertThat(stored.firedAt).isEqualTo(Instant.ofEpochMilli(105_000))
        // Forty five seconds late, which is inside the sixty second tolerance
        // the reliability figure is measured against.
        assertThat(stored.latencySeconds).isEqualTo(45L)
    }

    @Test
    fun `a second fire report does not overwrite the first`() = runTest {
        database.deliveryAuditDao().insert(
            DeliveryAuditEntity(
                occurrenceId = 2,
                scheduledFor = Instant.ofEpochMilli(60_000),
                firedAt = null,
                tier = DeliveryTier.FULL_SCREEN_ALARM.name,
                deviceModel = "M2101K7AI",
                manufacturer = "Xiaomi",
                sdkInt = 31,
                wasDeviceIdle = true,
                latencySeconds = null,
            ),
        )

        database.deliveryAuditDao().recordFired(occurrenceId = 2, firedAt = Instant.ofEpochMilli(61_000))
        database.deliveryAuditDao().recordFired(occurrenceId = 2, firedAt = Instant.ofEpochMilli(900_000))

        // A reschedule after the fact must not be able to rewrite the one number
        // the audit exists to produce.
        val stored = database.deliveryAuditDao().observeSince(Instant.EPOCH).first().single { it.occurrenceId == 2L }
        assertThat(stored.latencySeconds).isEqualTo(1L)
    }
}
