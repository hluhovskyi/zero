package com.hluhovskyi.zero.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ui.theme.ZeroTheme
import kotlinx.coroutines.launch
import kotlin.math.abs

internal enum class SwipeOutcome { Previous, Next, Tap, None }

/** Decide what a released vertical drag means. Up (negative dy) = next, down = previous. */
internal fun resolveSwipe(
    totalDy: Float,
    commitThreshold: Float,
    tapSlop: Float,
    canSelectPrevious: Boolean,
    canSelectNext: Boolean,
): SwipeOutcome = when {
    totalDy < -commitThreshold && canSelectNext -> SwipeOutcome.Next
    totalDy > commitThreshold && canSelectPrevious -> SwipeOutcome.Previous
    abs(totalDy) < tapSlop -> SwipeOutcome.Tap
    else -> SwipeOutcome.None
}

private val RowHeight = 40.dp
private val SwipeEasing = CubicBezierEasing(0.34f, 0.1f, 0.2f, 1f)
private const val SwipeDurationMs = 180

/**
 * Generic vertical swipe-to-select tile. Swipe up → next, swipe down → previous; bounces at the
 * edges. The caller owns the data and supplies the [current] / [previous] / [next] face slots plus
 * the commit callbacks — the rendered content is intentionally out of scope here. The 40dp viewport
 * clips a three-face spinner (neighbours at reduced opacity) that slides one row on commit. The
 * dropdown arrow is replaced by a [Icons.Filled.SwapVert] affordance in the top-right corner.
 *
 * Reused for the transaction-edit Date and Account tiles; the callback shape also fits an unbounded
 * sequence (dates) and a future Category tile.
 */
@Composable
fun SwipeSelectTile(
    label: String,
    onSelectPrevious: () -> Unit,
    onSelectNext: () -> Unit,
    modifier: Modifier = Modifier,
    canSelectPrevious: Boolean = true,
    canSelectNext: Boolean = true,
    onClick: (() -> Unit)? = null,
    previous: (@Composable () -> Unit)? = null,
    next: (@Composable () -> Unit)? = null,
    current: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val rowPx = with(density) { RowHeight.toPx() }
    val commitThreshold = with(density) { 14.dp.toPx() }
    val tapSlop = with(density) { 5.dp.toPx() }

    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val animSpec = tween<Float>(SwipeDurationMs, easing = SwipeEasing)

    val draggableState = rememberDraggableState { delta ->
        scope.launch { offset.snapTo((offset.value + delta).coerceIn(-rowPx, rowPx)) }
    }

    val gesture = Modifier
        .draggable(
            state = draggableState,
            orientation = Orientation.Vertical,
            onDragStopped = {
                when (
                    resolveSwipe(offset.value, commitThreshold, tapSlop, canSelectPrevious, canSelectNext)
                ) {
                    SwipeOutcome.Next -> {
                        offset.animateTo(-rowPx, animSpec)
                        onSelectNext()
                        offset.snapTo(0f)
                    }
                    SwipeOutcome.Previous -> {
                        offset.animateTo(rowPx, animSpec)
                        onSelectPrevious()
                        offset.snapTo(0f)
                    }
                    SwipeOutcome.Tap, SwipeOutcome.None -> offset.animateTo(0f, animSpec)
                }
            },
        )
        .let { if (onClick != null) it.clickable { onClick() } else it }

    Column(
        modifier = modifier
            .background(ZeroTheme.colors.surfaceContainerLow, RoundedCornerShape(16.dp))
            .then(gesture)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label.uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = ZeroTheme.colors.onSurfaceVariant,
                letterSpacing = 1.sp,
            )
            Icon(
                imageVector = Icons.Filled.SwapVert,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = ZeroTheme.colors.outline,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(RowHeight)
                .padding(top = 4.dp)
                .clipToBounds(),
        ) {
            Column(
                modifier = Modifier.graphicsLayer { translationY = -rowPx + offset.value },
            ) {
                SwipeFace(alpha = 0.3f) { previous?.invoke() }
                SwipeFace(alpha = 1f) { current() }
                SwipeFace(alpha = 0.3f) { next?.invoke() }
            }
        }
    }
}

@Composable
private fun SwipeFace(alpha: Float, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .height(RowHeight)
            .fillMaxWidth()
            .alpha(alpha),
        contentAlignment = Alignment.CenterStart,
    ) { content() }
}
