package com.buildorbreak.scheduler.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.domain.usecase.CompleteItemUseCase
import com.buildorbreak.core.domain.usecase.SkipItemUseCase
import com.buildorbreak.core.domain.usecase.SnoozeItemUseCase
import com.buildorbreak.scheduler.receiver.finishAfter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

/**
 * Somebody tapped a button on the notification.
 *
 * **The activity is never launched.** This is the requirement that shapes the
 * whole architecture: appflow.md wants eighty percent of interactions to finish
 * without opening the app, which is only possible if the use case layer can be
 * called from here as easily as from a ViewModel. That is the main reason use
 * cases exist at all rather than the ViewModels talking to repositories.
 *
 * Each branch settles the occurrence, cancels what is no longer due, reschedules
 * everything downstream and refreshes the widget. All of that lives in the use
 * case, so this class stays a translation from an intent to a call and has no
 * product logic to get wrong.
 */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject lateinit var complete: CompleteItemUseCase

    @Inject lateinit var snooze: SnoozeItemUseCase

    @Inject lateinit var skip: SkipItemUseCase

    @Inject lateinit var dispatchers: AppDispatchers

    override fun onReceive(context: Context, intent: Intent) {
        val occurrenceId = intent.getLongExtra(NotificationActions.EXTRA_OCCURRENCE_ID, NO_ID)
        if (occurrenceId == NO_ID) return

        val minutes = intent.getIntExtra(
            NotificationActions.EXTRA_SNOOZE_MINUTES,
            NotificationActions.DEFAULT_SNOOZE_MINUTES,
        )

        goAsync().finishAfter(dispatchers.io) {
            when (intent.action) {
                NotificationActions.ACTION_DONE -> complete(occurrenceId)

                // The smaller version counts as done. Scaling down on a bad day
                // is succeeding at what was planned for, and scoring it as a
                // partial failure would teach somebody to skip instead.
                NotificationActions.ACTION_DONE_MINIMUM -> complete(occurrenceId, minimum = true)

                NotificationActions.ACTION_SNOOZE -> snooze(occurrenceId, minutes.minutes)

                // No reason is asked for here. Requiring somebody to justify
                // themselves at the moment they are already having a bad day is
                // how the data stops arriving at all. The app offers a chip
                // later, and it is always optional.
                NotificationActions.ACTION_SKIP -> skip(occurrenceId)

                else -> Unit
            }
        }
    }

    private companion object {
        const val NO_ID = -1L
    }
}
