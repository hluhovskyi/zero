package com.hluhovskyi.zero.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ui.theme.ZeroTheme

/**
 * @param selectedFraction Optional continuous indicator position (0..items.size-1) that overrides
 * the animation toward [selectedItem]. Pass a pager offset to make the indicator follow a swipe
 * gesture; leave null for tap-driven use.
 */
@Composable
fun <T> SegmentedToggle(
    modifier: Modifier = Modifier,
    items: List<T>,
    selectedItem: T?,
    onItemSelected: (T) -> Unit,
    labelMapping: (T) -> String,
    selectedFraction: Float? = null,
) {
    val selectedIndex = items.indexOf(selectedItem).coerceAtLeast(0)
    val animatedFraction by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "segmentedToggleIndicator",
    )
    val effectiveFraction = (selectedFraction ?: animatedFraction)
        .coerceIn(0f, (items.size - 1).coerceAtLeast(0).toFloat())

    Layout(
        modifier = modifier
            .background(ZeroTheme.colors.surfaceContainerLow, RoundedCornerShape(12.dp))
            .padding(4.dp),
        content = {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = ZeroTheme.colors.surfaceContainerLowest,
                elevation = 2.dp,
                content = {},
            )
            items.forEach { item ->
                val isSelected = item == selectedItem
                Box(
                    modifier = Modifier
                        .clickable { onItemSelected(item) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = labelMapping(item),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) {
                            MaterialTheme.colors.primary
                        } else {
                            ZeroTheme.colors.onSurfaceVariant
                        },
                        textAlign = TextAlign.Center,
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val itemCount = items.size.coerceAtLeast(1)
        val totalWidth = constraints.maxWidth
        val itemWidth = totalWidth / itemCount
        val itemConstraints = constraints.copy(minWidth = itemWidth, maxWidth = itemWidth)

        val labelPlaceables = measurables.drop(1).map { it.measure(itemConstraints) }
        val height = labelPlaceables.maxOfOrNull { it.height } ?: 0
        val indicatorPlaceable = measurables[0].measure(Constraints.fixed(itemWidth, height))

        layout(totalWidth, height) {
            indicatorPlaceable.placeRelative(
                x = (itemWidth * effectiveFraction).toInt(),
                y = 0,
            )
            labelPlaceables.forEachIndexed { index, placeable ->
                placeable.placeRelative(x = itemWidth * index, y = 0)
            }
        }
    }
}
