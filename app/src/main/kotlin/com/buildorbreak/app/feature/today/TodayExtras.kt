package com.buildorbreak.app.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.buildorbreak.app.R
import com.buildorbreak.app.feature.plan.valueKindLabel
import com.buildorbreak.app.feature.plan.valueKindUnit
import com.buildorbreak.core.designsystem.component.FillButton
import com.buildorbreak.core.designsystem.component.GhostButton
import com.buildorbreak.core.designsystem.component.HairlineRule
import com.buildorbreak.core.designsystem.component.Kicker
import com.buildorbreak.core.designsystem.component.Panel
import com.buildorbreak.core.designsystem.theme.Theme
import com.buildorbreak.core.designsystem.theme.TimeStyle
import com.buildorbreak.core.model.enums.Milestone
import java.util.Locale

/**
 * What is still possible today, once the day has slipped.
 *
 * The honest version of a catch up feature. Most apps either say nothing when
 * a day slips or roll everything forward until the list is absurd, and both
 * teach the user that the list is fiction. This one names the slot each thing
 * could still take, offers the smaller version where only that fits, and says
 * plainly what will not fit at all.
 */
@Composable
internal fun CatchUpPanelView(panel: CatchUpPanel, onMove: (Long, Int) -> Unit, onSkip: (Long) -> Unit) {
    // Late enough and nothing fits any more. The panel still has something
    // worth saying, which is what the day close is about to settle to missed,
    // but it must stop calling it possible: a heading that promises a list
    // and then shows none is the app arguing with itself.
    val possible = panel.steps.isNotEmpty()

    Column(modifier = Modifier.padding(horizontal = Theme.spacing.medium, vertical = 8.dp)) {
        Panel {
            Column {
                Kicker(
                    text = stringResource(if (possible) R.string.today_catch_up else R.string.today_catch_up_gone),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = Theme.spacing.inset),
                )

                if (possible) {
                    Text(
                        text = stringResource(R.string.today_catch_up_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 10.dp),
                    )
                }

                panel.steps.forEach { step ->
                    HairlineRule()
                    CatchUpStepRow(step = step, onMove = onMove, onSkip = onSkip)
                }

                LeftOvers(panel = panel)
            }
        }
    }
}

