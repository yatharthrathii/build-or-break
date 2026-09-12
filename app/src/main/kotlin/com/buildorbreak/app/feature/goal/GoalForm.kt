package com.buildorbreak.app.feature.goal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.buildorbreak.app.R
import com.buildorbreak.app.feature.plan.valueKindLabel
import com.buildorbreak.core.designsystem.component.HairlineRule
import com.buildorbreak.core.designsystem.component.Kicker
import com.buildorbreak.core.designsystem.component.Panel
import com.buildorbreak.core.designsystem.component.PickerField
import com.buildorbreak.core.designsystem.component.SegmentedTabs
import com.buildorbreak.core.designsystem.component.Stepper
import com.buildorbreak.core.designsystem.theme.Theme
import com.buildorbreak.core.model.enums.GoalKind
import com.buildorbreak.core.model.enums.ValueKind

private const val MIN_WEEKS = 1
private const val MAX_WEEKS = 52

/** A measured goal reads a series, and only these kinds are a series worth reading. */
private val MEASURED_KINDS = listOf(ValueKind.WEIGHT_KG, ValueKind.REPS, ValueKind.PAGES, ValueKind.MINUTES)

/**
 * The form for writing a goal.
 *
 * The kind is the first choice and everything below it changes to suit,
 * because the four kinds are four genuinely different questions: a weight is
 * a level to reach, a count is a tally to fill, a duration is hours to
 * accumulate and a consistency goal is a rate to hold. Presenting them as one
 * form with a hidden switch would make three of the four confusing.
 *
 * The length is in weeks rather than as a target date. "In two months" is a
 * real thought; "by the 14th of March" is arithmetic somebody has to do
 * first, and doing arithmetic is not the mood in which anybody sets a goal.
 */
@Composable
internal fun GoalForm(draft: GoalDraft, items: List<GoalItemChoice>, onChange: (GoalDraft) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Theme.spacing.medium)
            .padding(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        GoalTextBox(
            label = stringResource(R.string.goal_form_title),
            value = draft.title,
            onValueChange = { onChange(draft.copy(title = it)) },
            placeholder = stringResource(R.string.goal_form_title_hint),
        )

        KindSection(draft = draft, onChange = onChange)

        if (draft.needsItem) {
            ItemSection(draft = draft, items = items, onChange = onChange)
        }

        if (draft.kind == GoalKind.NUMBER) {
            MeasuredSection(draft = draft, onChange = onChange)
        }

        TargetSection(draft = draft, onChange = onChange)

        LengthSection(draft = draft, onChange = onChange)
    }
}

@Composable
private fun KindSection(draft: GoalDraft, onChange: (GoalDraft) -> Unit) {
    Column {
        Kicker(text = stringResource(R.string.goal_form_kind))

        SegmentedTabs(
            options = GoalKind.entries.map { stringResource(goalKindLabel(it)) },
            selectedIndex = GoalKind.entries.indexOf(draft.kind),
            onSelect = { onChange(draft.copy(kind = GoalKind.entries[it])) },
            accent = true,
            stretch = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )

        Text(
            text = stringResource(goalKindHint(draft.kind)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Theme.spacing.small),
        )
    }
}

@Composable
private fun ItemSection(draft: GoalDraft, items: List<GoalItemChoice>, onChange: (GoalDraft) -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    val chosen = items.firstOrNull { it.id == draft.itemId }

    DropField(
        label = stringResource(R.string.goal_form_step),
        value = chosen?.let(::labelFor) ?: stringResource(R.string.goal_form_step_none),
        body = stringResource(R.string.goal_form_step_body),
        onClick = { choosing = true },
    )

    if (choosing) {
        PickDialog(title = stringResource(R.string.goal_form_step), onDismiss = { choosing = false }) {
            items.forEach { item ->
                PickRow(text = labelFor(item), chosen = item.id == draft.itemId) {
                    onChange(draft.copy(itemId = item.id))
                    choosing = false
                }
            }
        }
    }
}

/** "Gym" on a one template plan, "Gym · Weekend" once there are two. */
private fun labelFor(item: GoalItemChoice): String =
    if (item.templateName.isBlank()) item.title else item.title + " · " + item.templateName

