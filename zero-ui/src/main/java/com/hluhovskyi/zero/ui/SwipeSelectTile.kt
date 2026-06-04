package com.hluhovskyi.zero.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ui.theme.ZeroTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

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

/** Opacity drop for a face one full row away from the viewport centre (centre = 1f, neighbour = 0.3f). */
private const val NeighbourFade = 0.7f

/**
 * Generic vertical swipe-to-select tile. Swipe up → next, swipe down → previous; bounces at the
 * edges. The caller owns the data and supplies the [current] / [previous] / [next] face slots plus
 * the commit callbacks — the rendered content is intentionally out of scope here. The 40dp viewport
 * clips a three-face spinner whose opacity tracks distance from centre, so the arriving value lands
 * crisp. The dropdown arrow is replaced by a [Icons.Filled.SwapVert] affordance in the top-right.
 *
 * On commit the just-selected neighbour is shown in the centre *optimistically* — the tile folds
 * back to the parent's value only once [currentKey] reports the new selection. That fold-back is
 * derived during composition, so it's atomic with the parent's (async) state update and never
 * flashes the previous value mid-swipe. Pass a stable [currentKey] (e.g. the selected id) to enable
 * it; without one the tile still works but can flicker on slow state updates.
 *
 * Reused for the transaction-edit Date, Account and Category tiles.
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
    currentKey: Any? = null,
    previous: (@Composable () -> Unit)? = null,
    next: (@Composable () -> Unit)? = null,
    current: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val rowPx = with(density) { RowHeight.toPx() }
    val commitThreshold = with(density) { 14.dp.toPx() }
    val tapSlop = with(density) { 5.dp.toPx() }
    val animSpec = remember { tween<Float>(SwipeDurationMs, easing = SwipeEasing) }

    // Visual offset of the spinner column, +down / -up. Written synchronously by drag and by the
    // settle/commit animation, so there's no cross-scope race to freeze the tile mid-swipe.
    var offsetPx by remember { mutableFloatStateOf(0f) }

    // Optimistic commit latch: while waiting for the parent to apply a committed selection, render
    // its neighbour in the centre. `pending` clears the instant currentKey moves off the latched
    // (pre-commit) key — the same composition that swaps in the parent's new value.
    var committedKey by remember { mutableStateOf<Any?>(null) }
    var committedDir by remember { mutableIntStateOf(0) }
    val pending = committedKey != null && currentKey == committedKey
    val shift = if (pending) committedDir else 0
    LaunchedEffect(currentKey) {
        if (committedKey != null && currentKey != committedKey) committedKey = null
    }

    val scope = rememberCoroutineScope()
    var settleJob by remember { mutableStateOf<Job?>(null) }

    val draggableState = rememberDraggableState { delta ->
        offsetPx = (offsetPx + delta).coerceIn(-rowPx, rowPx)
    }

    val gesture = Modifier
        .draggable(
            state = draggableState,
            orientation = Orientation.Vertical,
            onDragStarted = { settleJob?.cancel() },
            onDragStopped = {
                val outcome =
                    resolveSwipe(offsetPx, commitThreshold, tapSlop, canSelectPrevious, canSelectNext)
                settleJob = scope.launch {
                    when (outcome) {
                        SwipeOutcome.Next -> {
                            animate(offsetPx, -rowPx, animationSpec = animSpec) { v, _ -> offsetPx = v }
                            // shift=+1 @ offset 0 renders identically to shift=0 @ offset -rowPx
                            // (next centred), so this swap is seamless; the parent catches up later.
                            Snapshot.withMutableSnapshot {
                                committedDir = 1
                                committedKey = currentKey
                                offsetPx = 0f
                            }
                            onSelectNext()
                        }
                        SwipeOutcome.Previous -> {
                            animate(offsetPx, rowPx, animationSpec = animSpec) { v, _ -> offsetPx = v }
                            Snapshot.withMutableSnapshot {
                                committedDir = -1
                                committedKey = currentKey
                                offsetPx = 0f
                            }
                            onSelectPrevious()
                        }
                        SwipeOutcome.Tap, SwipeOutcome.None ->
                            animate(offsetPx, 0f, animationSpec = animSpec) { v, _ -> offsetPx = v }
                    }
                }
            },
        )
        .let { if (onClick != null) it.clickable { onClick() } else it }

    // The three rendered slots, shifted by the optimistic latch so the committed value sits centre.
    val topFace = when (shift) {
        1 -> current
        -1 -> null
        else -> previous
    }
    val centerFace = when (shift) {
        1 -> next ?: current
        -1 -> previous ?: current
        else -> current
    }
    val bottomFace = when (shift) {
        1 -> null
        -1 -> current
        else -> next
    }

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

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(RowHeight)
                .clipToBounds(),
            contentAlignment = Alignment.Center,
        ) {
            // A 3-row column whose middle face is centred at rest; the layout offset keeps the
            // centre face in bounds (drawn + accessible) and slides it ±1 row during a swipe.
            Column(
                modifier = Modifier
                    .requiredHeight(RowHeight * 3)
                    .offset { IntOffset(0, offsetPx.roundToInt()) },
            ) {
                SwipeFace(slot = -1, offsetPx = { offsetPx }, rowPx = rowPx) { topFace?.invoke() }
                SwipeFace(slot = 0, offsetPx = { offsetPx }, rowPx = rowPx) { centerFace() }
                SwipeFace(slot = 1, offsetPx = { offsetPx }, rowPx = rowPx) { bottomFace?.invoke() }
            }
        }
    }
}

@Composable
private fun SwipeFace(
    slot: Int,
    offsetPx: () -> Float,
    rowPx: Float,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(RowHeight)
            .fillMaxWidth()
            .graphicsLayer {
                // Distance of this face's centre from the viewport centre, in rows.
                val rowsFromCentre = (abs(offsetPx() + slot * rowPx) / rowPx).coerceIn(0f, 1f)
                alpha = 1f - NeighbourFade * rowsFromCentre
            },
        contentAlignment = Alignment.CenterStart,
    ) { content() }
}
