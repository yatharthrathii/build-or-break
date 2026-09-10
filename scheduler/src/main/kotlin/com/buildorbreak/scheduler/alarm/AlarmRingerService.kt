package com.buildorbreak.scheduler.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.scheduler.notification.AlarmNotification
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The part that actually wakes somebody up.
 *
 * A notification is not an alarm. Its sound is whatever the channel was given,
 * it plays once for a few seconds, and if the screen happens to be on the
 * platform shows a heads up banner instead of anything else. That is fine for a
 * reminder and useless at six in the morning, which is the difference between
 * this app working and this app being deleted.
 *
 * So an alarm step rings from here: the alarm stream, on a loop, with the
 * vibrator running, until somebody says what happened. The notification this
 * service posts carries the full screen intent, which is what asks the platform
 * to put the alarm screen in front of a locked phone.
 *
 * It gives up on its own after [RING_LIMIT]. A phone ringing for an hour in an
 * empty room is a phone whose owner turns the app off, and the notification is
 * left behind afterwards so the step is not lost with the sound.
 */
@AndroidEntryPoint
class AlarmRingerService : Service() {

    @Inject lateinit var dispatchers: AppDispatchers

    @Inject lateinit var notifications: AlarmNotification

    private val scope by lazy { CoroutineScope(dispatchers.default) }

    private var player: MediaPlayer? = null
    private var ringing: Long = NO_ID
    private var timeout: Job? = null

    /**
     * Whether this service has told the platform it is in the foreground.
     *
     * Tracked because the obligation is per service instance, not per start,
     * and every path out of [onStartCommand] has to have met it.
     */
    private var promoted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val occurrenceId = intent?.getLongExtra(EXTRA_OCCURRENCE_ID, NO_ID) ?: NO_ID

        when {
            intent?.action == ACTION_RING && occurrenceId != NO_ID -> ring(intent, occurrenceId)

            intent?.action == ACTION_STOP && (occurrenceId == ringing || occurrenceId == NO_ID) -> stop()

            // Anything else: a stop for a different step, a start with no id, a
            // redelivery with its extras gone. None of them ring. The one that
            // arrives while an alarm is already going is ignored, because the
            // service is foreground already and the alarm must not be cut off.
            // Every other one has to leave properly. See [standDown].
            else -> if (!promoted) standDown()
        }

