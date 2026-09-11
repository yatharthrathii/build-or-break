package com.buildorbreak.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.buildorbreak.core.data.database.BuildOrBreakDatabase
import com.buildorbreak.core.model.audit.DeliveryAudit
import com.buildorbreak.core.model.enums.DeliveryTier
import com.buildorbreak.core.testing.coroutines.TestAppDispatchers
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * One delivery, one row.
 *
 * The rescheduling pass sets the same alarm again on every app open and every
 * completion, and each pass used to add a row. An alarm that fired once looked
 * like three that were scheduled, and a step done ten minutes early looked
 * like an alarm the phone failed to deliver. The reliability figure is the one
 * number in the app that has to be measured rather than flattering, so these
 * are pinned against the real SQL.
 */
@RunWith(RobolectricTestRunner::class)
class DeliveryAuditRepositoryImplTest {

    private lateinit var database: BuildOrBreakDatabase
    private lateinit var repository: DeliveryAuditRepositoryImpl

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BuildOrBreakDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = DeliveryAuditRepositoryImpl(database.deliveryAuditDao(), TestAppDispatchers())
    }

    @After
    fun tearDown() = database.close()

    private fun audit(occurrenceId: Long = 1, at: Instant = Instant.ofEpochMilli(60_000)) = DeliveryAudit(
        id = 0,
        occurrenceId = occurrenceId,
        scheduledFor = at,
        firedAt = null,
        tier = DeliveryTier.FULL_SCREEN_ALARM,
        deviceModel = "Pixel 8",
        manufacturer = "Google",
        sdkInt = 36,
        wasDeviceIdle = false,
        latencySeconds = null,
    )

    private suspend fun rows() = repository.observeSince(Instant.EPOCH).first()

    @Test
    fun `scheduling the same step again moves its row rather than adding one`() = runTest {
        repository.recordScheduled(audit(at = Instant.ofEpochMilli(60_000)))
        repository.recordScheduled(audit(at = Instant.ofEpochMilli(90_000)))

        assertThat(rows()).hasSize(1)
        assertThat(rows().single().scheduledFor).isEqualTo(Instant.ofEpochMilli(90_000))
    }

    @Test
    fun `an alarm cancelled before it fired leaves no row behind`() = runTest {
        repository.recordScheduled(audit())

        repository.discardUnfired(occurrenceId = 1)

        assertThat(rows()).isEmpty()
    }

    @Test
    fun `a fired row is never moved by a later schedule of the same step`() = runTest {
        repository.recordScheduled(audit(at = Instant.ofEpochMilli(60_000)))
        repository.recordFired(occurrenceId = 1, firedAt = Instant.ofEpochMilli(61_000))

        repository.recordScheduled(audit(at = Instant.ofEpochMilli(600_000)))

        val stored = rows().sortedBy { it.scheduledFor }
        assertThat(stored).hasSize(2)
        assertThat(stored.first().fired).isTrue()
        assertThat(stored.first().scheduledFor).isEqualTo(Instant.ofEpochMilli(60_000))
        assertThat(stored.last().fired).isFalse()
    }

    @Test
    fun `a cancel after a fire keeps the record of the fire`() = runTest {
        repository.recordScheduled(audit())
        repository.recordFired(occurrenceId = 1, firedAt = Instant.ofEpochMilli(61_000))

        repository.discardUnfired(occurrenceId = 1)

        assertThat(rows().single().fired).isTrue()
    }

    @Test
    fun `steps do not share rows`() = runTest {
        repository.recordScheduled(audit(occurrenceId = 1))
        repository.recordScheduled(audit(occurrenceId = 2))

        repository.discardUnfired(occurrenceId = 1)

        assertThat(rows().map { it.occurrenceId }).containsExactly(2L)
    }
}
