package com.buildorbreak.app.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.buildorbreak.app.R
import com.buildorbreak.core.designsystem.component.GhostButton
import com.buildorbreak.core.designsystem.component.OutlineButton
import com.buildorbreak.core.model.enums.SkipChip
import java.util.Locale

/**
 * Why a step is not happening, asked once and never insisted on.
 *
 * The weekly review has nothing useful to say without this. "You skipped the
 * walk four times" is a fact the user can already see on their own timeline;
 * "you skipped it four times, and three of those were because work came up" is
 * the one that leads somewhere, because it suggests moving the walk rather than
 * trying harder.
 *
 * So it is asked, and it is one tap, and there is always a way past it. A
 * dialog that demands a justification at the moment somebody is already having
 * a bad day is a dialog that teaches them to stop opening the app, and the data
 * it collects by force is worth less than the data it costs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SkipSheet(
    title: String,
    mode: SkipAskMode,
    onSkip: (SkipChip?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 28.dp)) {
            Text(
                text = stringResource(headline(mode)).uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )

            Text(
                text = stringResource(body(mode)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            Chips(onPick = onSkip)

            GhostButton(
                text = stringResource(wayOut(mode)),
                onClick = { onSkip(null) },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

/**
 * Asking "what happened" about something that has not happened yet reads as a
 * bug, and asking it about something already settled has to admit that it is
 * looking backwards. Three questions, three headlines.
 */
private fun headline(mode: SkipAskMode): Int = when (mode) {
    SkipAskMode.HAPPENED, SkipAskMode.AFTER_THE_FACT -> R.string.skip_title
    SkipAskMode.AHEAD -> R.string.skip_title_ahead
}

private fun body(mode: SkipAskMode): Int = when (mode) {
    SkipAskMode.HAPPENED -> R.string.skip_body
    SkipAskMode.AHEAD -> R.string.skip_body_ahead
    SkipAskMode.AFTER_THE_FACT -> R.string.skip_body_after
}

/**
 * The way past, which every one of the three has to have.
 *
 * After the fact it is worded as a refusal to be asked rather than as a skip,
 * because the skip already happened and a button offering to do it again would
 * be a button nobody trusts.
 */
private fun wayOut(mode: SkipAskMode): Int = when (mode) {
    SkipAskMode.HAPPENED, SkipAskMode.AHEAD -> R.string.skip_no_reason
    SkipAskMode.AFTER_THE_FACT -> R.string.skip_rather_not_say
}

@Composable
private fun Chips(onPick: (SkipChip) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CHIPS.forEach { chip ->
            OutlineButton(text = stringResource(chipLabel(chip)), onClick = { onPick(chip) })
        }
    }
}

/**
 * Six of the eight, in the order they actually happen.
 *
 * `OTHER` is left out because it is what the last button already means, and
 * `DID_IT_LATER` because a step done later is completed, not skipped, and
 * offering it here would quietly turn a kept day into a broken one.
 */
private val CHIPS = listOf(
    SkipChip.NO_TIME,
    SkipChip.WORK_CAME_UP,
    SkipChip.FORGOT,
    SkipChip.NOT_IN_MOOD,
    SkipChip.UNWELL,
    SkipChip.TRAVELLING,
)

private fun chipLabel(chip: SkipChip): Int = when (chip) {
    SkipChip.WORK_CAME_UP -> R.string.skip_work
    SkipChip.FORGOT -> R.string.skip_forgot
    SkipChip.NOT_IN_MOOD -> R.string.skip_mood
    SkipChip.UNWELL -> R.string.skip_unwell
    SkipChip.TRAVELLING -> R.string.skip_travelling
    SkipChip.NO_TIME -> R.string.skip_no_time
    SkipChip.DID_IT_LATER -> R.string.skip_later
    SkipChip.OTHER -> R.string.skip_other
}
