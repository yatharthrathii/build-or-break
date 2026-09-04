package com.buildorbreak.app.startup

import android.content.Context
import androidx.startup.Initializer
import com.buildorbreak.core.common.time.TimeProvider
import com.buildorbreak.core.domain.usecase.RescheduleAllUseCase
import com.buildorbreak.scheduler.notification.Channels
import com.buildorbreak.scheduler.work.DailyMaintenanceWorker
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Puts the alarms back on every launch.
 *
 * The daily worker is best effort and a phone with an aggressive battery manager
 * may not run it for days, so launch is the second path to the same work. That
 * redundancy is the point: the reschedule pass is idempotent, so running it here
 * as well costs nothing and covers the case where nothing else did.
 *
 * Runs through androidx.startup rather than `Application.onCreate`, so the work
 * is named and ordered rather than dropped into a method that grows. rules.md
 * section 5 budgets cold start under 500 ms, and everything below either returns
 * immediately or is launched and left.
 */
class SchedulerStartup : Initializer<Unit> {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface StartupEntryPoint {
        fun reschedule(): RescheduleAllUseCase
        fun time(): TimeProvider
    }

    override fun create(context: Context) {
        // Cheap, and it has to happen before anything is posted. Creating a
        // channel that already exists updates its name and leaves the user's own
        // importance setting alone, so this is safe on every launch.
        Channels.ensureCreated(context)

        val entry = EntryPointAccessors.fromApplication(context, StartupEntryPoint::class.java)

        DailyMaintenanceWorker.enqueuePeriodic(context, entry.time().localNow())

        // Off the main thread and not waited on. A cold start must not block on
        // a database read, and if this is killed before it finishes the worker
        // and the next launch will both do it again.
        CoroutineScope(Dispatchers.Default).launch { entry.reschedule() }
    }

    /**
     * Nothing to wait for. WorkManager initialises itself on the first call to
     * `getInstance`, so naming its initializer here would only add a compile
     * time dependency on a class this module has no other reason to see.
     */
    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
