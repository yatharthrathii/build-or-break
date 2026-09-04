package com.buildorbreak.scheduler.receiver

import android.content.BroadcastReceiver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Runs suspending work for a broadcast receiver and always releases the wake
 * lock afterwards.
 *
 * `goAsync` hands back a token that keeps the process alive, and forgetting to
 * finish it holds a wake lock until the platform gives up. Every receiver in
 * this module goes through here so that the `finally` cannot be left out of one
 * of them, which is the kind of omission that shows up as a battery complaint
 * three months later and is almost impossible to attribute.
 *
 * Anything that takes longer than about ten seconds will be killed mid flight.
 * That is the platform's rule and not something to work around: work that cannot
 * fit belongs in `DailyMaintenanceWorker`, which can take as long as it needs.
 */
fun BroadcastReceiver.PendingResult.finishAfter(dispatcher: CoroutineDispatcher, block: suspend () -> Unit) {
    CoroutineScope(dispatcher).launch {
        try {
            block()
        } finally {
            finish()
        }
    }
}
