package com.buildorbreak.app.feature.track

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.domain.track.TrackTextParser
import com.buildorbreak.core.domain.usecase.DeleteTrackUseCase
import com.buildorbreak.core.domain.usecase.ObserveTracksUseCase
import com.buildorbreak.core.domain.usecase.SaveTrackUseCase
import com.buildorbreak.core.domain.usecase.SetTrackUnitStateUseCase
import com.buildorbreak.core.domain.usecase.TrackHead
import com.buildorbreak.core.model.enums.TrackUnitState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MILLIS = 5_000L

/** One syllabus on the list: its name and where it stands. */
@Immutable
data class TrackRowUi(
    val id: Long,
    val name: String,
    /** One based. Zero once every part is dealt with. */
    val position: Int,
    val total: Int,
    val finished: Int,
    val nextTitle: String?,
) {
    val isFinished: Boolean get() = nextTitle == null && total > 0
}

@Immutable
data class TracksUiState(val tracks: ImmutableList<TrackRowUi>, val failed: Boolean = false) {
    companion object {
        val Empty = TracksUiState(tracks = persistentListOf())
    }
}

/** Every syllabus on the plan, and the way to start one. */
@HiltViewModel
class TracksViewModel @Inject constructor(
    observeTracks: ObserveTracksUseCase,
    private val saveTrack: SaveTrackUseCase,
    private val parser: TrackTextParser,
) : ViewModel() {

    private val failed = MutableStateFlow(false)

    val state: StateFlow<TracksUiState> = combine(observeTracks(), failed) { heads, failure ->
        TracksUiState(tracks = heads.map(::toRow).toImmutableList(), failed = failure)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TracksUiState.Empty)

    /** How many parts the text would become, for the line under the editor. */
    fun partsIn(text: String): Int = parser.parse(text).size

    fun onCreate(name: String, text: String) = viewModelScope.launch {
        failed.value = saveTrack(trackId = null, name = name, text = text) is Outcome.Failure
    }

    private fun toRow(head: TrackHead) = TrackRowUi(
        id = head.track.id,
        name = head.track.name,
        position = head.position,
        total = head.total,
        finished = head.finished,
        nextTitle = head.next?.title,
    )
}

/** One part of a syllabus, as the list draws it. */
@Immutable
data class UnitUi(
    val id: Long,
    /** One based, so the row says "3" for the third part. */
    val number: Int,
    val title: String,
    val estimateMinutes: Int?,
    val state: TrackUnitState,
    val isNext: Boolean,
)

@Immutable
data class TrackUiState(
    val id: Long,
    val name: String,
    /** The text as pasted, for the editor to open on. */
    val text: String,
    val units: ImmutableList<UnitUi>,
    val position: Int,
    val total: Int,
    val finished: Int,
    val minutesSpent: Int,
    /** Where the last sitting on the next part stopped, when it said. */
    val leftOff: String?,
    /** True until the first read, and again once the syllabus is gone. */
    val isMissing: Boolean = false,
    val failed: Boolean = false,
) {
    val isFinished: Boolean get() = total > 0 && position == 0

    companion object {
        val Loading = TrackUiState(
            id = 0,
            name = "",
            text = "",
            units = persistentListOf(),
            position = 0,
            total = 0,
            finished = 0,
            minutesSpent = 0,
            leftOff = null,
        )
    }
}

/** One syllabus: its parts, where it stands, and the ways to change either. */
@HiltViewModel
class TrackViewModel @Inject constructor(
    private val observeTracks: ObserveTracksUseCase,
    private val saveTrack: SaveTrackUseCase,
    private val setState: SetTrackUnitStateUseCase,
    private val deleteTrack: DeleteTrackUseCase,
    private val parser: TrackTextParser,
) : ViewModel() {

    /** Null until the screen says which one. Loading, not missing, until then. */
    private val trackId = MutableStateFlow<Long?>(null)
    private val failed = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<TrackUiState> = trackId
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(TrackUiState.Loading)
            } else {
                observeTracks.one(id).map { head -> head?.let(::toState) ?: missing(id) }
            }
        }
        .combine(failed) { state, failure -> state.copy(failed = failure) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TrackUiState.Loading)

    fun load(id: Long) {
        trackId.value = id
    }

    fun partsIn(text: String): Int = parser.parse(text).size

    fun onSave(name: String, text: String) = viewModelScope.launch {
        val id = trackId.value ?: return@launch

        failed.value = saveTrack(trackId = id, name = name, text = text) is Outcome.Failure
    }

    fun onSetState(unitId: Long, state: TrackUnitState) = viewModelScope.launch {
        failed.value = setState(unitId, state) is Outcome.Failure
    }

    fun onDelete(onDone: () -> Unit) = viewModelScope.launch {
        val id = trackId.value ?: return@launch

        if (deleteTrack(id) is Outcome.Success) onDone() else failed.value = true
    }

    private fun missing(id: Long) = TrackUiState.Loading.copy(id = id, isMissing = true)

    private fun toState(head: TrackHead) = TrackUiState(
        id = head.track.id,
        name = head.track.name,
        text = head.track.sourceText ?: head.units.joinToString("\n") { it.title },
        units = head.units.mapIndexed { index, unit ->
            UnitUi(
                id = unit.id,
                number = index + 1,
                title = unit.title,
                estimateMinutes = unit.estimateMinutes,
                state = unit.state,
                isNext = unit.id == head.next?.id,
            )
        }.toImmutableList(),
        position = head.position,
        total = head.total,
        finished = head.finished,
        minutesSpent = head.minutesSpent,
        leftOff = head.leftOff,
    )
}
