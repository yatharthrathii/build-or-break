package com.buildorbreak.core.designsystem.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.buildorbreak.core.designsystem.theme.Theme

private val RingSize = 96.dp
private val RingStroke = 10.dp
private const val FULL_SWEEP = 360f
private const val TOP = -90f

/**
 * The day, as a ring.
 *
 * Drawn rather than borrowed from Material, because Material's indicator has a
 * rounded cap and a gap, and this one has neither. A butt cap and a full track
 * make the ring look like a printed dial.
 *
 * The first composition starts from empty and springs up to the real value, so
 * opening the screen shows the morning filling in. After that the ring follows
 * every change on the same spring, which is what makes a completed step feel
 * like it registered.
 */
@Composable
fun ProgressRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    size: Dp = RingSize,
    stroke: Dp = RingStroke,
) {
    var target by remember { mutableStateOf(0f) }
    LaunchedEffect(fraction) { target = fraction.coerceIn(0f, 1f) }

    val sweep by animateFloatAsState(
        targetValue = target * FULL_SWEEP,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "ring",
    )

    val track = Theme.colours.rail
    val fill = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier.size(size)) {
        val width = stroke.toPx()
        val inset = width / 2
        val arcSize = Size(this.size.width - width, this.size.height - width)
        val topLeft = Offset(inset, inset)

        drawArc(
            color = track,
            startAngle = TOP,
            sweepAngle = FULL_SWEEP,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = width, cap = StrokeCap.Butt),
        )

        if (sweep > 0f) {
            drawArc(
                color = fill,
                startAngle = TOP,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = width, cap = StrokeCap.Butt),
            )
        }
    }
}