@Composable
private fun MeasuredSection(draft: GoalDraft, onChange: (GoalDraft) -> Unit) {
    var choosing by remember { mutableStateOf(false) }

    DropField(
        label = stringResource(R.string.goal_form_measures),
        value = stringResource(valueKindLabel(draft.valueKind)),
        body = stringResource(R.string.goal_form_measures_body),
        onClick = { choosing = true },
    )

    GoalTextBox(
        label = stringResource(R.string.goal_form_start),
        value = draft.startValue,
        onValueChange = { onChange(draft.copy(startValue = it)) },
        placeholder = stringResource(R.string.goal_form_start_hint),
        numeric = true,
        modifier = Modifier.padding(top = 18.dp),
    )

    if (choosing) {
        MeasureKindDialog(
            chosen = draft.valueKind,
            onPick = {
                onChange(draft.copy(valueKind = it))
                choosing = false
            },
            onDismiss = { choosing = false },
        )
    }
}

@Composable
private fun MeasureKindDialog(chosen: ValueKind, onPick: (ValueKind) -> Unit, onDismiss: () -> Unit) {
    PickDialog(title = stringResource(R.string.goal_form_measures), onDismiss = onDismiss) {
        MEASURED_KINDS.forEach { kind ->
            PickRow(text = stringResource(valueKindLabel(kind)), chosen = kind == chosen) { onPick(kind) }
        }
    }
}

@Composable
private fun TargetSection(draft: GoalDraft, onChange: (GoalDraft) -> Unit) {
    GoalTextBox(
        label = stringResource(goalTargetLabel(draft.kind)),
        value = draft.targetValue,
        onValueChange = { onChange(draft.copy(targetValue = it)) },
        placeholder = stringResource(R.string.goal_form_target_hint),
        numeric = true,
    )
}

@Composable
private fun LengthSection(draft: GoalDraft, onChange: (GoalDraft) -> Unit) {
    Column {
        Kicker(text = stringResource(R.string.goal_form_length))

        Stepper(
            value = pluralStringResource(R.plurals.goal_form_weeks, draft.weeks, draft.weeks),
            onDecrement = { onChange(draft.copy(weeks = (draft.weeks - 1).coerceAtLeast(MIN_WEEKS))) },
            onIncrement = { onChange(draft.copy(weeks = (draft.weeks + 1).coerceAtMost(MAX_WEEKS))) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )

        Text(
            text = stringResource(R.string.goal_form_length_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Theme.spacing.small),
        )
    }
}

/** A picker with the sentence that explains it, which every one here has. */
@Composable
private fun DropField(
    label: String,
    value: String,
    body: String,
    onClick: () -> Unit,
) {
    Column {
        PickerField(label = label, value = value, onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp),
            )
        }

        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Theme.spacing.small),
        )
    }
}

@Composable
private fun PickDialog(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Panel {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Kicker(text = title, modifier = Modifier.padding(horizontal = Theme.spacing.medium, vertical = 10.dp))
                content()
            }
        }
    }
}

@Composable
private fun PickRow(text: String, chosen: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Theme.spacing.medium, vertical = Theme.spacing.inset),
    )

    HairlineRule(Modifier.padding(horizontal = Theme.spacing.medium))
}

/** The same bordered box the plan editor uses, with a number keyboard when asked. */
@Composable
private fun GoalTextBox(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    numeric: Boolean = false,
) {
    Column(modifier = modifier) {
        Kicker(text = label)

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.titleSmall.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .border(Theme.spacing.rule, MaterialTheme.colorScheme.onSurface)
                .background(Theme.colours.raised)
                .padding(horizontal = Theme.spacing.inset, vertical = Theme.spacing.inset),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.titleSmall,
                            color = Theme.colours.faint,
                        )
                    }
                    inner()
                }
            },
        )
    }
}

private fun goalKindLabel(kind: GoalKind): Int = when (kind) {
    GoalKind.NUMBER -> R.string.goal_kind_number
    GoalKind.COUNT -> R.string.goal_kind_count
    GoalKind.DURATION -> R.string.goal_kind_duration
    GoalKind.CONSISTENCY -> R.string.goal_kind_consistency
}

private fun goalKindHint(kind: GoalKind): Int = when (kind) {
    GoalKind.NUMBER -> R.string.goal_kind_number_hint
    GoalKind.COUNT -> R.string.goal_kind_count_hint
    GoalKind.DURATION -> R.string.goal_kind_duration_hint
    GoalKind.CONSISTENCY -> R.string.goal_kind_consistency_hint
}

private fun goalTargetLabel(kind: GoalKind): Int = when (kind) {
    GoalKind.NUMBER -> R.string.goal_form_target_value
    GoalKind.COUNT -> R.string.goal_form_target_times
    GoalKind.DURATION -> R.string.goal_form_target_minutes
    GoalKind.CONSISTENCY -> R.string.goal_form_target_percent
}