        // Never restarted by the platform. An alarm the system decided to
        // resurrect at a later moment of its own choosing would ring for a step
        // that is long past, which is worse than one that did not ring at all.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        silence()
        scope.cancel()
        super.onDestroy()
    }

    /**
     * The notification goes up first.
     *
     * From Android 12 a foreground service has a few seconds to post its
     * notification or the platform kills the process, and that budget is not
     * the place to be opening an audio device.
     */
    private fun ring(intent: Intent, occurrenceId: Long) {
        silence()
        ringing = occurrenceId

        ServiceCompat.startForeground(
            this,
            AlarmScheduling.requestCode(occurrenceId),
            notifications.ringing(intent),
            foregroundType(),
        )
        promoted = true

        startSound()
        startVibration()

        timeout = scope.launch {
            delay(RING_LIMIT)
            // Detached, so the notification outlives the service and the step
            // is still there to be dealt with.
            ServiceCompat.stopForeground(this@AlarmRingerService, ServiceCompat.STOP_FOREGROUND_DETACH)
            silence()
            ringing = NO_ID
            stopSelf()
        }
    }

    private fun stop() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        silence()
        ringing = NO_ID
        promoted = false
        stopSelf()
    }

    /**
     * Leaves without ringing, having first done what the platform demands.
     *
     * `startForegroundService` is a promise, and the platform enforces it by
     * killing the process when it is not kept, whatever the reason the service
     * had for changing its mind. Returning quietly from a start that has
     * nothing to do therefore crashes the app rather than doing nothing, and it
     * crashes it in the one place that must never fail: an alarm going off.
     *
     * So the promise is kept and immediately released. The placeholder is on
     * screen for a few milliseconds and nobody sees it.
     */
    private fun standDown() {
        if (!promoted) {
            ServiceCompat.startForeground(this, PLACEHOLDER_ID, notifications.placeholder(), foregroundType())
            promoted = true
        }

        stop()
    }

    /**
     * Plays what the phone calls an alarm, on the alarm stream.
     *
     * The stream matters more than the sound. Alarm volume is the one level a
     * user does not turn down by accident, it plays through a silenced ringer,
     * and it is the only stream that behaves the way somebody expects of the
     * thing that wakes them.
     */
    private fun startSound() {
        val tone = alarmTone() ?: return

        player = MediaPlayer().apply {
            @Suppress("SwallowedException", "TooGenericExceptionCaught")
            try {
                setDataSource(this@AlarmRingerService, tone)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                isLooping = true
                // Starts quiet and climbs. Somebody who wakes on the first
                // gentle pass gets to stop it before it wakes the whole room,
                // and anybody who does not is dealt with a few seconds later.
                setVolume(FIRST_VOLUME, FIRST_VOLUME)
                prepare()
                start()
                rampUp()
            } catch (failed: Exception) {
                release()
                player = null
            }
        }
    }

    private fun rampUp() = scope.launch {
        var level = FIRST_VOLUME

        while (level < 1f) {
            delay(RAMP_STEP)
            level = (level + RAMP_INCREMENT).coerceAtMost(1f)

            @Suppress("SwallowedException")
            try {
                player?.setVolume(level, level)
            } catch (gone: IllegalStateException) {
                return@launch
            }
        }
    }

    /** The user's own alarm sound, then any ringtone. Never silence pretending to ring. */
    private fun alarmTone(): Uri? = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

    /**
     * Vibrates as an alarm, which is a category the platform treats specially.
     *
     * Saying so is what lets it through a phone set to vibrate only, and on
     * Android 13 the way of saying it changed from audio attributes to
     * vibration attributes. Both name the same usage.
     */
    private fun startVibration() {
        val vibrator = vibrator() ?: return
        if (!vibrator.hasVibrator()) return

        val pattern = VibrationEffect.createWaveform(PATTERN, REPEAT_FROM)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(pattern, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(
                pattern,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
    }

    private fun silence() {
        timeout?.cancel()
        timeout = null

        player?.let { open ->
            @Suppress("SwallowedException")
            try {
                open.stop()
            } catch (already: IllegalStateException) {
                Unit
            }
            open.release()
        }
        player = null

        vibrator()?.cancel()
    }

    private fun vibrator(): Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getSystemService<VibratorManager>()?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService<Vibrator>()
    }

    /**
     * Declared as media playback, because that is what it is.
     *
     * From Android 14 a foreground service has to name its type and the
     * platform checks that the service does what it claimed. This one opens an
     * audio device and plays a sound the user hears. Anything else would be a
     * worse description and a harder thing to justify on Play.
     */
    private fun foregroundType(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
    } else {
        0
    }

    companion object {
        const val ACTION_RING = "com.buildorbreak.scheduler.ACTION_RING"
        const val ACTION_STOP = "com.buildorbreak.scheduler.ACTION_STOP_RING"

        const val EXTRA_OCCURRENCE_ID = AlarmScheduling.EXTRA_OCCURRENCE_ID
        const val EXTRA_ITEM_ID = AlarmScheduling.EXTRA_ITEM_ID
        const val EXTRA_TITLE = "title"
        const val EXTRA_DETAIL = "detail"
        const val EXTRA_TIME = "time"
        const val EXTRA_HAS_MINIMUM = "has_minimum"

        private const val NO_ID = -1L

        /** Its own id, so a placeholder can never replace a live alarm's notification. */
        private const val PLACEHOLDER_ID = 424_242

        /** Ten minutes, the same as the phone's own alarm app. */
        private val RING_LIMIT = 10.minutes

        private const val FIRST_VOLUME = 0.18f
        private const val RAMP_INCREMENT = 0.06f
        private val RAMP_STEP = 900.milliseconds

        private val PATTERN = longArrayOf(0, 500, 700, 500, 1500)
        private const val REPEAT_FROM = 0

        /**
         * Started from the alarm broadcast, which is one of the few exemptions
         * the platform makes to the rule against starting a foreground service
         * from the background.
         */
        fun start(context: Context, intent: Intent) {
            context.startForegroundService(intent.setClass(context, AlarmRingerService::class.java))
        }

        fun stop(context: Context, occurrenceId: Long) {
            context.startService(
                Intent(context, AlarmRingerService::class.java)
                    .setAction(ACTION_STOP)
                    .putExtra(EXTRA_OCCURRENCE_ID, occurrenceId),
            )
        }
    }
}
