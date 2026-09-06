package com.buildorbreak.app.feature.onboarding

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.buildorbreak.app.R
import com.buildorbreak.core.designsystem.component.HairlineRule
import com.buildorbreak.core.designsystem.component.HeavyRule
import com.buildorbreak.core.designsystem.component.Kicker
import com.buildorbreak.core.designsystem.theme.Theme
import java.util.Locale

private const val STAGGER_MILLIS = 90
private const val ENTER_MILLIS = 380
private const val RISE_PX = 24

/**
 * What the app does, in three numbered lines.
 *
 * Numbered because they are read in order and each builds on the one before:
 * steps have times, times adapt, and all of it stays on the phone. The whole
 * screen rises into place on a short stagger, which is the one moment of
 * ceremony the first run allows itself.
 */
@Composable
internal fun WelcomeStep() {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 26.dp),
    ) {
        Rise(shown = shown, order = 0) {
            Text(
                text = stringResource(R.string.onboarding_welcome_title).uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Rise(shown = shown, order = 1) {
            Text(
                text = stringResource(R.string.onboarding_welcome_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 14.dp),
            )
        }

        Rise(shown = shown, order = 2) {
            Column(modifier = Modifier.padding(top = 26.dp)) {
                HeavyRule()
                Point(number = "01", title = R.string.onboarding_point_1_title, body = R.string.onboarding_point_1_body)
                Point(number = "02", title = R.string.onboarding_point_2_title, body = R.string.onboarding_point_2_body)
                Point(number = "03", title = R.string.onboarding_point_3_title, body = R.string.onboarding_point_3_body)
            }
        }
    }
}

@Composable
private fun Rise(shown: Boolean, order: Int, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(ENTER_MILLIS, delayMillis = order * STAGGER_MILLIS)) +
            slideInVertically(tween(ENTER_MILLIS, delayMillis = order * STAGGER_MILLIS)) { RISE_PX },
    ) {
        content()
    }
}

@Composable
private fun Point(number: String, @StringRes title: Int, @StringRes body: Int) {
    Row(modifier = Modifier.padding(vertical = 15.dp)) {
        Text(
            text = number,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 12.dp),
        )

        Column {
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = stringResource(body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }

    HairlineRule()
}

/**
 * Three ways in, one of them recommended.
 *
 * The sample is first and selected by default because it is the fastest way
 * to a screen that shows what the app does. Paste is for the person who came
 * here with a routine already written, and blank is for the one who wants to
 * type it.
 */
@Composable
internal fun ChooseStep(choice: StartChoice, onChoose: (StartChoice) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 22.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.onboarding_start_title).uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Text(
            text = stringResource(R.string.onboarding_start_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        ChoiceCards(choice = choice, onChoose = onChoose)
    }
}

@Composable
private fun ChoiceCards(choice: StartChoice, onChoose: (StartChoice) -> Unit) {
    ChoiceCard(
        selected = choice == StartChoice.SAMPLE,
        kicker = R.string.onboarding_choice_sample_kicker,
        title = R.string.onboarding_choice_sample_title,
        body = R.string.onboarding_choice_sample_body,
        onClick = { onChoose(StartChoice.SAMPLE) },
    )

    ChoiceCard(
        selected = choice == StartChoice.PASTE,
        kicker = R.string.onboarding_choice_paste_kicker,
        title = R.string.onboarding_choice_paste_title,
        body = R.string.onboarding_choice_paste_body,
        onClick = { onChoose(StartChoice.PASTE) },
    ) {
        PasteExample()
    }

    ChoiceCard(
        selected = choice == StartChoice.WRITE,
        kicker = R.string.onboarding_choice_write_kicker,
        title = R.string.onboarding_choice_write_title,
        body = R.string.onboarding_choice_write_body,
        onClick = { onChoose(StartChoice.WRITE) },
    )
}

@Composable
private fun ChoiceCard(
    selected: Boolean,
    @StringRes kicker: Int,
    @StringRes title: Int,
    @StringRes body: Int,
    onClick: () -> Unit,
    extra: @Composable () -> Unit = {},
) {
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val ground = if (selected) MaterialTheme.colorScheme.primaryContainer else Theme.colours.raised

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(Theme.spacing.rule, border)
            .background(ground)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Kicker(
                text = stringResource(kicker),
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f),
            )

            SelectionMark(selected = selected)
        }

        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp),
        )

        Text(
            text = stringResource(body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 5.dp),
        )

        extra()
    }
}

/** A filled square with a tick when chosen, a hollow one otherwise. */
@Composable
private fun SelectionMark(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .then(
                if (selected) {
                    Modifier.background(MaterialTheme.colorScheme.primary)
                } else {
                    Modifier.border(Theme.spacing.rule, Theme.colours.faint)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

/** Three lines in the format the parser reads, set in a monospace so it looks like data. */
@Composable
private fun PasteExample() {
    Text(
        text = stringResource(R.string.onboarding_paste_example),
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .background(MaterialTheme.colorScheme.surface)
            .border(Theme.spacing.hairline, Theme.colours.faint)
            .padding(horizontal = 10.dp, vertical = 9.dp),
    )
}
