package com.buildorbreak.app.feature.onboarding

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buildorbreak.app.format.ClockFormat
import com.buildorbreak.app.sample.Starter
import com.buildorbreak.app.sample.Starters
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.domain.parse.PlanTextParser
import com.buildorbreak.core.domain.repository.SettingsRepository
import com.buildorbreak.core.domain.usecase.CreatePlanUseCase
import com.buildorbreak.core.domain.usecase.ImportPlanUseCase
import com.buildorbreak.core.model.enums.DeliveryTier
import com.buildorbreak.core.model.enums.Salience
import com.buildorbreak.core.model.plan.Anchor
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The four screens of the first run, in order.
 *
 * Four rather than three, and the new one is the whole point. The old flow
 * asked which sample to load and then went straight to a permissions wall, so
 * the first thing a new user was asked to trust the app with came before they
 * had seen the app do anything. Now they see the day they are agreeing to
 * first, and the permissions screen is a question about something real.
 */
const val ONBOARDING_STEPS = 4

/** How a plan gets into the app on the first run. */
enum class StartChoice {
    /** Wake, stretch, breakfast, wind down. The commonest thing people want. */
    MORNING,

    /** Two deep blocks, revision and practice, each with a smaller version. */
    STUDY,

    /** A pinned gym slot, food around it, a walk, a fixed lights out. */
    FITNESS,

    /** Paste text from an AI chat. Fastest if the routine already exists. */
    PASTE,

    /** A blank plan and the editor. */
    WRITE,
    ;

    /** Whether this choice arrives with a day already written. */
    val hasRoutine: Boolean get() = this == MORNING || this == STUDY || this == FITNESS
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

/** What kind of time a previewed step keeps. The screen turns this into words. */
@Immutable
sealed interface PreviewKind {
    data object Fixed : PreviewKind

    data class After(val minutes: Int) : PreviewKind

    data class Window(val until: String) : PreviewKind

    data object Every : PreviewKind
}

/** One line of the day the user is about to agree to. */
@Immutable
data class PreviewRow(
    val time: String,
    val title: String,
    val kind: PreviewKind,
    val rings: Boolean,
    val pinned: Boolean,
)

@Immutable
data class OnboardingUiState(
    val step: Int,
    val choice: StartChoice,
    val preview: ImmutableList<PreviewRow>,
    val permissions: PermissionFacts,
    val busy: Boolean,
    val failed: Boolean,
) {
    val isFirst: Boolean get() = step == 0
    val isLast: Boolean get() = step == ONBOARDING_STEPS - 1

    /** How many of the previewed steps will make a noise. Shown, so nobody is surprised. */
    val ringCount: Int get() = preview.count { it.rings }

    companion object {
        val Start = OnboardingUiState(
            step = 0,
            choice = StartChoice.MORNING,
            preview = persistentListOf(),
            permissions = PermissionFacts.Unknown,
            busy = false,
            failed = false,
        )
    }
}

/**
 * Four screens, one decision, and the permissions the decision needs.
 *
 * The plan is written on the last tap, not when the choice is made. Somebody
 * who picks a starter, reads the permission screen and closes the app has not
 * started a routine, and finding nine steps on Today the next morning that
 * they never agreed to would be a poor first day. The preview screen is
 * therefore built by running the parser in memory: the same reader a real
 * import goes through, against the same text, writing nothing.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val importPlan: ImportPlanUseCase,
    private val createPlan: CreatePlanUseCase,
    private val parser: PlanTextParser,
    private val starters: Starters,
    private val clock: ClockFormat,
    private val tiers: TierDetector,
    private val guide: OemGuide,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState.Start)
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        refreshPermissions()
        _state.update { it.copy(preview = previewOf(it.choice)) }
    }

    fun onNext() = _state.update { it.copy(step = (it.step + 1).coerceAtMost(ONBOARDING_STEPS - 1)) }

    fun onBack() = _state.update { it.copy(step = (it.step - 1).coerceAtLeast(0)) }

    fun onChoose(choice: StartChoice) = _state.update { it.copy(choice = choice, preview = previewOf(choice)) }

    /**
     * Re read on every resume. Android has no callback for a permission being
     * granted from a system dialog, and coming back from one is a resume.
     */
    fun refreshPermissions() = viewModelScope.launch {
        readPermissions()

        // Granting and the system recording it are not the same instant, and
        // battery optimisation in particular lands a second or two after the
        // dialog closes. Two cheap re reads cover that without polling.
        RECHECKS.forEach { wait ->
            delay(wait)
            readPermissions()
        }
    }

