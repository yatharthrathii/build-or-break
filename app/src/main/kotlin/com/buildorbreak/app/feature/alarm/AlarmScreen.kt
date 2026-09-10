package com.buildorbreak.app.feature.alarm

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buildorbreak.app.R
import com.buildorbreak.core.designsystem.component.Badge
import com.buildorbreak.core.designsystem.component.BlockButton
import com.buildorbreak.core.designsystem.component.HeavyRule
import com.buildorbreak.core.designsystem.component.Kicker
import com.buildorbreak.core.designsystem.component.Wordmark
import com.buildorbreak.core.designsystem.theme.BuildOrBreakTheme
import com.buildorbreak.core.designsystem.theme.HeroNumberStyle
import com.buildorbreak.core.designsystem.theme.Theme
import java.util.Locale

/** How long the ringing bar takes to cross the screen once. */
private const val SWEEP_MILLIS = 1100

/** The hero time breathes rather than sits, so the eye lands on it first. */
private const val BREATH_MILLIS = 1400
private const val HERO_BREATH = 1.03f

private const val ARRIVE_MILLIS = 260
private val ARRIVE_FROM = 28.dp
private val BarHeight = 6.dp
private val BarWidth = 96.dp
private val CellHeight = 48.dp

/**
 * The alarm, full screen.
 *
 * Opened by the notification's full screen intent, over the lock screen, with
 * the display turned on. Nothing here is new: the same three actions as the
 * notification, drawn large enough to hit half asleep, plus the one thing the
 * notification cannot say, which is what a snooze would do to the rest of
 * the day.
 */
@Composable
fun AlarmScreen(
    occurrenceId: Long,
    itemId: Long,
    onClosed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlarmViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(occurrenceId) { viewModel.load(occurrenceId, itemId) }
    LaunchedEffect(state.gone) { if (state.gone) onClosed() }

    AlarmContent(
        state = state,
        onDone = { viewModel.onDone(onClosed) },
        onDoneMinimum = { viewModel.onDoneMinimum(onClosed) },
        onSnooze = { viewModel.onSnooze(onClosed) },
        onSkip = { viewModel.onSkip(onClosed) },
        modifier = modifier,
    )
}

@Composable
fun AlarmContent(
    state: AlarmUiState,
    onDone: () -> Unit,
    onDoneMinimum: () -> Unit,
    onSnooze: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .systemBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Wordmark(modifier = Modifier.weight(1f))
        }

        HeavyRule()

        RingingBar()

        Spacer(Modifier.weight(1f))

        Arriving { Heading(state = state) }

        Spacer(Modifier.weight(1f))

        Actions(state = state, onDone = onDone, onDoneMinimum = onDoneMinimum, onSnooze = onSnooze, onSkip = onSkip)
    }
}

/** The time, the step, and what kind of time it keeps. */
@Composable
private fun Heading(state: AlarmUiState) {
    Kicker(text = stringResource(R.string.alarm_kicker), color = MaterialTheme.colorScheme.primary)

    val transition = rememberInfiniteTransition(label = "hero")
    val breath by transition.animateFloat(
        initialValue = 1f,
        targetValue = HERO_BREATH,
        animationSpec = infiniteRepeatable(tween(BREATH_MILLIS), RepeatMode.Reverse),
        label = "breath",
    )

    Text(
        text = state.time,
        style = HeroNumberStyle,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .padding(top = 10.dp)
            .scale(breath),
    )

    Text(
        text = state.title,
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 14.dp),
    )

    Row(
        modifier = Modifier.padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Badge(text = stringResource(kindLabel(state.kindLabel)), accent = true)
        state.durationMinutes?.let { Badge(text = stringResource(R.string.today_duration_min, it)) }
    }
}

