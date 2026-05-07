package com.hluhovskyi.zero.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

/**
 * Layout with a fixed [topBar], a collapsible [hero] card that slides away on scroll,
 * and scrollable [content] below. The top bar is never affected by scroll gestures.
 */
@Composable
fun CollapsibleHeroLayout(
    topBar: @Composable () -> Unit,
    hero: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val heroHeightPx = remember { mutableStateOf(0) }
    val heroOffsetPx = remember { mutableStateOf(0f) }

    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y >= 0f) return Offset.Zero
                val newOffset = (heroOffsetPx.value + available.y)
                    .coerceIn(-heroHeightPx.value.toFloat(), 0f)
                val consumed = newOffset - heroOffsetPx.value
                heroOffsetPx.value = newOffset
                return Offset(0f, consumed)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (available.y <= 0f) return Offset.Zero
                val newOffset = (heroOffsetPx.value + available.y).coerceAtMost(0f)
                val delta = newOffset - heroOffsetPx.value
                heroOffsetPx.value = newOffset
                return Offset(0f, delta)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        topBar()
        Box(
            Modifier
                .weight(1f)
                .nestedScroll(connection)
                .clipToBounds(),
        ) {
            val topPaddingDp = with(LocalDensity.current) {
                (heroHeightPx.value + heroOffsetPx.value).coerceAtLeast(0f).toDp()
            }
            Box(Modifier.fillMaxSize().padding(top = topPaddingDp)) {
                content()
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { heroHeightPx.value = it.height }
                    .offset { IntOffset(0, heroOffsetPx.value.roundToInt()) },
            ) {
                hero()
            }
        }
    }
}
