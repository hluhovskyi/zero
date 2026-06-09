package com.hluhovskyi.zero.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hluhovskyi.zero.ui.theme.ZeroTheme

/**
 * Sparkline: min/max-normalized polyline with a gradient area fill and an end-point dot.
 * Degrades on its own terms: empty → a flat dashed baseline (never a blank box); 1 point →
 * a lone dot, no line (a line needs two points); flat series → centered line.
 */
@Composable
fun LineChart(
    data: LineChartData,
    lineColor: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 2.5.dp,
    showArea: Boolean = true,
    showEndpoint: Boolean = true,
    baselineColor: Color = ZeroTheme.colors.surfaceContainer,
) {
    val points = data.points
    val fractions = remember(points) { normalizeToFractions(points) }
    Canvas(modifier = modifier) {
        if (points.isEmpty()) {
            val y = size.height / 2f
            drawLine(
                color = baselineColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = strokeWidth.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 16f)),
            )
            return@Canvas
        }
        val pad = strokeWidth.toPx()
        val usableH = size.height - pad * 2
        val dotR = strokeWidth.toPx() * 1.4f
        fun xAt(i: Int) = if (points.size == 1) size.width / 2f else pad + (i.toFloat() / (points.size - 1)) * (size.width - pad * 2)
        fun yAt(i: Int) = pad + (1f - fractions[i]) * usableH

        if (points.size == 1) {
            if (showEndpoint) drawCircle(lineColor, dotR, Offset(xAt(0), yAt(0)))
            return@Canvas
        }
        val line = Path().apply {
            moveTo(xAt(0), yAt(0))
            for (i in 1 until points.size) lineTo(xAt(i), yAt(i))
        }
        if (showArea) {
            val area = Path().apply {
                addPath(line)
                lineTo(xAt(points.size - 1), size.height)
                lineTo(xAt(0), size.height)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.28f), lineColor.copy(alpha = 0f))))
        }
        drawPath(line, lineColor, style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (showEndpoint) drawCircle(lineColor, dotR, Offset(xAt(points.size - 1), yAt(points.size - 1)))
    }
}
