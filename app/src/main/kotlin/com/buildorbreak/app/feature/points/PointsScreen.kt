package com.buildorbreak.app.feature.points

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buildorbreak.app.R
import com.buildorbreak.app.feature.about.BackHeader
import com.buildorbreak.core.designsystem.component.Badge
import com.buildorbreak.core.designsystem.component.FillButton
import com.buildorbreak.core.designsystem.component.GhostButton
import com.buildorbreak.core.designsystem.component.HairlineRule
import com.buildorbreak.core.designsystem.component.Kicker
import com.buildorbreak.core.designsystem.component.Label
import com.buildorbreak.core.designsystem.component.OutlineButton
import com.buildorbreak.core.designsystem.component.Panel
import com.buildorbreak.core.designsystem.component.SectionLabel
import com.buildorbreak.core.designsystem.theme.BuildOrBreakTheme
import com.buildorbreak.core.designsystem.theme.Theme
import com.buildorbreak.core.model.enums.PointReason
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.collections.immutable.persistentListOf

/**
 * "Fri 12 Sep". Long enough to place the day, short enough for a list.
 *
 * A function rather than a value, because a value is read once when the
 * class loads and would keep the locale the app started in. Somebody who
 * switches the phone to Hindi and comes back would find the dates still in
 * English until the process was killed.
 */
private fun rowDate(): DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

/**
 * What the routine has earned, what it buys, and where it went.
 *
 * The points already existed as a score on Insights, which is a number with
 * nothing behind it. This is the other half: a balance that can be spent, a
 * plain list of what it is for, and every movement with a date on it, so
 * the figure can be checked rather than believed.
 *
 * The ad row is here and does not work yet, on purpose. Wiring the ads SDK
 * is the moment this app stops being one that never touches the network, so
 * it waits until there is a reason. The button says as much when tapped.
 */
@Composable
fun PointsScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: PointsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    PointsContent(
        state = state,
        onWatchAd = viewModel::onWatchAd,
        onDismissAd = viewModel::onDismissAd,
        freezeActions = FreezeActions(
            onAsk = viewModel::onAskFreeze,
            onConfirm = { viewModel.onConfirmFreeze() },
            onDismiss = viewModel::onDismissFreeze,
        ),
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun PointsContent(
    state: PointsUiState,
    onWatchAd: () -> Unit,
    onDismissAd: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    freezeActions: FreezeActions = FreezeActions(),
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding(),
    ) {
        BackHeader(
            kicker = stringResource(R.string.points_kicker),
            title = stringResource(R.string.points_title),
            onBack = onBack,
        )

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { Balance(state = state) }
            item { AdRow(reward = state.adReward, available = state.adAvailable, onWatchAd = onWatchAd) }

            item { SectionLabel(text = stringResource(R.string.points_unlocks), underlined = true) }
            items(items = state.unlocks, key = { it.kind.name }) { unlock ->
                UnlockRow(unlock = unlock) {
                    if (unlock.kind == UnlockKind.STREAK_FREEZE) {
                        FreezeOffer(freeze = state.freeze, onAsk = freezeActions.onAsk)
                    }
                }
            }

            item { SectionLabel(text = stringResource(R.string.points_history), underlined = true) }

            if (state.movements.isEmpty()) {
                item { Empty(loaded = state.loaded) }
            } else {
                items(items = state.movements, key = { "${it.date}${it.reason}${it.delta}" }) { MovementRow(it) }
            }
        }
    }

    if (state.adNotReady) AdNotReadyDialog(onDismiss = onDismissAd)

    state.freeze?.takeIf { it.asking }?.let { FreezeDialog(freeze = it, actions = freezeActions) }
}

