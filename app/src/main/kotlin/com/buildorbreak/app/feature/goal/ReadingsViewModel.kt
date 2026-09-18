package com.buildorbreak.app.feature.goal

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.common.time.TimeProvider
import com.buildorbreak.core.domain.usecase.AddReadingUseCase
import com.buildorbreak.core.domain.usecase.DeleteReadingUseCase
import com.buildorbreak.core.domain.usecase.GoalSeries
import com.buildorbreak.core.domain.usecase.ObserveGoalReadingsUseCase
import com.buildorbreak.core.domain.usecase.SaveReadingUseCase
import com.buildorbreak.core.model.enums.ValueKind
import com.buildorbreak.core.model.execution.Measurement
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One recorded number, ready to draw. */
@Immutable
data class ReadingRow(
    val id: Long,
    val date: LocalDate,
    val value: Double,
    /** Today's, which is the one most likely to be a typo somebody just made. */
    val isToday: Boolean,
)

/**
 * The reading being written, and what has been typed into it so far.
 *
 * One shape for both jobs. Correcting a row and adding a missing one ask the
 * same question of the user, and the only difference is whether the day is
 * already decided, so the editor is the same editor with its date unlocked.
 */
@Immutable
data class ReadingDraft(
    val id: Long,
    val date: LocalDate,
    val typed: String,
    /** Adding rather than correcting: the day can still be moved. */
    val isNew: Boolean = false,
    /** True when the chosen day already has a number this would replace. */
    val replaces: Boolean = false,
) {
    val value: Double? get() = typed.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }

    val canSave: Boolean get() = value != null

    /** Nothing to remove until there is a row. */
    val canDelete: Boolean get() = !isNew || replaces
}

@Immutable
data class ReadingsUiState(
    val loaded: Boolean,
    val title: String,
    val valueKind: ValueKind,
    val rows: ImmutableList<ReadingRow>,
    val editing: ReadingDraft? = null,
    val failed: Boolean = false,
    /** Only a measured goal takes readings, so only one can be added by hand. */
    val canAdd: Boolean = false,
) {
    companion object {
        val Empty = ReadingsUiState(
            loaded = false,
            title = "",
            valueKind = ValueKind.NONE,
            rows = persistentListOf(),
        )
    }
}

/**
 * Every number behind a measured goal, and the two things you can do to one.
 *
 * The list exists because the goal card shows a seven day average, and an
 * average nobody can see the inputs of is a number that has to be taken on
 * faith. Somebody who weighed themselves before and after a meal, or typed
 * 720 for 72, has no way to explain what the card is showing them until they
 * can see the readings it was built from.
 *
 * Correcting or removing one rebuilds the goal's stored history from that day
 * on. That is the use case's job, not this one's; all that happens here is
 * that the change is asked for and the failure is reported.
 */
@HiltViewModel
class ReadingsViewModel @Inject constructor(
    observeReadings: ObserveGoalReadingsUseCase,
    private val addReading: AddReadingUseCase,
    private val saveReading: SaveReadingUseCase,
    private val deleteReading: DeleteReadingUseCase,
    private val time: TimeProvider,
) : ViewModel() {

    private val draft = MutableStateFlow<ReadingDraft?>(null)
    private val failed = MutableStateFlow(false)

    /** Kept so an edit can be written back as the row it came from, not as a new one. */
    private var series: List<Measurement> = emptyList()

    /** The earliest day a reading may be added for. Before the goal it would not count. */
    private var firstDay: LocalDate? = null

    val state: StateFlow<ReadingsUiState> =
        combine(observeReadings(), draft, failed) { found, editing, failure ->
            series = found?.readings.orEmpty()
            firstDay = found?.goal?.startDate

            ReadingsUiState(
                loaded = true,
                title = found?.goal?.title.orEmpty(),
                valueKind = found?.goal?.valueKind ?: ValueKind.NONE,
                rows = rowsOf(found),
                editing = editing,
                failed = failure,
                canAdd = found != null,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ReadingsUiState.Empty,
        )

    /**
     * Opens the editor on a day with no number, today by default.
     *
     * Today because that is the reading somebody came here to add: the step
     * was ticked from a notification hours ago and the figure has nowhere to
     * go. The arrows are there for the morning it is remembered a day late.
     */
    fun onAdd() {
        draft.value = draftFor(time.today())
        failed.value = false
    }

    /** Moves the day being added by [days], inside the goal's own window. */
    fun onShiftDay(days: Long) {
        val current = draft.value?.takeIf { it.isNew } ?: return
        val moved = current.date.plusDays(days)

        // A reading before the goal began is averaged into nothing, and one
        // dated tomorrow is a number that has not been taken yet.
        if (moved > time.today() || moved < (firstDay ?: moved)) return

        draft.value = draftFor(moved)
        failed.value = false
    }

    fun onEdit(id: Long) {
        val row = series.firstOrNull { it.id == id } ?: return

        draft.value = ReadingDraft(id = row.id, date = row.date, typed = trimmed(row.value))
        failed.value = false
    }

    fun onTyped(text: String) {
        draft.value = draft.value?.copy(typed = text)
        failed.value = false
    }

    fun onCancel() {
        draft.value = null
    }

    fun onSave() = viewModelScope.launch {
        val current = draft.value ?: return@launch
        val value = current.value ?: return@launch

        val written = if (current.isNew) {
            addReading(current.date, value) is Outcome.Success
        } else {
            val row = series.firstOrNull { it.id == current.id } ?: return@launch
            saveReading(row, value) is Outcome.Success
        }

        if (written) draft.value = null
        failed.value = !written
    }

    fun onDelete() = viewModelScope.launch {
        val current = draft.value ?: return@launch
        val id = if (current.isNew) series.firstOrNull { it.date == current.date }?.id else current.id
        val row = series.firstOrNull { it.id == id } ?: return@launch

        val removed = deleteReading(row) is Outcome.Success

        if (removed) draft.value = null
        failed.value = !removed
    }

    /** A day, with whatever is already written against it put in the box. */
    private fun draftFor(date: LocalDate): ReadingDraft {
        val existing = series.firstOrNull { it.date == date }

        return ReadingDraft(
            id = existing?.id ?: 0L,
            date = date,
            typed = existing?.let { trimmed(it.value) }.orEmpty(),
            isNew = true,
            replaces = existing != null,
        )
    }

    private fun rowsOf(found: GoalSeries?): ImmutableList<ReadingRow> {
        val today = time.today()

        return found?.readings.orEmpty()
            .map { ReadingRow(id = it.id, date = it.date, value = it.value, isToday = it.date == today) }
            .toImmutableList()
    }

    /** 72.0 goes into the box as "72". Nobody wants to delete a decimal to fix a typo. */
    private fun trimmed(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
