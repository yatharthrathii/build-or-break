package com.buildorbreak.app.feature.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.domain.export.BackupProblem
import com.buildorbreak.core.domain.repository.SettingsRepository
import com.buildorbreak.core.domain.usecase.ExportPlanUseCase
import com.buildorbreak.core.domain.usecase.RestoreBackupUseCase
import com.buildorbreak.core.domain.usecase.RestoreSummary
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
/**
 * How a restore ended, for the one line the screen says afterwards.
 *
 * A restore replaces everything, which is the sort of thing somebody wants
 * told back to them in their own numbers: eleven steps and forty days is a
 * sentence they can check against what they remember having.
 */
@Immutable
sealed interface RestoreResult {
    @Immutable
    data class Done(val summary: RestoreSummary) : RestoreResult

    @Immutable
    data class Failed(val problem: BackupProblem) : RestoreResult
}

@Immutable
data class SettingsUiState(
    val themeMode: ThemeMode,
    val tier: DeliveryTier,
    val fixCount: Int,
    /** Zero means every slip moves the relative steps. */
    val lateToleranceMinutes: Int,
    val exporting: Boolean,
    val wiping: Boolean,
    val restoring: Boolean = false,
    val restored: RestoreResult? = null,
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
    private val restoreBackup: RestoreBackupUseCase,
    private val wipeData: WipeDataUseCase,
    private val tiers: TierDetector,
) : ViewModel() {

    private val delivery = MutableStateFlow(tiers.detect())
    private val busy = MutableStateFlow(false to false)
    private val restore = MutableStateFlow(false to null as RestoreResult?)

    val state: StateFlow<SettingsUiState> = combine(
        settings.themeMode,
        settings.lateTolerance,
        delivery,
        busy,
        restore,
    ) { mode, tolerance, status, flags, restoreState ->
        SettingsUiState(
            themeMode = mode,
            tier = status.tier,
            fixCount = status.blockers.size,
            lateToleranceMinutes = tolerance.inWholeMinutes.toInt(),
            exporting = flags.first,
            wiping = flags.second,
            restoring = restoreState.first,
            restored = restoreState.second,
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
     * Puts a backup file back, replacing everything.
     *
     * The text is read by the screen rather than here: picking a file needs a
     * content resolver, which is a framework type, and a ViewModel that held
     * one would be a ViewModel that cannot be tested without Android. The same
     * split as the export, which hands its text out for somebody else to send.
     */
    fun onRestore(text: String) = viewModelScope.launch {
        restore.value = true to null

        restore.value = false to when (val outcome = restoreBackup(text)) {
            is Outcome.Success -> RestoreResult.Done(outcome.value)
            is Outcome.Failure -> RestoreResult.Failed(outcome.reason)
        }
    }

    /** The file could not even be opened, which the screen reports like any other bad file. */
    fun onRestoreUnreadable() {
        restore.value = false to RestoreResult.Failed(BackupProblem.NOT_READABLE)
    }

    fun onDismissRestore() {
        restore.value = false to null
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
