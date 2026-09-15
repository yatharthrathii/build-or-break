package com.buildorbreak.app.feature.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
) : ViewModel() {

    private val delivery = MutableStateFlow(tiers.detect())
    private val busy = MutableStateFlow(false to false)

    val state: StateFlow<SettingsUiState> = combine(
        settings.themeMode,
        settings.lateTolerance,
        delivery,
        busy,
    ) { mode, tolerance, status, flags ->
        SettingsUiState(
            themeMode = mode,
            tier = status.tier,
            fixCount = status.blockers.size,
            lateToleranceMinutes = tolerance.inWholeMinutes.toInt(),
            exporting = flags.first,
            wiping = flags.second,
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
