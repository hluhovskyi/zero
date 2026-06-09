package com.hluhovskyi.zero.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hluhovskyi.zero.ui.theme.ZeroTheme

/**
 * Donut: one arc per segment (swept by value/total) over a full track ring, with a centered
 * [content] slot. Degrades on its own terms: total 0 / empty → a hollow dashed ring (so it reads
 * as "no spending yet", not a filled circle); the center [content] can hold an empty label.
 */
@Composable
fun DonutChart(
    data: DonutChartData,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 14.dp,
    trackColor: Color = ZeroTheme.colors.surfaceContainer,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val segments = data.segments
    val total = segments.fold(0f) { acc, s -> acc + s.value }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val diameter = size.minDimension - stroke
            val arcSize = Size(diameter, diameter)
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            if (total <= 0f) {
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 22f))),
                )
                return@Canvas
            }
            drawArc(trackColor, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            var start = -90f
            segments.forEach { seg ->
                val sweep = seg.value / total * 360f
                drawArc(seg.color, start, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                start += sweep
            }
        }
        content()
    }
}
