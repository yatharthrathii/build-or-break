package com.buildorbreak.app.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buildorbreak.app.R
import com.buildorbreak.core.designsystem.component.BlockButton
import com.buildorbreak.core.designsystem.component.HeavyRule
import com.buildorbreak.core.designsystem.component.Kicker
import com.buildorbreak.core.designsystem.component.StepBars
import com.buildorbreak.core.designsystem.component.Wordmark
import com.buildorbreak.core.designsystem.theme.BuildOrBreakTheme
import com.buildorbreak.scheduler.alarm.TierBlocker

private const val SLIDE_FRACTION = 6
private const val FADE_MILLIS = 200

/**
 * The first run. Three screens and the app has a plan.
 *
 * What it does, how the plan gets in, and what the phone needs to allow. Each
 * one asks for one thing, and the third one explains every permission before
 * asking for it, because a permission dialog with no reason attached is one
 * most people decline.
 */
@Composable
fun OnboardingScreen(
    onRequestNotifications: () -> Unit,
    onFix: (TierBlocker) -> Unit,
    onOpenAutostart: () -> Unit,
    onFinished: (StartChoice) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // A system dialog closing is a resume, and a resume is when the answer to
    // "is this granted" can have changed.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermissions()
        onPauseOrDispose { }
    }

    BackHandler(enabled = !state.isFirst) { viewModel.onBack() }

    OnboardingContent(
        state = state,
        onNext = viewModel::onNext,
        onBack = viewModel::onBack,
        onChoose = viewModel::onChoose,
        onRequestNotifications = onRequestNotifications,
        onFix = onFix,
        onOpenAutostart = onOpenAutostart,
        onFinish = { viewModel.onFinish(onFinished) },
        modifier = modifier,
    )
}

@Composable
fun OnboardingContent(
    state: OnboardingUiState,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onChoose: (StartChoice) -> Unit,
    onRequestNotifications: () -> Unit,
    onFix: (TierBlocker) -> Unit,
    onOpenAutostart: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .systemBarsPadding(),
    ) {
        StepHeader(state = state, onBack = onBack)

        AnimatedContent(
            targetState = state.step,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                val forward = targetState > initialState
                val sign = if (forward) 1 else -1
                (slideInHorizontally(spring()) { sign * it / SLIDE_FRACTION } + fadeIn(tween(FADE_MILLIS)))
                    .togetherWith(
                        slideOutHorizontally(spring()) { -sign * it / SLIDE_FRACTION } + fadeOut(tween(FADE_MILLIS)),
                    )
            },
            label = "step",
        ) { step ->
            when (step) {
                0 -> WelcomeStep()
                1 -> ChooseStep(choice = state.choice, onChoose = onChoose)
                else -> PermissionsStep(
                    facts = state.permissions,
                    onRequestNotifications = onRequestNotifications,
                    onFix = onFix,
                    onOpenAutostart = onOpenAutostart,
                )
            }
        }

        StepFooter(state = state, onNext = onNext, onFinish = onFinish)
    }
}

/** The wordmark on the first screen, a back arrow and a step count after that. */
@Composable
private fun StepHeader(state: OnboardingUiState, onBack: () -> Unit) {
    Column {
        if (state.isFirst) {
            Wordmark(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
            )
        } else {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(role = Role.Button, onClick = onBack),
                )

                Kicker(
                    text = stringResource(R.string.onboarding_step_of, state.step + 1, ONBOARDING_STEPS),
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }

        HeavyRule()
    }
}

@Composable
private fun StepFooter(state: OnboardingUiState, onNext: () -> Unit, onFinish: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        if (state.failed) {
            Text(
                text = stringResource(R.string.onboarding_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        StepBars(total = ONBOARDING_STEPS, completed = state.step + 1, modifier = Modifier.padding(bottom = 14.dp))

        BlockButton(
            text = stringResource(footerLabel(state)),
            onClick = if (state.isLast) onFinish else onNext,
            enabled = !state.busy,
            icon = Icons.AutoMirrored.Outlined.ArrowForward,
        )
    }
}

private fun footerLabel(state: OnboardingUiState): Int = when (state.step) {
    0 -> R.string.onboarding_get_started
    1 -> when (state.choice) {
        StartChoice.SAMPLE -> R.string.onboarding_cta_sample
        StartChoice.PASTE -> R.string.onboarding_cta_paste
        StartChoice.WRITE -> R.string.onboarding_cta_write
    }

    else -> R.string.onboarding_start_day
}

// Previews ---------------------------------------------------------------------

@Preview(name = "Welcome", showBackground = true)
@Composable
private fun WelcomePreview() {
    BuildOrBreakTheme { OnboardingContent(OnboardingUiState.Start, {}, {}, {}, {}, {}, {}, {}) }
}

@Preview(name = "Choose", showBackground = true)
@Composable
private fun ChoosePreview() {
    BuildOrBreakTheme { OnboardingContent(OnboardingUiState.Start.copy(step = 1), {}, {}, {}, {}, {}, {}, {}) }
}

@Preview(name = "Permissions", showBackground = true)
@Composable
private fun PermissionsPreview() {
    BuildOrBreakTheme {
        OnboardingContent(
            OnboardingUiState.Start.copy(step = 2, permissions = PermissionFacts.Unknown.copy(autostart = true)),
            {},
            {},
            {},
            {},
            {},
            {},
            {},
        )
    }
}
