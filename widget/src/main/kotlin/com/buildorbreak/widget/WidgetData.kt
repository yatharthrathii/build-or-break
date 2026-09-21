package com.buildorbreak.widget

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Immutable
import com.buildorbreak.core.domain.usecase.CompleteItemUseCase
import com.buildorbreak.core.domain.usecase.ObserveTodayUseCase
import com.buildorbreak.core.domain.usecase.SnoozeItemUseCase
import com.buildorbreak.core.model.resolved.ResolvedDay
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.first

/**
 * Everything the widget draws, worked out before it draws anything.
 *
 * Facts, already formatted. The same rule the ViewModels follow, and for a
 * sharper reason here: a Glance composable runs inside a RemoteViews render
 * pass, and doing work there means doing it again on every launcher redraw.
 */
@Immutable
internal data class WidgetSnapshot(
    val kicker: String,
    val count: String,
    val time: String?,
    val title: String?,
    val occurrenceId: Long,
    val emptyLine: String,
    val doneLabel: String,
    val snoozeLabel: String,
)

/**
 * Reads the day for the widget.
 *
 * A widget is not an activity and has no ViewModel, so it reaches the use case
 * layer through a Hilt entry point. That is the same layer the notification
 * buttons call, which is what keeps a Done from the home screen and a Done from
 * the lock screen doing exactly the same thing.
 */
internal object WidgetData {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun observeToday(): ObserveTodayUseCase

        fun complete(): CompleteItemUseCase

        fun snooze(): SnoozeItemUseCase
    }

    fun dependencies(context: Context): Dependencies =
        EntryPointAccessors.fromApplication(context.applicationContext, Dependencies::class.java)

    suspend fun read(context: Context): WidgetSnapshot =
        snapshotOf(context, dependencies(context).observeToday().invoke().first())

    /**
     * The day, as the widget says it.
     *
     * Apart from [read] so it can be tested without Hilt or a database. What
     * the widget picks as next, and what it says when there is nothing, are
     * the only decisions this module makes, and they were the only part of
     * the app with no test at all.
     */
    fun snapshotOf(context: Context, day: ResolvedDay?): WidgetSnapshot {
        val next = day?.entries?.firstOrNull { it.occurrence?.isSettled != true }
        val done = day?.doneCount ?: 0
        val total = day?.total ?: 0

        return WidgetSnapshot(
            kicker = context.getString(R.string.widget_kicker),
            count = context.getString(R.string.widget_count, done, total),
            time = next?.at?.let { clock(context).format(it) },
            title = next?.let { entry -> entry.item.minimum?.title.takeIf { entry.reduced } ?: entry.item.title },
            occurrenceId = next?.occurrence?.id ?: NO_ID,
            emptyLine = context.getString(if (day == null) R.string.widget_no_plan else R.string.widget_all_done),
            doneLabel = context.getString(R.string.widget_done),
            snoozeLabel = context.getString(R.string.widget_snooze),
        )
    }

    /** The phone's own twelve or twenty four hour setting, like everywhere else in the app. */
    private fun clock(context: Context): DateTimeFormatter = if (DateFormat.is24HourFormat(context)) {
        DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    } else {
        DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    }

    /** No occurrence yet: the step is on the plan but its row has not been written. */
    const val NO_ID = 0L
}
