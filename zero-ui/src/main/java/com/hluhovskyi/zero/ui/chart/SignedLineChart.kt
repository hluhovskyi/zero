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
 * Signed area/line chart (e.g. net worth that can go underwater). The domain always includes 0,
 * a dashed zero baseline is drawn, and the area fills toward that baseline — not the canvas
 * bottom — so above/below zero reads correctly. The line/area is colored by the sign of the
 * latest point ([negativeColor] when underwater, else [positiveColor]).
 *
 * Degrades like [LineChart]: empty → a dashed baseline; 1 point → a lone dot on the zero line.
 */
@Composable
fun SignedLineChart(
    data: LineChartData,
    modifier: Modifier = Modifier,
    positiveColor: Color = ZeroTheme.colors.secondary,
    negativeColor: Color = ZeroTheme.colors.error,
    zeroLineColor: Color = ZeroTheme.colors.onSurfaceVariant,
    strokeWidth: Dp = 2.5.dp,
    showEndpoint: Boolean = true,
) {
    val points = data.points
    val scale = remember(points) { signedScale(points) }
    Canvas(modifier = modifier) {
        val pad = strokeWidth.toPx()
        val usableH = size.height - pad * 2
        val dotR = strokeWidth.toPx() * 1.4f
        fun xAt(i: Int) = if (points.size == 1) size.width / 2f else pad + (i.toFloat() / (points.size - 1)) * (size.width - pad * 2)
        fun yAt(fraction: Float) = pad + (1f - fraction) * usableH

        if (points.isEmpty()) {
            val y = size.height / 2f
            drawLine(
                color = zeroLineColor.copy(alpha = 0.6f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )
            return@Canvas
        }

        val color = if (points.last() < 0f) negativeColor else positiveColor
        val zeroY = yAt(scale.zeroFraction)

        // Zero reference line.
        drawLine(
            color = zeroLineColor.copy(alpha = 0.6f),
            start = Offset(0f, zeroY),
            end = Offset(size.width, zeroY),
            strokeWidth = 1f.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
        )

        if (points.size == 1) {
            if (showEndpoint) drawCircle(color, dotR, Offset(xAt(0), yAt(scale.fractions[0])))
            return@Canvas
        }

        val line = Path().apply {
            moveTo(xAt(0), yAt(scale.fractions[0]))
            for (i in 1 until points.size) lineTo(xAt(i), yAt(scale.fractions[i]))
        }
        // Area anchored to the zero line so under/over zero is shaded toward the baseline.
        val area = Path().apply {
            addPath(line)
            lineTo(xAt(points.size - 1), zeroY)
            lineTo(xAt(0), zeroY)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.05f), color.copy(alpha = 0.26f))))
        drawPath(line, color, style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (showEndpoint) drawCircle(color, dotR, Offset(xAt(points.size - 1), yAt(scale.fractions[points.size - 1])))
    }
}
