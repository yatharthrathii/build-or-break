package com.buildorbreak.scheduler.alarm

import androidx.test.core.app.ApplicationProvider
import com.buildorbreak.scheduler.notification.NotificationActions
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * How an alarm is addressed.
 *
 * `AlarmManager` cannot be asked what it holds, so the only handle on an alarm
 * already set is a `PendingIntent` that matches the one used to set it. Every
 * property below is what makes cancelling work, and every one of them fails
 * silently in production: an alarm that cannot be cancelled rings for something
 * the user already did, and an alarm that overwrites another never rings at all.
 */
@RunWith(RobolectricTestRunner::class)
class AlarmSchedulingTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `the same occurrence always maps to the same request code`() {
        // Derived rather than allocated, which is why cancelling still works
        // after a reboot with nothing kept in memory.
        assertThat(AlarmScheduling.requestCode(42)).isEqualTo(AlarmScheduling.requestCode(42))
    }

    @Test
    fun `different occurrences get different request codes`() {
        val codes = (1L..1000L).map(AlarmScheduling::requestCode)

        assertThat(codes.toSet()).hasSize(1000)
    }

    @Test
    fun `a request code stays inside the int a pending intent takes`() {
        assertThat(AlarmScheduling.requestCode(Long.MAX_VALUE)).isAtLeast(0)
        assertThat(AlarmScheduling.requestCode(0)).isEqualTo(0)
    }

    @Test
    fun `the occurrence id is in the data uri, not only in an extra`() {
        val intent = AlarmScheduling.fireIntent(context, occurrenceId = 7, itemId = 3)

        // Extras take no part in PendingIntent equality. Two alarms differing
        // only by extra would be the same intent to AlarmManager, and setting the
        // second would silently replace the first.
        assertThat(intent.data.toString()).contains("7")
        assertThat(intent.getLongExtra(AlarmScheduling.EXTRA_OCCURRENCE_ID, -1)).isEqualTo(7L)
        assertThat(intent.getLongExtra(AlarmScheduling.EXTRA_ITEM_ID, -1)).isEqualTo(3L)
    }

    @Test
    fun `two occurrences do not share an intent`() {
        val first = AlarmScheduling.fireIntent(context, occurrenceId = 1, itemId = 1)
        val second = AlarmScheduling.fireIntent(context, occurrenceId = 2, itemId = 1)

        assertThat(first.filterEquals(second)).isFalse()
    }

    @Test
    fun `an alarm can be looked up without being created`() {
        // FLAG_NO_CREATE returning null is how the app can ask whether something
        // is already scheduled rather than blindly setting it again.
        assertThat(AlarmScheduling.pendingIntent(context, 99, 1, create = false)).isNull()

        AlarmScheduling.pendingIntent(context, 99, 1, create = true)

        assertThat(AlarmScheduling.pendingIntent(context, 99, 1, create = false)).isNotNull()
    }

    @Test
    fun `the four notification buttons never collide with each other`() {
        val actions = listOf(
            NotificationActions.ACTION_DONE,
            NotificationActions.ACTION_DONE_MINIMUM,
            NotificationActions.ACTION_SNOOZE,
            NotificationActions.ACTION_SKIP,
        )

        val intents = actions.map { NotificationActions.pendingIntent(context, occurrenceId = 5, action = it) }

        // Four buttons on one notification. Two that collided would leave the
        // same button doing two different things.
        assertThat(intents.toSet()).hasSize(4)
    }

    @Test
    fun `the same button on two occurrences stays distinct`() {
        val first = NotificationActions.pendingIntent(context, 1, NotificationActions.ACTION_DONE)
        val second = NotificationActions.pendingIntent(context, 2, NotificationActions.ACTION_DONE)

        assertThat(first).isNotEqualTo(second)
    }
}
