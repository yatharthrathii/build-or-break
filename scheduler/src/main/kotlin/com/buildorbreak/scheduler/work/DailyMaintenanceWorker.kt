package com.buildorbreak.scheduler.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.buildorbreak.core.common.time.TimeProvider
import com.buildorbreak.core.domain.repository.DeliveryAuditRepository
import com.buildorbreak.core.domain.usecase.CloseDayUseCase
import com.buildorbreak.core.domain.usecase.RescheduleAllUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Duration as JavaDuration
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/** How long delivery audit rows are kept before the reliability figure stops needing them. */
private const val AUDIT_RETENTION_DAYS = 180L

/** The daily job aims for just after midnight, when the day it closes is genuinely over. */
private val RUN_AT: LocalTime = LocalTime.of(0, 5)

/**
 * Everything that has to happen once a day, whether or not the app is opened.
 *
 * architecture.md section 6.3. Closes yesterday, materialises today, puts every
 * alarm back and prunes the audit table.
 *
 * **It cannot be relied on to run.** WorkManager is best effort, and on a phone
 * with an aggressive battery manager it may not run for days. That is why
 * `CloseDayUseCase` closes every unclosed day rather than only yesterday, and
 * why the app also triggers this work on launch. The worker is the fast path,
 * not the only path, and designing it as the only path is how a routine app ends
 * up with a hole in three weeks of somebody's history.
 */
class DailyMaintenanceWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    /**
     * Dependencies are pulled from Hilt rather than injected into the
     * constructor.
     *
     * `@HiltWorker` would be tidier and needs a `hilt-work` dependency, a custom
     * `WorkerFactory` and a `Configuration.Provider` on the application. For one
     * worker that is three moving parts to save four lines, and every one of them
     * is a thing that can be misconfigured into a worker that silently never
     * runs.
     */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface MaintenanceEntryPoint {
        fun closeDay(): CloseDayUseCase
        fun reschedule(): RescheduleAllUseCase
        fun audits(): DeliveryAuditRepository
        fun time(): TimeProvider
    }

    override suspend fun doWork(): Result {
        val entry = EntryPointAccessors.fromApplication(applicationContext, MaintenanceEntryPoint::class.java)

        return runCatching {
            // Order matters. Closing first settles yesterday's open rows, so the
            // reschedule that follows is working from a day that is finished
            // rather than one still half open.
            entry.closeDay()()
            entry.reschedule()()
            entry.audits().pruneBefore(entry.time().now().minus(AUDIT_RETENTION_DAYS, ChronoUnit.DAYS))

            Result.success()
        }.getOrElse {
            // Retried with WorkManager's own backoff. A day that failed to close
            // is picked up by the next run anyway, so this is a nudge rather
            // than the only chance.
            Result.retry()
        }
    }

    companion object {
        const val PERIODIC_NAME = "daily-maintenance"
        const val ONE_SHOT_NAME = "daily-maintenance-once"

        /**
         * Scheduled for just after midnight, and kept if one already exists.
         *
         * `KEEP` rather than `REPLACE` on purpose. This is called on every launch,
         * and replacing would restart the delay each time, so an app opened daily
         * would have a periodic job whose period never elapses.
         */
        fun enqueuePeriodic(context: Context, now: java.time.LocalDateTime) {
            val nextRun = now.toLocalDate().plusDays(1).atTime(RUN_AT)
            val delay = JavaDuration.between(now, nextRun).toMinutes().coerceAtLeast(1)

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<DailyMaintenanceWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(delay, TimeUnit.MINUTES)
                    .build(),
            )
        }

        /** Runs the same work now. Used after a locked boot, and on launch. */
        fun enqueueOnce(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_SHOT_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<DailyMaintenanceWorker>().build(),
            )
        }
    }
}
