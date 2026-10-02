package com.buildorbreak.widget

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Immutable
import com.buildorbreak.core.domain.usecase.CompleteItemUseCase
import com.buildorbreak.core.domain.usecase.ObserveTodayUseCase
import com.buildorbreak.core.domain.usecase.SnoozeItemUseCase
import com.buildorbreak.core.model.enums.Salience
import com.buildorbreak.core.model.resolved.ResolvedDay
import com.buildorbreak.core.model.resolved.ResolvedEntry
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
    /** The one glyph the strip has room for in place of "Done". */
    val tick: String,
    /**
     * The whole day, in order, for the tall widget.
     *
     * Every step, settled or not, so the list reads as the day and not as a
     * to do list that shrinks. The next one is marked, because the tall face
     * has no card above it to say which row is live.
     */
    val rows: List<WidgetRow> = emptyList(),
)

/** One row of the day on the tall widget. */
@Immutable
internal data class WidgetRow(val time: String, val title: String, val settled: Boolean, val isNext: Boolean)

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
        // The same rule as the card on Today: a timeline note has no row and
        // no Done button, so it is never the thing to do next.
        val next = day?.entries?.firstOrNull { it.occurrence?.isSettled != true && it.salience != Salience.TIMELINE }
        val done = day?.doneCount ?: 0
        val total = day?.entries?.count { it.salience != Salience.TIMELINE } ?: 0
        val clock = clock(context)

        return WidgetSnapshot(
            kicker = context.getString(R.string.widget_kicker),
            count = context.getString(R.string.widget_count, done, total),
            time = next?.at?.let { clock.format(it) },
            title = next?.let(::titleOf),
            occurrenceId = next?.occurrence?.id ?: NO_ID,
            emptyLine = context.getString(if (day == null) R.string.widget_no_plan else R.string.widget_all_done),
            doneLabel = context.getString(R.string.widget_done),
            snoozeLabel = context.getString(R.string.widget_snooze),
            tick = context.getString(R.string.widget_tick),
            rows = day?.entries.orEmpty().map { entry ->
                WidgetRow(
                    time = clock.format(entry.at),
                    title = titleOf(entry),
                    settled = entry.occurrence?.isSettled == true,
                    isNext = entry === next,
                )
            },
        )
    }

    /** The smaller version's title on a sick day, the step's own otherwise. */
    private fun titleOf(entry: ResolvedEntry): String =
        entry.item.minimum?.title.takeIf { entry.reduced } ?: entry.item.title

    /** The phone's own twelve or twenty four hour setting, like everywhere else in the app. */
    private fun clock(context: Context): DateTimeFormatter = if (DateFormat.is24HourFormat(context)) {
        DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    } else {
        DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    }

    /** No occurrence yet: the step is on the plan but its row has not been written. */
    const val NO_ID = 0L
}