@Composable
private fun CatchUpStepRow(step: CatchUpRow, onMove: (Long, Int) -> Unit, onSkip: (Long) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = step.time, style = TimeStyle, color = MaterialTheme.colorScheme.onSurface)

        Column(modifier = Modifier.weight(1f).padding(start = Theme.spacing.inset)) {
            Text(
                text = step.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = if (step.useMinimum) {
                    stringResource(R.string.today_catch_up_smaller, step.minutes)
                } else {
                    stringResource(R.string.today_catch_up_takes, step.minutes)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        GhostButton(text = stringResource(R.string.today_catch_up_skip), onClick = { onSkip(step.occurrenceId) })
        GhostButton(
            text = stringResource(R.string.today_catch_up_move),
            onClick = { onMove(step.occurrenceId, step.moveByMinutes) },
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * What was missed and is not on the list above.
 *
 * Two lines, because they are two different pieces of news: one is final and
 * one is only a limit on what the app is willing to propose.
 */
@Composable
private fun LeftOvers(panel: CatchUpPanel) {
    if (panel.alsoMissed.isNotEmpty()) {
        HairlineRule()
        LeftOverLine(
            text = pluralStringResource(
                R.plurals.today_catch_up_also,
                panel.alsoMissed.size,
                panel.alsoMissed.joinToString(", "),
            ),
        )
    }

    if (panel.outOfTime.isNotEmpty()) {
        HairlineRule()
        LeftOverLine(text = stringResource(R.string.today_catch_up_no_room, panel.outOfTime.joinToString(", ")))
    }
}

/** Named, not counted. Two things nobody can name is a number to go and decode. */
@Composable
private fun LeftOverLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

/**
 * Something earned, said once.
 *
 * Milestones are already rationed hard in the domain: at most one a day, never
 * twice from the same category, and never at all on a day that went badly. All
 * this does is say the one that survived that and then close it for good.
 */
@Composable
internal fun MilestoneBanner(notice: MilestoneNotice, onSeen: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onPrimary

    Column(
        modifier = Modifier
            .padding(horizontal = Theme.spacing.medium, vertical = 8.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Kicker(text = stringResource(R.string.today_milestone), color = ink)

            Text(
                text = stringResource(milestoneTitle(notice.milestone)),
                style = MaterialTheme.typography.headlineSmall,
                color = ink,
                modifier = Modifier.padding(top = 8.dp),
            )

            Text(
                text = stringResource(milestoneBody(notice.milestone)),
                style = MaterialTheme.typography.bodySmall,
                color = ink,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        Box(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
            Text(
                text = stringResource(R.string.today_milestone_seen).uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelMedium,
                color = ink,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clickable(role = Role.Button, onClick = onSeen)
                    .padding(start = Theme.spacing.inset, top = 4.dp, bottom = Theme.spacing.inset),
            )
        }
    }
}

/**
 * The number a completed step asked for.
 *
 * Opens after the settle, never before it, and closes either way. The step is
 * already done by the time this appears: making the number a condition of
 * ticking something off is how the numbers stop arriving at all.
 *
 * A sheet rather than a dialog, and for the same reason `SkipSheet` is one:
 * it is a question about something that has already happened, not a form
 * standing between somebody and their day.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MeasureSheet(prompt: MeasurePrompt, onLog: (Double) -> Unit, onDismiss: () -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    // "NaN" and "Infinity" are doubles as far as the parser is concerned, and
    // a NaN reading poisons every average it is ever part of.
    val value = typed.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }

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
            Text(
                text = prompt.title.uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            NumberField(
                label = stringResource(valueKindLabel(prompt.kind)),
                unit = stringResource(valueKindUnit(prompt.kind)),
                value = typed,
                onValueChange = { typed = it },
            )

            Text(
                text = stringResource(R.string.today_number_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            MeasureActions(value = value, onLog = onLog, onDismiss = onDismiss)
        }
    }
}

/** Save, and the way past it. Skipping the number never skips the step. */
@Composable
private fun MeasureActions(value: Double?, onLog: (Double) -> Unit, onDismiss: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        GhostButton(text = stringResource(R.string.today_number_skip), onClick = onDismiss)
        FillButton(
            text = stringResource(R.string.today_number_save),
            onClick = { value?.let(onLog) },
            enabled = value != null,
        )
    }
}

@Composable
private fun NumberField(
    label: String,
    unit: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Column {
        Kicker(text = label)

        Row(verticalAlignment = Alignment.CenterVertically) {
            NumberInput(value = value, onValueChange = onValueChange, modifier = Modifier.weight(1f))

            if (unit.isNotEmpty()) {
                Text(
                    text = unit,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Theme.spacing.inset, top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun NumberInput(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = MaterialTheme.typography.displaySmall.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier
            .padding(top = 6.dp)
            .border(Theme.spacing.rule, MaterialTheme.colorScheme.onSurface)
            .background(Theme.colours.raised)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(text = "0", style = MaterialTheme.typography.displaySmall, color = Theme.colours.faint)
                }
                inner()
            }
        },
    )
}

internal fun milestoneTitle(milestone: Milestone): Int = when (milestone) {
    Milestone.FIRST_COMPLETION -> R.string.milestone_first_completion
    Milestone.FIRST_FULL_DAY -> R.string.milestone_first_full_day
    Milestone.FIRST_WEEK -> R.string.milestone_first_week
    Milestone.GOAL_QUARTER -> R.string.milestone_goal_quarter
    Milestone.GOAL_HALF -> R.string.milestone_goal_half
    Milestone.GOAL_THREE_QUARTERS -> R.string.milestone_goal_three_quarters
    Milestone.GOAL_REACHED -> R.string.milestone_goal_reached
    Milestone.BEST_WEEK -> R.string.milestone_best_week
    Milestone.ITEM_THIRTY_DAY_RUN -> R.string.milestone_thirty_day_run
}

private fun milestoneBody(milestone: Milestone): Int = when (milestone) {
    Milestone.FIRST_COMPLETION -> R.string.milestone_first_completion_body
    Milestone.FIRST_FULL_DAY -> R.string.milestone_first_full_day_body
    Milestone.FIRST_WEEK -> R.string.milestone_first_week_body
    Milestone.GOAL_QUARTER, Milestone.GOAL_HALF, Milestone.GOAL_THREE_QUARTERS ->
        R.string.milestone_goal_body

    Milestone.GOAL_REACHED -> R.string.milestone_goal_reached_body
    Milestone.BEST_WEEK -> R.string.milestone_best_week_body
    Milestone.ITEM_THIRTY_DAY_RUN -> R.string.milestone_thirty_day_run_body
}
