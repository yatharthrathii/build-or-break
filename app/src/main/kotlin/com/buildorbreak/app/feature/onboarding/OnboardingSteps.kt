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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.buildorbreak.app.R
import com.buildorbreak.core.designsystem.component.HairlineRule
import com.buildorbreak.core.designsystem.component.HeavyRule
import com.buildorbreak.core.designsystem.component.Kicker
import com.buildorbreak.core.designsystem.theme.Theme
import com.buildorbreak.core.designsystem.theme.TimeStyle
import java.util.Locale
import kotlinx.collections.immutable.ImmutableList

private const val STAGGER_MILLIS = 90
private const val ENTER_MILLIS = 380
private const val RISE_PX = 24

/** Wide enough for a twelve hour time with its marker, so titles stay in one column. */
private val TimeColumn = 74.dp

/**
 * What the app does, told as the morning it is for.
 *
 * The screen used to open with an abstract promise: your day, on rails. It was
 * true and it meant nothing to somebody who had just installed the thing. A
 * routine app is bought or abandoned on one specific morning, the one where
 * everything has already gone wrong, so that is the morning this screen is
 * about. The contrast is stated plainly because the difference between this
 * app and the twenty next to it in search results is genuinely one behaviour,
 * and nobody will find it by exploring.
 */
@Composable
internal fun WelcomeStep() {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Theme.spacing.medium)
            .padding(top = 26.dp, bottom = 8.dp),
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

        Rise(shown = shown, order = 2) { BeforeAndAfter() }

        Rise(shown = shown, order = 3) { ThreePoints() }
    }
}

/** The same morning, twice: how it normally goes, and how it goes here. */
@Composable
private fun BeforeAndAfter() {
    Column(modifier = Modifier.padding(top = 22.dp)) {
        Contrast(
            kicker = R.string.onboarding_before_kicker,
            body = R.string.onboarding_before_body,
            accent = false,
        )

        Contrast(
            kicker = R.string.onboarding_after_kicker,
            body = R.string.onboarding_after_body,
            accent = true,
        )
    }
}

@Composable
private fun ThreePoints() {
    Column(modifier = Modifier.padding(top = 24.dp)) {
        HeavyRule()
        Point(number = "01", title = R.string.onboarding_point_1_title, body = R.string.onboarding_point_1_body)
        Point(number = "02", title = R.string.onboarding_point_2_title, body = R.string.onboarding_point_2_body)
        Point(number = "03", title = R.string.onboarding_point_3_title, body = R.string.onboarding_point_3_body)
    }
}

/** One half of the before and after. The accent one is what this app does. */
@Composable
private fun Contrast(@StringRes kicker: Int, @StringRes body: Int, accent: Boolean) {
    val ground = if (accent) MaterialTheme.colorScheme.primaryContainer else Theme.colours.raised
    val ink = if (accent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .background(ground)
            .padding(14.dp),
    ) {
        Kicker(text = stringResource(kicker), color = ink)

        Text(
            text = stringResource(body),
            style = MaterialTheme.typography.bodyMedium,
            color = ink,
            modifier = Modifier.padding(top = 6.dp),
        )
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
 * What the user is trying to build, which is a question they can answer.
 *
 * The old screen asked how they wanted to start, which is a question about the
 * app rather than about them, and the recommended answer was a stranger's nine
 * step day. Naming the goal first means the routine that arrives on the next
 * screen is recognisably theirs, and the two ways in for people who already
 * have a routine stay where they were, at the bottom.
 */
@Composable
internal fun ChooseStep(choice: StartChoice, onChoose: (StartChoice) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Theme.spacing.medium)
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
        selected = choice == StartChoice.MORNING,
        kicker = R.string.onboarding_choice_morning_kicker,
        title = R.string.onboarding_choice_morning_title,
        body = R.string.onboarding_choice_morning_body,
        onClick = { onChoose(StartChoice.MORNING) },
    )

    ChoiceCard(
        selected = choice == StartChoice.STUDY,
        kicker = R.string.onboarding_choice_study_kicker,
        title = R.string.onboarding_choice_study_title,
        body = R.string.onboarding_choice_study_body,
        onClick = { onChoose(StartChoice.STUDY) },
    )

    ChoiceCard(
        selected = choice == StartChoice.FITNESS,
        kicker = R.string.onboarding_choice_fitness_kicker,
        title = R.string.onboarding_choice_fitness_title,
        body = R.string.onboarding_choice_fitness_body,
        onClick = { onChoose(StartChoice.FITNESS) },
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
            modifier = Modifier.padding(top = Theme.spacing.tight),
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
            .padding(horizontal = Theme.spacing.inset, vertical = Theme.spacing.small),
    )
}

