package com.buildorbreak.app.feature.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buildorbreak.core.data.demo.DemoHistory
import com.buildorbreak.core.domain.repository.SettingsRepository
import com.buildorbreak.core.domain.usecase.ExportPlanUseCase
import com.buildorbreak.core.domain.usecase.WipeDataUseCase
import com.buildorbreak.core.model.enums.DeliveryTier
import com.buildorbreak.core.model.enums.ThemeMode
import com.buildorbreak.scheduler.alarm.TierDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Facts for the settings rows.
 *
 * [fixCount] is how many things the reliability screen would offer to change.
 * Zero means the row can say so and nobody has to open it.
 */
@Immutable
data class SettingsUiState(
    val themeMode: ThemeMode,
    val tier: DeliveryTier,
    val fixCount: Int,
    /** Zero means every slip moves the relative steps. */
    val lateToleranceMinutes: Int,
    val exporting: Boolean,
    val wiping: Boolean,
    /** Temporary. See `DemoHistory`; both go together. */
    val demoBusy: Boolean = false,
    val demoMessage: String? = null,
) {
    companion object {
        val Initial = SettingsUiState(
            themeMode = ThemeMode.SYSTEM,
            tier = DeliveryTier.IN_APP_ONLY,
            fixCount = 0,
            lateToleranceMinutes = 0,
            exporting = false,
            wiping = false,
        )
    }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val exportPlan: ExportPlanUseCase,
    private val wipeData: WipeDataUseCase,
    private val tiers: TierDetector,
    private val demoHistory: DemoHistory,
) : ViewModel() {

    private val delivery = MutableStateFlow(tiers.detect())
    private val busy = MutableStateFlow(false to false)

    /** Temporary, with `DemoHistory`. Busy, and how many rows the last run wrote. */
    private val demo = MutableStateFlow<Pair<Boolean, Int?>>(false to null)

    val state: StateFlow<SettingsUiState> = combine(
        settings.themeMode,
        settings.lateTolerance,
        delivery,
        busy,
        demo,
    ) { mode, tolerance, status, flags, demoState ->
        SettingsUiState(
            themeMode = mode,
            tier = status.tier,
            fixCount = status.blockers.size,
            lateToleranceMinutes = tolerance.inWholeMinutes.toInt(),
            exporting = flags.first,
            wiping = flags.second,
            demoBusy = demoState.first,
            demoMessage = demoState.second?.toString(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = SettingsUiState.Initial,
    )

    /** Re read on resume, for the same reason the reliability screen does. */
    fun refresh() {
        delivery.value = tiers.detect()
    }

    fun onThemeMode(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }

    fun onLateTolerance(minutes: Int) = viewModelScope.launch { settings.setLateTolerance(minutes.minutes) }

    /** Builds the file and hands the text to whoever shares it. Nothing when there is no plan. */
    fun onExport(onReady: (String) -> Unit) = viewModelScope.launch {
        busy.value = true to busy.value.second
        exportPlan()?.let(onReady)
        busy.value = false to busy.value.second
    }

    /**
     * Temporary, and deliberately obvious.
     *
     * Six weeks of invented history so the review screens can be looked at
     * before six weeks have passed. `DemoHistory` says the rest; deleting that
     * file and these two functions removes the feature.
     */
    fun onSeedDemo() = viewModelScope.launch {
        demo.value = true to null
        val written = demoHistory.seed()
        demo.value = false to written
    }

    fun onClearDemo() = viewModelScope.launch {
        demo.value = true to null
        demoHistory.clear()
        demo.value = false to 0
    }

    fun onWipe(onDone: () -> Unit) = viewModelScope.launch {
        busy.value = busy.value.first to true
        wipeData()
        busy.value = busy.value.first to false
        onDone()
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
