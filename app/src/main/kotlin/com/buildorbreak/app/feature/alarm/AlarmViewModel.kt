package com.buildorbreak.app.feature.alarm

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buildorbreak.app.format.ClockFormat
import com.buildorbreak.core.common.time.TimeProvider
import com.buildorbreak.core.domain.repository.ItemRepository
import com.buildorbreak.core.domain.repository.OccurrenceRepository
import com.buildorbreak.core.domain.usecase.CompleteItemUseCase
import com.buildorbreak.core.domain.usecase.PreviewSnoozeUseCase
import com.buildorbreak.core.domain.usecase.SkipItemUseCase
import com.buildorbreak.core.domain.usecase.SnoozeItemUseCase
import com.buildorbreak.core.model.plan.Anchor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The one snooze the screen offers. Matches the notification. */
private val SNOOZE = 10.minutes

/**
 * Everything the alarm screen shows.
 *
 * [movedBySnooze] is how many later steps a snooze would push, worked out by
 * resolving the day twice. Null while it is being worked out or when there is
 * nothing to move, and the screen says nothing in either case.
 */
@Immutable
data class AlarmUiState(
    val occurrenceId: Long,
    val time: String,
    val title: String,
    val kindLabel: AlarmKind,
    val durationMinutes: Int?,
    val hasMinimum: Boolean,
    val movedBySnooze: Int?,
    /** The row was already dealt with, or never existed. The screen closes. */
    val gone: Boolean,
) {
    companion object {
        val Loading = AlarmUiState(
            occurrenceId = 0,
            time = "",
            title = "",
            kindLabel = AlarmKind.FIXED,
            durationMinutes = null,
            hasMinimum = false,
            movedBySnooze = null,
            gone = false,
        )
    }
}

enum class AlarmKind { FIXED, RELATIVE, WINDOW, REPEAT }

/**
 * Reads the two rows the alarm is about, then waits for one tap.
 *
 * Every action goes through the same use case the notification buttons use,
 * so completing from this screen and completing from the shade are the same
 * operation. The screen closes as soon as the write is through.
 */
@HiltViewModel
class AlarmViewModel @Inject constructor(
    private val occurrences: OccurrenceRepository,
    private val items: ItemRepository,
    private val previewSnooze: PreviewSnoozeUseCase,
    private val complete: CompleteItemUseCase,
    private val snooze: SnoozeItemUseCase,
    private val skip: SkipItemUseCase,
    private val time: TimeProvider,
    private val clock: ClockFormat,
) : ViewModel() {

    private val _state = MutableStateFlow(AlarmUiState.Loading)
    val state: StateFlow<AlarmUiState> = _state.asStateFlow()

    fun load(occurrenceId: Long, itemId: Long) = viewModelScope.launch {
        val row = occurrences.observeForDate(time.today()).first().firstOrNull { it.id == occurrenceId }
        val item = items.byId(itemId)

        if (row == null || item == null || row.isSettled) {
            _state.update { it.copy(gone = true) }
            return@launch
        }

        _state.value = AlarmUiState(
            occurrenceId = row.id,
            time = clock.format(row.effectiveAt),
            title = item.title,
            kindLabel = kindOf(item.anchor),
            durationMinutes = item.duration?.inWholeMinutes?.toInt(),
            hasMinimum = item.hasMinimum,
            movedBySnooze = null,
            gone = false,
        )

        // Worked out after the screen is up, so a slow preview never delays
        // the one thing the screen exists for.
        val moved = previewSnooze(occurrenceId, SNOOZE)?.moved?.size?.minus(1)?.takeIf { it > 0 }
        _state.update { it.copy(movedBySnooze = moved) }
    }

    fun onDone(onClosed: () -> Unit) = act(onClosed) { complete(it) }

    fun onDoneMinimum(onClosed: () -> Unit) = act(onClosed) { complete(it, minimum = true) }

    fun onSnooze(onClosed: () -> Unit) = act(onClosed) { snooze(it, SNOOZE) }

    fun onSkip(onClosed: () -> Unit) = act(onClosed) { skip(it) }

    private fun act(onClosed: () -> Unit, action: suspend (Long) -> Unit) = viewModelScope.launch {
        val id = _state.value.occurrenceId
        if (id > 0) action(id)
        onClosed()
    }

    private fun kindOf(anchor: Anchor): AlarmKind = when (anchor) {
        is Anchor.Fixed -> AlarmKind.FIXED
        is Anchor.Relative -> AlarmKind.RELATIVE
        is Anchor.Window -> AlarmKind.WINDOW
        is Anchor.Interval -> AlarmKind.REPEAT
    }
}
