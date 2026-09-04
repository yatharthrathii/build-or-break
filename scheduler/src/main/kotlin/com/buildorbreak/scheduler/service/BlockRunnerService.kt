package com.buildorbreak.scheduler.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.scheduler.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Keeps a block of consecutive steps running while the user works through it.
 *
 * A block is five things between 08:00 and 08:30, delivered as one notification
 * and one guided screen rather than five alarms. rules.md section 1 rule 4
 * exists because the alternative is a muted app inside a week.
 *
 * A foreground service, not a background one, and that is not a technicality.
 * The process has to survive the user putting the phone down between two steps,
 * and on Android nothing else survives that reliably. The visible notification
 * is the price, and it is an honest one: something genuinely is running.
 *
 * The service starts when a block begins and stops the moment the last step is
 * settled. A foreground service left running after its work is done is the
 * quickest way onto a battery blame list, and being on one is how an app gets
 * uninstalled.
 */
@AndroidEntryPoint
class BlockRunnerService : Service() {

    @Inject lateinit var dispatchers: AppDispatchers

    private val scope by lazy { CoroutineScope(SupervisorJob() + dispatchers.default) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val blockId = intent?.getLongExtra(EXTRA_BLOCK_ID, NO_ID) ?: NO_ID
        if (blockId == NO_ID) {
            stopSelf()
            return START_NOT_STICKY
        }

        ensureChannel()
        startInForeground()

        // START_NOT_STICKY rather than START_STICKY. If the system killed this
        // mid block, the block is long over by the time it would be restarted,
        // and a notification about a morning routine appearing at four in the
        // afternoon is worse than nothing.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Declared as a special use service.
     *
     * From Android 14 a foreground service must state its type, and none of the
     * standard ones fit: this is not media, not location, not a data sync. Special
     * use requires a Play Console justification, which is the correct amount of
     * friction for something that keeps a process alive.
     */
    private fun startInForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }

        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
    }

    private fun buildNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setContentTitle(getString(R.string.running_block_title))
        .setContentText(getString(R.string.running_block_text))
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    /** Low importance on purpose. It has to be visible; it does not have to interrupt. */
    private fun ensureChannel() {
        getSystemService<NotificationManager>()?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.channel_service), NotificationManager.IMPORTANCE_LOW),
        )
    }

    companion object {
        const val EXTRA_BLOCK_ID = "block_id"

        private const val CHANNEL_ID = "block_runner"
        private const val NOTIFICATION_ID = 2
        private const val NO_ID = -1L

        fun start(context: Context, blockId: Long) {
            context.startForegroundService(
                Intent(context, BlockRunnerService::class.java).putExtra(EXTRA_BLOCK_ID, blockId),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BlockRunnerService::class.java))
        }
    }
}