/** The number that matters, with the two it is made of underneath. */
@Composable
private fun Balance(state: PointsUiState) {
    Column(modifier = Modifier.padding(Theme.spacing.medium)) {
        Text(
            text = count(state.balance),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Label(text = stringResource(R.string.points_balance))

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Theme.spacing.small),
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.medium),
        ) {
            Figure(value = state.earned, label = stringResource(R.string.points_earned))
            Figure(value = state.spent, label = stringResource(R.string.points_spent))
        }
    }
}

@Composable
private fun Figure(value: Int, label: String) {
    Column {
        Text(
            text = count(value),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Label(text = label, maxLines = 2)
    }
}

/**
 * The one way to earn points that is not the routine itself.
 *
 * Offered, never pushed. Nothing else on this screen changes whether it is
 * tapped, and declining costs nothing, which is both the decent way round
 * and what Google's rewarded-ad policy requires.
 */
@Composable
private fun AdRow(reward: Int, available: Boolean, onWatchAd: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = Theme.spacing.medium)) {
        HairlineRule()

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = Theme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.small),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.points_ad_title, reward),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(
                        if (available) R.string.points_ad_body else R.string.points_ad_done,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlineButton(
                text = stringResource(R.string.points_ad_action),
                onClick = onWatchAd,
                enabled = available,
            )
        }

        HairlineRule()
    }
}

/** The three things the freeze row can ask for, so they travel as one parameter. */
@Immutable
data class FreezeActions(
    val onAsk: () -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onDismiss: () -> Unit = {},
)

/**
 * The day on offer, or a line saying there is none.
 *
 * The row used to show a price and nothing else, which is a shop window
 * with no door. Now it always says where things stand: either here is the
 * day and the button, or there is nothing to cover and this is where it
 * will appear.
 */
@Composable
private fun FreezeOffer(freeze: FreezeUi?, onAsk: () -> Unit) {
    if (freeze == null) {
        Text(
            text = stringResource(R.string.points_freeze_none),
            style = MaterialTheme.typography.bodySmall,
            color = Theme.colours.faint,
            modifier = Modifier.padding(bottom = Theme.spacing.small),
        )
        return
    }

    Column(modifier = Modifier.padding(bottom = Theme.spacing.inset)) {
        Text(
            text = stringResource(
                R.string.points_freeze_offer,
                freeze.date.format(rowDate()),
                freeze.runNow,
                freeze.runAfter,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(bottom = Theme.spacing.small),
        )

        OutlineButton(
            text = stringResource(R.string.points_freeze_action),
            onClick = onAsk,
            enabled = freeze.affordable,
        )

        if (!freeze.affordable) NotEnough(cost = freeze.cost)
    }
}

@Composable
private fun NotEnough(cost: Int) {
    Text(
        text = stringResource(R.string.points_freeze_short, cost),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Theme.spacing.small),
    )
}

@Composable
private fun FreezeDialog(freeze: FreezeUi, actions: FreezeActions) {
    Dialog(onDismissRequest = actions.onDismiss) {
        Panel {
            Column(modifier = Modifier.padding(Theme.spacing.medium)) {
                Text(
                    text = stringResource(R.string.points_freeze_title, freeze.date.format(rowDate())),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    text = stringResource(
                        if (freeze.failed) R.string.points_freeze_failed else R.string.points_freeze_body,
                        freeze.cost,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Theme.spacing.small, bottom = Theme.spacing.medium),
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(Theme.spacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FillButton(
                        text = stringResource(R.string.points_freeze_confirm, freeze.cost),
                        onClick = actions.onConfirm,
                    )
                    GhostButton(text = stringResource(R.string.editor_cancel), onClick = actions.onDismiss)
                }
            }
        }
    }
}

@Composable
private fun UnlockRow(unlock: UnlockUi, extra: @Composable () -> Unit = {}) {
    Column(modifier = Modifier.padding(horizontal = Theme.spacing.medium)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = Theme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.small),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(unlockTitle(unlock.kind)),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(unlockBody(unlock.kind)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (unlock.cost == null) {
                Badge(text = stringResource(R.string.points_planned))
            } else {
                Text(
                    text = count(unlock.cost),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (unlock.affordable) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Theme.colours.faint
                    },
                )
            }
        }

        extra()

        HairlineRule()
    }
}

