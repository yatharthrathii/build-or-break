package com.buildorbreak.scheduler.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.domain.usecase.RescheduleAllUseCase
import com.buildorbreak.scheduler.work.DailyMaintenanceWorker
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Every alarm is gone. Put them all back.
 *
 * `AlarmManager` keeps nothing across a restart, so without this a phone that
 * rebooted overnight wakes up with an app that looks fine and will not ring
 * again. It is the single most common way a routine app silently stops working,
 * and the user has no way to tell until the morning it matters.
 *
 * Only `BOOT_COMPLETED`, which arrives once the user has unlocked. The locked
 * boot broadcast comes hours earlier on a phone that restarted overnight, but
 * nothing here can use it: the database, and WorkManager's own database with
 * it, live in credential encrypted storage and cannot be opened until the
 * unlock. Queuing work from that state is a crash, not a head start.
 *
 * The day close is queued as well as the reschedule. A phone that was off
 * for a week has days to settle, and the close is what settles them.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var reschedule: RescheduleAllUseCase

    @Inject lateinit var dispatchers: AppDispatchers

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        DailyMaintenanceWorker.enqueueOnce(context)
        goAsync().finishAfter(dispatchers.io) { reschedule() }
    }
}

/**
 * The clock moved, so every alarm is now set for the wrong moment.
 *
 * A timezone change is the sharp case. Flying two hours east does not change
 * what the plan says: waking at six means six where the user now is, and the
 * alarms already set are for six somewhere else. Because the resolver works in
 * local time and the reschedule pass recomputes from the plan, putting this
 * right is one call rather than a pile of arithmetic.
 *
 * A date change matters just as much and is easier to forget: at midnight the
 * day the app should be scheduling becomes a different day.
 */
@AndroidEntryPoint
class TimeChangeReceiver : BroadcastReceiver() {

    @Inject lateinit var reschedule: RescheduleAllUseCase

    @Inject lateinit var dispatchers: AppDispatchers

    override fun onReceive(context: Context, intent: Intent) {
        val relevant = intent.action in setOf(
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
        )
        if (!relevant) return

        goAsync().finishAfter(dispatchers.io) { reschedule() }
    }
}

/**
 * The app was updated, which also clears every alarm it had set.
 *
 * Easy to miss because it only happens on a release, and it fails in exactly the
 * way that is hardest to notice: everything works in testing, and the morning
 * after an update on somebody's phone is silent.
 */
@AndroidEntryPoint
class PackageReplacedReceiver : BroadcastReceiver() {

    @Inject lateinit var reschedule: RescheduleAllUseCase

    @Inject lateinit var dispatchers: AppDispatchers

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        goAsync().finishAfter(dispatchers.io) { reschedule() }
    }
}
