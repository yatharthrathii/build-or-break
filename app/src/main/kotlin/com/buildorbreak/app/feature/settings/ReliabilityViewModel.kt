package com.buildorbreak.app.feature.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import com.buildorbreak.core.model.enums.DeliveryTier
import com.buildorbreak.scheduler.alarm.TierBlocker
import com.buildorbreak.scheduler.alarm.TierDetector
import com.buildorbreak.scheduler.oem.OemGuide
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the app can promise, and what would change that.
 *
 * Facts only. Not a single sentence of copy lives in this file: rules.md section
 * 9 keeps every user visible word in a string resource so that adding Hindi and
 * a Hinglish variant later is a translation job rather than a rewrite of the one
 * screen whose whole purpose is explaining something.
 */
@Immutable
data class ReliabilityUiState(
    val tier: DeliveryTier,
    /** At most two. A list of six settings gets closed; two get done. */
    val blockers: ImmutableList<TierBlocker>,
    val needsAutostart: Boolean,
) {
    companion object {
        val Unknown = ReliabilityUiState(
            tier = DeliveryTier.IN_APP_ONLY,
            blockers = persistentListOf(),
            needsAutostart = false,
        )
    }
}

/**
 * Reads the capabilities fresh every time it is asked.
 *
 * Nothing is cached and nothing is observed, because there is nothing to observe:
 * Android has no callback for a permission being revoked from the shade. The
 * screen re reads on every resume instead, which covers the one case that
 * matters anyway, somebody going to settings and coming back.
 *
 * No `Intent` crosses this class. architecture.md keeps framework types out of a
 * ViewModel, so the screen reports which blocker was tapped and the activity
 * turns that into a screen to open.
 */
@HiltViewModel
class ReliabilityViewModel @Inject constructor(
    private val tiers: TierDetector,
    private val guide: OemGuide,
) : ViewModel() {

    private val _state = MutableStateFlow(ReliabilityUiState.Unknown)
    val state: StateFlow<ReliabilityUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val status = tiers.detect()

        _state.value = ReliabilityUiState(
            tier = status.tier,
            blockers = status.topBlockers().toImmutableList(),
            needsAutostart = guide.needsAutostartGuidance(),
        )
    }
}
