package com.buildorbreak.app.feature.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buildorbreak.core.domain.repository.SettingsRepository
import com.buildorbreak.core.model.enums.DeliveryTier
import com.buildorbreak.scheduler.alarm.TierBlocker
import com.buildorbreak.scheduler.alarm.TierDetector
import com.buildorbreak.scheduler.oem.OemGuide
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
    /**
     * Whether this phone still has to be told to let an alarm show on the lock
     * screen. The one that decides whether an alarm can be answered without
     * unlocking, and the one no API can read.
     */
    val needsLockScreen: Boolean = false,
) {
    companion object {
        val Unknown = ReliabilityUiState(
            tier = DeliveryTier.IN_APP_ONLY,
            blockers = persistentListOf(),
            needsAutostart = false,
            needsLockScreen = false,
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
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ReliabilityUiState.Unknown)
    val state: StateFlow<ReliabilityUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /**
     * Re reads now, and again a moment later.
     *
     * Granting a permission and the system recording it are not the same
     * instant. Battery optimisation is the clear case: the dialog closes, this
     * screen resumes, and the answer changes a second or two afterwards, which
     * the user sees as a screen that did not notice what they just did. Two
     * cheap re reads cover it without polling forever.
     */
    fun refresh() = viewModelScope.launch {
        read()

        RECHECKS.forEach { wait ->
            delay(wait)
            read()
        }
    }

    /** The user's word that the autostart list is dealt with. Nothing can verify it. */
    fun onAutostartDone() = viewModelScope.launch {
        settings.setAutostartDone(true)
        read()
    }

    fun onLockScreenDone() = viewModelScope.launch {
        settings.setLockScreenDone(true)
        read()
    }

    private suspend fun read() {
        val status = tiers.detect()

        _state.value = ReliabilityUiState(
            tier = status.tier,
            blockers = status.topBlockers().toImmutableList(),
            needsAutostart = guide.needsAutostartGuidance() && !settings.autostartDone.first(),
            needsLockScreen = guide.needsLockScreenGuidance() && !settings.lockScreenDone.first(),
        )
    }

    private companion object {
        val RECHECKS = listOf(1200.milliseconds, 3000.milliseconds)
    }
}