@Composable
private fun MovementRow(movement: MovementUi) {
    Column(modifier = Modifier.padding(horizontal = Theme.spacing.medium)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = Theme.spacing.inset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(movementTitle(movement.reason)),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Kicker(text = movement.date.format(rowDate()))
            }

            Text(
                text = signed(movement.delta),
                style = MaterialTheme.typography.titleMedium,
                color = if (movement.delta < 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }

        HairlineRule()
    }
}

@Composable
private fun Empty(loaded: Boolean) {
    if (!loaded) return

    Text(
        text = stringResource(R.string.points_history_none),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(Theme.spacing.medium),
    )
}

@Composable
private fun AdNotReadyDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Panel {
            Column(modifier = Modifier.padding(Theme.spacing.medium)) {
                Text(
                    text = stringResource(R.string.points_ad_soon_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    text = stringResource(R.string.points_ad_soon_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Theme.spacing.small, bottom = Theme.spacing.medium),
                )

                FillButton(text = stringResource(R.string.action_close), onClick = onDismiss)
            }
        }
    }
}

private fun unlockTitle(kind: UnlockKind): Int = when (kind) {
    UnlockKind.UNDO_STEP -> R.string.points_unlock_undo
    UnlockKind.STREAK_FREEZE -> R.string.points_unlock_freeze
    UnlockKind.EXTRA_ROUTINE -> R.string.points_unlock_routine
    UnlockKind.SECOND_GOAL -> R.string.points_unlock_goal
}

private fun unlockBody(kind: UnlockKind): Int = when (kind) {
    UnlockKind.UNDO_STEP -> R.string.points_unlock_undo_body
    UnlockKind.STREAK_FREEZE -> R.string.points_unlock_freeze_body
    UnlockKind.EXTRA_ROUTINE -> R.string.points_unlock_routine_body
    UnlockKind.SECOND_GOAL -> R.string.points_unlock_goal_body
}

private fun movementTitle(reason: PointReason?): Int = when (reason) {
    null -> R.string.points_move_day
    PointReason.AD_REWARD -> R.string.points_move_ad
    PointReason.UNDO_STEP -> R.string.points_unlock_undo
    PointReason.STREAK_FREEZE -> R.string.points_unlock_freeze
    PointReason.EXTRA_ROUTINE -> R.string.points_unlock_routine
}

private fun count(value: Int): String = NumberFormat.getIntegerInstance().format(value)

/** A spend reads as "−150" rather than as a bare number that could be either. */
private fun signed(value: Int): String = if (value < 0) "−" + count(-value) else "+" + count(value)

@Preview(name = "Points", showBackground = true)
@Composable
private fun PointsPreview() {
    BuildOrBreakTheme {
        PointsContent(
            state = PointsUiState(
                loaded = true,
                balance = 640,
                earned = 790,
                spent = 150,
                unlocks = persistentListOf(
                    UnlockUi(UnlockKind.UNDO_STEP, 150, affordable = true),
                    UnlockUi(UnlockKind.STREAK_FREEZE, 200, affordable = true),
                    UnlockUi(UnlockKind.EXTRA_ROUTINE, 500, affordable = true),
                    UnlockUi(UnlockKind.SECOND_GOAL, null, affordable = false),
                ),
                movements = persistentListOf(
                    MovementUi(LocalDate.of(2026, 9, 19), -150, PointReason.UNDO_STEP),
                    MovementUi(LocalDate.of(2026, 9, 19), 110, null),
                    MovementUi(LocalDate.of(2026, 9, 18), 95, null),
                ),
            ),
            onWatchAd = {},
            onDismissAd = {},
            onBack = {},
        )
    }
}