/**
 * The bar that says the alarm is live.
 *
 * A block of accent running back and forth under the rule, for as long as the
 * sound is playing. This is the one piece of motion the screen needs: somebody
 * waking up has to know at a glance that this is ringing now rather than a
 * notification they are reading afterwards, and a still screen cannot say that.
 *
 * It stops the moment the alarm is answered, because the screen closes with it.
 */
@Composable
private fun RingingBar() {
    val transition = rememberInfiniteTransition(label = "ringing")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SWEEP_MILLIS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sweep",
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(BarHeight)
            .background(Theme.colours.badge),
    ) {
        val travel = maxWidth - BarWidth

        Box(
            modifier = Modifier
                .offset { IntOffset(x = (travel * sweep).roundToPx(), y = 0) }
                .width(BarWidth)
                .height(BarHeight)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/**
 * The whole heading, arriving.
 *
 * Slides up and fades in once, on the first frame. An alarm screen that is
 * simply there reads as a screenshot; one that arrives reads as something that
 * just happened, which is what it is.
 */
@Composable
private fun Arriving(content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    val offset by animateDpAsState(
        targetValue = if (shown) 0.dp else ARRIVE_FROM,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "arrive",
    )
    val fade by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(ARRIVE_MILLIS),
        label = "fade",
    )

    // The lambda overload, so a value that changes every frame relayouts
    // rather than recomposing the subtree behind it.
    Column(modifier = Modifier.offset { IntOffset(x = 0, y = offset.roundToPx()) }.alpha(fade)) { content() }
}

@Composable
private fun Actions(
    state: AlarmUiState,
    onDone: () -> Unit,
    onDoneMinimum: () -> Unit,
    onSnooze: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 20.dp)) {
        state.movedBySnooze?.let { moved ->
            Text(
                text = pluralStringResource(R.plurals.alarm_snooze_moves, moved, moved),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        BlockButton(
            text = stringResource(R.string.today_done),
            onClick = onDone,
            enabled = state.occurrenceId > 0,
            icon = Icons.Outlined.Check,
        )

        SecondaryCells(state = state, onDoneMinimum = onDoneMinimum, onSnooze = onSnooze, onSkip = onSkip)
    }
}

@Composable
private fun SecondaryCells(
    state: AlarmUiState,
    onDoneMinimum: () -> Unit,
    onSnooze: () -> Unit,
    onSkip: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Theme.spacing.small)
            .height(CellHeight),
    ) {
        if (state.hasMinimum) {
            Cell(
                text = stringResource(R.string.today_done_minimum),
                onClick = onDoneMinimum,
                modifier = Modifier.weight(1f),
            )
            Gap()
        }

        Cell(text = stringResource(R.string.today_snooze_10), onClick = onSnooze, modifier = Modifier.weight(1f))
        Gap()
        Cell(
            text = stringResource(R.string.today_skip_today),
            onClick = onSkip,
            modifier = Modifier.weight(1f),
            muted = true,
        )
    }
}

@Composable
private fun Cell(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Theme.colours.raised)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelMedium,
            color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun Gap() {
    Spacer(Modifier.width(Theme.spacing.small))
}

private fun kindLabel(kind: AlarmKind): Int = when (kind) {
    AlarmKind.FIXED -> R.string.kind_fixed
    AlarmKind.RELATIVE -> R.string.anchor_relative
    AlarmKind.WINDOW -> R.string.kind_window
    AlarmKind.REPEAT -> R.string.anchor_interval
}

@Preview(name = "Alarm", showBackground = true)
@Composable
private fun AlarmPreview() {
    BuildOrBreakTheme(darkTheme = true) {
        AlarmContent(
            state = AlarmUiState(
                occurrenceId = 1,
                time = "06:40",
                title = "Wake + water",
                kindLabel = AlarmKind.FIXED,
                durationMinutes = null,
                hasMinimum = false,
                movedBySnooze = 3,
                gone = false,
            ),
            onDone = {},
            onDoneMinimum = {},
            onSnooze = {},
            onSkip = {},
        )
    }
}
