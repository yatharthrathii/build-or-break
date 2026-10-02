package com.buildorbreak.app.feature.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.buildorbreak.app.R
import com.buildorbreak.app.feature.plan.TextBox
import com.buildorbreak.core.designsystem.component.FillButton
import com.buildorbreak.core.designsystem.component.GhostButton
import com.buildorbreak.core.designsystem.component.HairlineRule
import com.buildorbreak.core.designsystem.component.Kicker
import com.buildorbreak.core.designsystem.component.OutlineButton
import com.buildorbreak.core.designsystem.component.SquareToggle
import com.buildorbreak.core.designsystem.component.Stepper
import com.buildorbreak.core.designsystem.theme.Theme
import java.util.Locale

private const val MINUTES_STEP = 5
private const val MAX_MINUTES = 600

/**
 * The syllabus line on the card: which part this sitting is, and where the
 * last one stopped.
 *
 * The left off note is the whole point. It is shown at the one moment it is
 * worth anything, when the step has arrived and somebody is about to sit
 * down, which is when "page 42, the JWT example" saves the five minutes of
 * working out where page 42 was.
 */
@Composable
internal fun TrackLine(line: TrackLineUi, onOpen: (Long) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onOpen(line.trackId) }
            .padding(horizontal = 12.dp)
            .padding(top = Theme.spacing.small),
    ) {
        Kicker(
            text = if (line.isFinished) {
                stringResource(R.string.today_track_finished, line.name)
            } else {
                stringResource(R.string.today_track_part, line.position, line.total, line.name)
            },
            color = MaterialTheme.colorScheme.primary,
        )

        line.unitTitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = Theme.spacing.tight),
            )
        }

        line.leftOff?.let {
            Text(
                text = stringResource(R.string.today_track_left_off, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Theme.spacing.tight),
            )
        }
    }
}

/** The link the step carries, as one button with the site or the file on it. */
@Composable
internal fun LinkRow(link: String, onOpen: (String) -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 12.dp).padding(top = Theme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.small),
    ) {
        OutlineButton(
            text = stringResource(if (link.isFileLink()) R.string.today_open_file else R.string.today_open_link),
            onClick = { onOpen(link) },
        )

        Text(
            text = rememberLinkLabel(link),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * What the sitting had to say about itself.
 *
 * Opens after the settle, never before it, like the number question. Two
 * facts: whether the part is finished, and where it stopped if not. The
 * step is already done by the time this appears, and it says so.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SessionSheet(prompt: SessionPrompt, onLog: (Boolean, Int, String) -> Unit, onDismiss: () -> Unit) {
    var finished by rememberSaveable { mutableStateOf(true) }
    var leftOff by rememberSaveable { mutableStateOf("") }
    var minutes by rememberSaveable { mutableStateOf(prompt.minutes) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier.padding(
                start = Theme.spacing.medium,
                end = Theme.spacing.medium,
                top = 22.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SessionHeading(prompt = prompt)

            FinishedRow(finished = finished, onChange = { finished = it })

            TextBox(
                label = stringResource(R.string.today_session_left_off),
                value = leftOff,
                onValueChange = { leftOff = it },
                placeholder = stringResource(R.string.today_session_left_off_hint),
                help = stringResource(R.string.today_session_left_off_body),
            )

            MinutesRow(minutes = minutes, onChange = { minutes = it })

            SessionActions(onSave = { onLog(finished, minutes, leftOff) }, onDismiss = onDismiss)
        }
    }
}

@Composable
private fun SessionHeading(prompt: SessionPrompt) {
    Kicker(
        text = stringResource(R.string.today_session_kicker, prompt.position, prompt.total),
        color = MaterialTheme.colorScheme.primary,
    )

    Text(
        text = prompt.unitTitle.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** Save, and the way past it. Skipping the question never skips the step. */
@Composable
private fun SessionActions(onSave: () -> Unit, onDismiss: () -> Unit) {
    Text(
        text = stringResource(R.string.today_session_body),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        GhostButton(text = stringResource(R.string.today_number_skip), onClick = onDismiss)
        FillButton(text = stringResource(R.string.today_session_save), onClick = onSave)
    }
}

@Composable
private fun FinishedRow(finished: Boolean, onChange: (Boolean) -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.today_session_finished),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    text = stringResource(
                        if (finished) R.string.today_session_finished_on else R.string.today_session_finished_off,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Theme.spacing.tight),
                )
            }

            SquareToggle(
                checked = finished,
                onCheckedChange = onChange,
                modifier = Modifier.padding(start = Theme.spacing.medium),
            )
        }

        HairlineRule(Modifier.padding(top = Theme.spacing.inset))
    }
}

@Composable
private fun MinutesRow(minutes: Int, onChange: (Int) -> Unit) {
    Column {
        Kicker(text = stringResource(R.string.today_session_minutes))

        Stepper(
            decrementLabel = stringResource(R.string.stepper_less),
            incrementLabel = stringResource(R.string.stepper_more),
            value = stringResource(R.string.editor_minutes_value, minutes),
            onDecrement = { onChange((minutes - MINUTES_STEP).coerceAtLeast(0)) },
            onIncrement = { onChange((minutes + MINUTES_STEP).coerceAtMost(MAX_MINUTES)) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}