    /** The user's word that the autostart list is dealt with. Nothing can verify it. */
    fun onAutostartDone() = viewModelScope.launch {
        settings.setAutostartDone(true)
        readPermissions()
    }

    private suspend fun readPermissions() {
        val status = tiers.detect()
        val autostartDone = settings.autostartDone.first()

        _state.update {
            it.copy(
                permissions = PermissionFacts(
                    notifications = stateOf(TierBlocker.NOTIFICATIONS_DENIED !in status.blockers),
                    exactAlarms = stateOf(TierBlocker.EXACT_ALARMS_DENIED !in status.blockers),
                    battery = stateOf(TierBlocker.BATTERY_OPTIMISED !in status.blockers),
                    autostart = guide.needsAutostartGuidance() && !autostartDone,
                    tier = status.tier,
                ),
            )
        }
    }

    /** Writes the plan the choice implies, then hands the choice back to navigation. */
    fun onFinish(onFinished: (StartChoice) -> Unit) = viewModelScope.launch {
        val choice = _state.value.choice
        _state.update { it.copy(busy = true, failed = false) }

        val written = when {
            choice.hasRoutine -> starterFor(choice).let { starter ->
                importPlan(
                    parsed = parser.parse(starter.text).items,
                    templateName = starter.templateName,
                    planName = starter.planName,
                ) is Outcome.Success
            }

            choice == StartChoice.WRITE ->
                createPlan(starters.blank.planName, starters.blank.templateName) is Outcome.Success

            // The import screen writes its own plan once the text is in.
            else -> true
        }

        if (written) {
            settings.setOnboardingComplete(true)
            onFinished(choice)
        }

        _state.update { it.copy(busy = false, failed = !written) }
    }

    private fun starterFor(choice: StartChoice): Starter = when (choice) {
        StartChoice.MORNING -> starters.morning
        StartChoice.STUDY -> starters.study
        StartChoice.FITNESS -> starters.fitness
        StartChoice.PASTE, StartChoice.WRITE -> starters.blank
    }

    /**
     * The chosen day, read but not written.
     *
     * Through the real parser rather than from a second hand list, so the
     * preview cannot show a day the import would not produce. A starter whose
     * preview and result disagreed would be worse than no preview at all.
     */
    private fun previewOf(choice: StartChoice): ImmutableList<PreviewRow> {
        if (!choice.hasRoutine) return persistentListOf()

        return parser.parse(starterFor(choice).text).items
            .map { parsed ->
                PreviewRow(
                    time = timeOf(parsed.anchor),
                    title = parsed.title,
                    kind = kindOf(parsed.anchor),
                    rings = parsed.salience == Salience.ALARM,
                    pinned = parsed.pinned,
                )
            }
            .toImmutableList()
    }

    /**
     * Where the row sits in the left hand column.
     *
     * A relative step has no clock time of its own, and leaving the column
     * empty read as a missing value rather than as a different kind of time.
     * It gets its offset instead, in the same shape the import format uses.
     */
    private fun timeOf(anchor: Anchor): String = when (anchor) {
        is Anchor.Fixed -> clock.format(anchor.at)
        is Anchor.Window -> clock.format(anchor.from)
        is Anchor.Interval -> clock.format(anchor.from)
        is Anchor.Relative -> "+" + anchor.offset.inWholeMinutes + "m"
    }

    private fun kindOf(anchor: Anchor): PreviewKind = when (anchor) {
        is Anchor.Fixed -> PreviewKind.Fixed
        is Anchor.Relative -> PreviewKind.After(anchor.offset.inWholeMinutes.toInt())
        is Anchor.Window -> PreviewKind.Window(clock.format(anchor.to))
        is Anchor.Interval -> PreviewKind.Every
    }

    private fun stateOf(granted: Boolean) = if (granted) PermissionState.GRANTED else PermissionState.NEEDED

    private companion object {
        /** When to look again after a system dialog closes. */
        val RECHECKS = listOf(1200.milliseconds, 3000.milliseconds)
    }
}
