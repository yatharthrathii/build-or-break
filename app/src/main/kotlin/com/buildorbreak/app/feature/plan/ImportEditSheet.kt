package com.buildorbreak.app.feature.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.buildorbreak.app.R
import com.buildorbreak.core.designsystem.component.BlockButton
import com.buildorbreak.core.designsystem.component.Kicker
import com.buildorbreak.core.designsystem.component.OutlineButton
import com.buildorbreak.core.designsystem.component.Stepper
import com.buildorbreak.core.model.plan.Anchor
import java.time.LocalTime
import java.util.Locale
import kotlin.time.Duration.Companion.minutes

private const val STEP_MINUTES = 5
private const val MAX_MINUTES = 240
private const val MIN_EVERY = 5

/**
 * One parsed line, corrected by hand before it is saved.
 *
 * The parser reads most lines right and a few wrong, and the wrong ones are
 * nearly always the title or the time. So that is what this edits: the title,
 * and the fields the anchor has. Changing the kind of anchor is the step
 * editor's job once the plan exists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImportEditSheet(
    kicker: String,
    title: String,
    anchor: Anchor,
    onSave: (String, Anchor) -> Unit,
    onRemove: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(title) }
    var draft by remember { mutableStateOf(anchor) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 28.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = kicker.uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            TextBox(label = stringResource(R.string.editor_title_label), value = name, onValueChange = { name = it })

            AnchorFields(anchor = draft, onChange = { draft = it })

            BlockButton(
                text = stringResource(R.string.import_edit_save),
                onClick = { onSave(name.trim(), draft) },
                enabled = name.isNotBlank() && draft.isSane(),
            )

            onRemove?.let { OutlineButton(text = stringResource(R.string.import_edit_remove), onClick = it) }
        }
    }
}

/** The fields this kind of anchor has, and nothing else. */
@Composable
private fun AnchorFields(anchor: Anchor, onChange: (Anchor) -> Unit) {
    when (anchor) {
        is Anchor.Fixed -> TimeField(
            label = stringResource(R.string.editor_at),
            time = anchor.at,
            onPicked = { onChange(anchor.copy(at = it)) },
        )

        is Anchor.Window -> FromTo(anchor.from, anchor.to) { from, to -> onChange(anchor.copy(from = from, to = to)) }

        is Anchor.Relative -> MinutesField(
            label = stringResource(R.string.editor_after),
            minutes = anchor.offset.inWholeMinutes.toInt(),
            onMinutes = { onChange(anchor.copy(offset = it.minutes)) },
        )

        is Anchor.Interval -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            MinutesField(
                label = stringResource(R.string.editor_every),
                minutes = anchor.every.inWholeMinutes.toInt(),
                floor = MIN_EVERY,
                onMinutes = { onChange(anchor.copy(every = it.minutes)) },
            )

            FromTo(anchor.from, anchor.to) { from, to -> onChange(anchor.copy(from = from, to = to)) }
        }
    }
}

@Composable
private fun FromTo(from: LocalTime, to: LocalTime, onChange: (LocalTime, LocalTime) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TimeField(
            label = stringResource(R.string.editor_from),
            time = from,
            onPicked = { onChange(it, to) },
            modifier = Modifier.weight(1f),
        )

        TimeField(
            label = stringResource(R.string.editor_to),
            time = to,
            onPicked = { onChange(from, it) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MinutesField(
    label: String,
    minutes: Int,
    onMinutes: (Int) -> Unit,
    floor: Int = 0,
) {
    Column {
        Kicker(text = label)

        Stepper(
            value = stringResource(R.string.editor_minutes_value, minutes),
            onDecrement = { onMinutes((minutes - STEP_MINUTES).coerceAtLeast(floor)) },
            onIncrement = { onMinutes((minutes + STEP_MINUTES).coerceAtMost(MAX_MINUTES)) },
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** A window that ends before it starts cannot be saved. */
private fun Anchor.isSane(): Boolean = when (this) {
    is Anchor.Window -> to.isAfter(from)
    is Anchor.Interval -> to.isAfter(from)
    is Anchor.Fixed, is Anchor.Relative -> true
}
