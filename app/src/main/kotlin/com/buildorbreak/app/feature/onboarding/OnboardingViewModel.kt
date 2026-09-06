package com.buildorbreak.app.feature.onboarding

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buildorbreak.app.sample.SampleRoutine
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.domain.parse.PlanTextParser
import com.buildorbreak.core.domain.repository.SettingsRepository
import com.buildorbreak.core.domain.usecase.CreatePlanUseCase
import com.buildorbreak.core.domain.usecase.ImportPlanUseCase
import com.buildorbreak.core.model.enums.DeliveryTier
import com.buildorbreak.scheduler.alarm.TierBlocker
import com.buildorbreak.scheduler.alarm.TierDetector
import com.buildorbreak.scheduler.oem.OemGuide
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The three screens of the first run, in order. */
const val ONBOARDING_STEPS = 3

/** How a plan gets into the app on the first run. */
enum class StartChoice {
    /** Nine steps, ready to edit. The recommended path. */
    SAMPLE,

    /** Paste text from an AI chat. Fastest if the routine already exists. */
    PASTE,

    /** A blank plan and the editor. */
    WRITE,
}

/** Whether one permission is in place. Facts only; the wording is the screen's. */
enum class PermissionState { GRANTED, NEEDED }

/**
 * What the phone will let the app do right now.
 *
 * [autostart] is a fact about the phone rather than about a permission: some
 * vendors keep a list no API can read, and the row is shown on those phones
 * without claiming to know whether it has been done.
 */
@Immutable
data class PermissionFacts(
    val notifications: PermissionState,
    val exactAlarms: PermissionState,
    val battery: PermissionState,
    val autostart: Boolean,
    val tier: DeliveryTier,
) {
    companion object {
        val Unknown = PermissionFacts(
            notifications = PermissionState.NEEDED,
            exactAlarms = PermissionState.NEEDED,
            battery = PermissionState.NEEDED,
            autostart = false,
            tier = DeliveryTier.IN_APP_ONLY,
        )
    }
}

@Immutable
data class OnboardingUiState(
    val step: Int,
    val choice: StartChoice,
    val permissions: PermissionFacts,
    val busy: Boolean,
    val failed: Boolean,
) {
    val isFirst: Boolean get() = step == 0
    val isLast: Boolean get() = step == ONBOARDING_STEPS - 1

    companion object {
        val Start = OnboardingUiState(
            step = 0,
            choice = StartChoice.SAMPLE,
            permissions = PermissionFacts.Unknown,
            busy = false,
            failed = false,
        )
    }
}

/**
 * Three screens, one decision, and the permissions the decision needs.
 *
 * The plan is written on the last tap, not when the choice is made. Somebody
 * who picks the sample, reads the permission screen and closes the app has
 * not started a routine, and finding nine steps on Today the next morning that
 * they never agreed to would be a poor first day.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val importPlan: ImportPlanUseCase,
    private val createPlan: CreatePlanUseCase,
    private val parser: PlanTextParser,
    private val sample: SampleRoutine,
    private val tiers: TierDetector,
    private val guide: OemGuide,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState.Start)
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        refreshPermissions()
    }

    fun onNext() = _state.update { it.copy(step = (it.step + 1).coerceAtMost(ONBOARDING_STEPS - 1)) }

    fun onBack() = _state.update { it.copy(step = (it.step - 1).coerceAtLeast(0)) }

    fun onChoose(choice: StartChoice) = _state.update { it.copy(choice = choice) }

    /**
     * Re read on every resume. Android has no callback for a permission being
     * granted from a system dialog, and coming back from one is a resume.
     */
    fun refreshPermissions() {
        val status = tiers.detect()

        _state.update {
            it.copy(
                permissions = PermissionFacts(
                    notifications = stateOf(TierBlocker.NOTIFICATIONS_DENIED !in status.blockers),
                    exactAlarms = stateOf(TierBlocker.EXACT_ALARMS_DENIED !in status.blockers),
                    battery = stateOf(TierBlocker.BATTERY_OPTIMISED !in status.blockers),
                    autostart = guide.needsAutostartGuidance(),
                    tier = status.tier,
                ),
            )
        }
    }

    /** Writes the plan the choice implies, then hands the choice back to navigation. */
    fun onFinish(onFinished: (StartChoice) -> Unit) = viewModelScope.launch {
        val choice = _state.value.choice
        _state.update { it.copy(busy = true, failed = false) }

        val written = when (choice) {
            StartChoice.SAMPLE -> importPlan(
                parsed = parser.parse(sample.text).items,
                templateName = sample.templateName,
                planName = sample.planName,
            ) is Outcome.Success

            StartChoice.WRITE -> createPlan(sample.planName, sample.templateName) is Outcome.Success

            StartChoice.PASTE -> true
        }

        if (written) {
            settings.setOnboardingComplete(true)
            onFinished(choice)
        }

        _state.update { it.copy(busy = false, failed = !written) }
    }

    private fun stateOf(granted: Boolean) = if (granted) PermissionState.GRANTED else PermissionState.NEEDED
}
