package com.buildorbreak.app

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.buildorbreak.scheduler.alarm.AlarmRingerService
import com.buildorbreak.scheduler.alarm.AlarmScheduling
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.FileInputStream
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * The alarm, on a real device, doing the three things a notification cannot.
 *
 * Everything else about this app can be checked without a phone. This cannot.
 * Whether a broadcast at six in the morning ends with a sound playing and a
 * screen in front of somebody depends on a foreground service being allowed to
 * start, an audio focus request being granted and a background activity launch
 * getting through, and all three are the platform's decision rather than the
 * code's.
 *
 * The ringer is driven directly rather than through the database on purpose:
 * it was built to take everything it needs in the intent, precisely so that it
 * never has to open a database inside a ten second broadcast budget. That
 * design is what makes it testable here without a seeded plan.
 */
class AlarmDeliveryTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * Notifications on, because that is the case worth testing.
     *
     * From Android 13 the permission is denied until asked for, and a fresh
     * install has never asked. Without it the ringer still plays, the
     * notification never appears, and the test would be measuring the
     * permission rather than the alarm.
     */
    @Before
    fun allowNotifications() {
        shell("pm grant ${'$'}{context.packageName} android.permission.POST_NOTIFICATIONS")

        // Between two tests this process has no activity and no service, so
        // the platform sees a cached app and its freezer may stop it cold. A
        // frozen process cannot answer a foreground start in time, and the
        // platform then kills it for the delay it caused itself. A real alarm
        // never meets this: the broadcast thaws the app before the start.
        shell("am unfreeze --sticky ${'$'}{context.packageName}")

        // The previous test's stop may still be tearing the service down.
        // A start that lands on an instance on its way out inherits its
        // promotion deadline, so each test begins with the ringer gone.
        waitFor { !isRingerRunning() }
        Thread.sleep(SETTLE_MILLIS)
    }

    @After
    fun silence() {
        AlarmRingerService.stop(context, OCCURRENCE_ID)
        Thread.sleep(SETTLE_MILLIS)
    }

    @Test
    fun ringingStartsAForegroundService() {
        AlarmRingerService.start(context, ringIntent())

        assertThat(waitFor { isRingerRunning() }).isTrue()
    }

    @Test
    fun ringingPostsTheNotificationTheLockScreenNeeds() {
        // A permission granted from a test does not always reach a process that
        // is already running, and the platform hides the foreground service
        // notification without it. Skipped rather than failed when that
        // happens: it would be measuring the grant, not the alarm.
        assumeTrue("notifications are off for this install", notificationsAllowed())

        AlarmRingerService.start(context, ringIntent())

        assertThat(waitFor { isRingerRunning() }).isTrue()
        assertThat(waitFor { hasAlarmNotification() }).isTrue()
    }

    @Test
    fun theAlarmScreenComesToTheFrontOverWhateverWasThere() {
        // The emulator's screen may have gone dark since the previous test,
        // and a dark screen has no focused window to find the alarm in. The
        // real alarm turns the screen on itself; the test starts the activity
        // directly and so has to do that part by hand.
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        Thread.sleep(SETTLE_MILLIS)

        AlarmRingerService.start(context, ringIntent())
        waitFor { isRingerRunning() }

        context.startActivity(alarmScreenIntent())

        assertThat(waitFor { isAlarmScreenInFront() }).isTrue()
        shot("alarm-screen")
    }

    /**
     * A start that cannot ring still has to keep the platform's promise.
     *
     * `startForegroundService` obliges the service to promote itself within a
     * few seconds or the platform kills the entire process, and that applies
     * even when the start turns out to have nothing to do. An alarm intent
     * that arrives without its occurrence id is exactly that case, and it used
     * to return quietly and take the app down with it: a crash at six in the
     * morning, in the one code path that exists to be reliable.
     */
    @Test
    fun anAlarmWithNothingToRingStopsInsteadOfKillingTheApp() {
        val nothing = Intent(AlarmRingerService.ACTION_RING)

        AlarmRingerService.start(context, nothing)
        Thread.sleep(FOREGROUND_GRACE_MILLIS)

        assertThat(isAppAlive()).isTrue()
        assertThat(waitFor { !isRingerRunning() }).isTrue()
    }

    /** Stopping is the half that matters at six in the morning. */
    @Test
    fun stoppingTheRingerTakesTheNotificationWithIt() {
        AlarmRingerService.start(context, ringIntent())
        waitFor { isRingerRunning() }

        AlarmRingerService.stop(context, OCCURRENCE_ID)

        assertThat(waitFor { !isRingerRunning() }).isTrue()
        assertThat(waitFor { !hasAlarmNotification() }).isTrue()
    }

    /** The process the ringer runs in, which is the thing the platform kills. */
    private fun isAppAlive(): Boolean = context.getSystemService(ActivityManager::class.java)
        .runningAppProcesses
        .orEmpty()
        .any { it.processName == context.packageName }

    private fun notificationsAllowed(): Boolean =
        context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    /**
     * Kept so a run can be looked at, not only passed.
     *
     * Through UiAutomator rather than the instrumentation's own capture:
     * that one goes through the app's window and comes back black on an
     * emulator, which is the one result worse than no screenshot, because
     * a black rectangle looks like the screen it was taken of.
     */
    private fun shot(name: String) {
        val folder = File(context.getExternalFilesDir(null), "walkthrough").apply { mkdirs() }

        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(File(folder, "$name.png"))
    }

    private fun ringIntent() = Intent(AlarmRingerService.ACTION_RING)
        .putExtra(AlarmRingerService.EXTRA_OCCURRENCE_ID, OCCURRENCE_ID)
        .putExtra(AlarmRingerService.EXTRA_ITEM_ID, ITEM_ID)
        .putExtra(AlarmRingerService.EXTRA_TITLE, "Wake up")
        .putExtra(AlarmRingerService.EXTRA_TIME, "6:40 AM")
        .putExtra(AlarmRingerService.EXTRA_HAS_MINIMUM, false)

    private fun alarmScreenIntent() = Intent(AlarmScheduling.ACTION_ALARM_SCREEN)
        .setPackage(context.packageName)
        .putExtra(AlarmScheduling.EXTRA_OCCURRENCE_ID, OCCURRENCE_ID)
        .putExtra(AlarmScheduling.EXTRA_ITEM_ID, ITEM_ID)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    @Suppress("DEPRECATION")
    private fun isRingerRunning(): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        return manager.getRunningServices(RUNNING_SERVICE_LIMIT)
            .any { it.service.className == AlarmRingerService::class.java.name }
    }

    private fun hasAlarmNotification(): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)

        return manager.activeNotifications.any { it.id == AlarmScheduling.requestCode(OCCURRENCE_ID) }
    }

    private fun isAlarmScreenInFront(): Boolean = shell("dumpsys window")
        .lineSequence()
        .filter { it.contains("mCurrentFocus") }
        .any { it.contains("AlarmActivity") }

    private fun shell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)

        return FileInputStream(descriptor.fileDescriptor).use { it.readBytes().decodeToString() }
    }

    /** Polls rather than sleeps, so a fast device finishes fast and a slow one still passes. */
    private fun waitFor(condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS

        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(POLL_MILLIS)
        }

        return false
    }

    private companion object {
        const val OCCURRENCE_ID = 4321L
        const val ITEM_ID = 99L
        const val TIMEOUT_MILLIS = 15_000L
        const val POLL_MILLIS = 250L
        const val SETTLE_MILLIS = 500L

        /** Longer than the few seconds the platform gives a service to promote itself. */
        const val FOREGROUND_GRACE_MILLIS = 8_000L
        const val RUNNING_SERVICE_LIMIT = 100
    }
}