/**
 * The day, before agreeing to anything.
 *
 * This screen is the one the first run was missing. Permissions used to be
 * asked for before the user had seen the app produce a single thing, which is
 * the moment people decline and then wonder why the alarms are quiet. Showing
 * the routine first turns the next screen from an interruption into a question
 * about something they can already see.
 *
 * Nothing is written yet. The rows come from running the real parser over the
 * starter text in memory, so what is shown here cannot differ from what gets
 * saved.
 */
@Composable
internal fun PreviewStep(state: OnboardingUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 22.dp, bottom = 12.dp),
    ) {
        Text(
            text = stringResource(previewTitle(state.choice)).uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = Theme.spacing.medium),
        )

        Text(
            text = stringResource(previewBody(state.choice)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Theme.spacing.medium).padding(top = 10.dp),
        )

        if (state.preview.isNotEmpty()) {
            Kicker(
                text = pluralStringResource(
                    R.plurals.onboarding_preview_count,
                    state.preview.size,
                    state.preview.size,
                    state.ringCount,
                ),
                modifier = Modifier.padding(horizontal = Theme.spacing.medium).padding(top = 18.dp, bottom = 10.dp),
            )

            HeavyRule()
            PreviewRows(rows = state.preview)
        }
    }
}

@Composable
private fun PreviewRows(rows: ImmutableList<PreviewRow>) {
    rows.forEachIndexed { index, row ->
        PreviewLine(row = row)

        if (index != rows.lastIndex) HairlineRule()
    }
}

@Composable
private fun PreviewLine(row: PreviewRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Theme.spacing.medium, vertical = Theme.spacing.inset),
    ) {
        Text(
            text = row.time,
            style = TimeStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(TimeColumn),
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            meta(row)?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/**
 * At most one fact per row.
 *
 * Ringing comes first because it is the only one with a consequence at six in
 * the morning, and a row that listed everything true about it would be a row
 * nobody read.
 */
@Composable
private fun meta(row: PreviewRow): String? {
    val kind = row.kind

    return when {
        row.rings -> stringResource(R.string.onboarding_preview_rings)
        // The offset is already in the time column, so this says what the
        // offset is measured from rather than repeating the number.
        kind is PreviewKind.After -> stringResource(R.string.onboarding_preview_after)
        kind is PreviewKind.Window -> stringResource(R.string.onboarding_preview_window, kind.until)
        row.pinned -> stringResource(R.string.onboarding_preview_pinned)
        kind is PreviewKind.Every -> stringResource(R.string.onboarding_preview_every)
        else -> null
    }
}

private fun previewTitle(choice: StartChoice): Int = when (choice) {
    StartChoice.PASTE -> R.string.onboarding_preview_paste_title
    StartChoice.WRITE -> R.string.onboarding_preview_write_title
    else -> R.string.onboarding_preview_title
}

private fun previewBody(choice: StartChoice): Int = when (choice) {
    StartChoice.PASTE -> R.string.onboarding_preview_paste_body
    StartChoice.WRITE -> R.string.onboarding_preview_write_body
    else -> R.string.onboarding_preview_body
}
