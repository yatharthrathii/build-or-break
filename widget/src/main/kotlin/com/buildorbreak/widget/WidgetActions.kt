package com.buildorbreak.widget

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import kotlin.time.Duration.Companion.minutes

/** How long the widget's snooze moves a step, matching the notification's. */
private const val SNOOZE_MINUTES = 10

/**
 * The two things the widget can do, and the app they do it through.
 *
 * Both go to the same use case the notification buttons call. A widget that
 * wrote to the database itself would be a third place that has to remember to
 * cancel the alarm, reschedule the rest of the day and refresh the widget, and
 * the third place is always the one that forgets.
 */
internal object WidgetActions {

    val occurrenceId = ActionParameters.Key<Long>("occurrence_id")

    fun done(id: Long): Action = actionRunCallback<DoneAction>(actionParametersOf(occurrenceId to id))

    fun snooze(id: Long): Action = actionRunCallback<SnoozeAction>(actionParametersOf(occurrenceId to id))
}

internal class DoneAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[WidgetActions.occurrenceId] ?: return
        if (id == WidgetData.NO_ID) return

        WidgetData.dependencies(context).complete().invoke(id)
        TodayWidget().update(context, glanceId)
    }
}

internal class SnoozeAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[WidgetActions.occurrenceId] ?: return
        if (id == WidgetData.NO_ID) return

        WidgetData.dependencies(context).snooze().invoke(id, SNOOZE_MINUTES.minutes)
        TodayWidget().update(context, glanceId)
    }
}

/**
 * Opens the app on the day.
 *
 * By main launcher intent rather than by class, because this module cannot see
 * the activity and should not be given a reason to.
 */
internal class OpenAppAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return

        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
