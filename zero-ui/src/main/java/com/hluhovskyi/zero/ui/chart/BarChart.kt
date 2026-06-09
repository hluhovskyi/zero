package com.hluhovskyi.zero.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ui.theme.ZeroTheme

/**
 * Vertical bars, grouped (e.g. cash in/out) or single. Bar width/gap adapt to bucket count.
 * Keeps the chart frame for sparse data: a bucket with no bars renders a dashed baseline
 * placeholder; an all-zero series renders faint solid baseline tracks (never a blank box or a
 * misleading flat colored line). A lone zero among real values still shows a 2dp colored stub.
 *
 * Per-bar [BarValue.color] carries emphasis (e.g. a dimmed past month vs a vivid current month),
 * and [BarGroup.topLabel] renders a value caption above the bar(s) — together these cover the
 * single-series "category trend" chart (value on top, current period highlighted).
 */
@Composable
fun BarChart(
    data: BarChartData,
    modifier: Modifier = Modifier,
    barAreaHeight: Dp = 104.dp,
    showLabels: Boolean = true,
    barCornerRadius: Dp = 3.dp,
    barWidth: Dp? = null,
    emptyBarColor: Color = ZeroTheme.colors.surfaceContainer,
    placeholderColor: Color = ZeroTheme.colors.outlineVariant,
) {
    val groups = data.groups
    if (groups.isEmpty()) {
        Box(modifier.height(barAreaHeight), contentAlignment = Alignment.BottomCenter) {
            DashedBaseline(placeholderColor, Modifier.fillMaxWidth())
        }
        return
    }
    val maxValue = groups.flatMap { it.bars }.maxOfOrNull { it.value } ?: 0f
    val hasData = maxValue > 0f
    val n = groups.size
    val barW = barWidth ?: adaptiveBarWidthDp(n).dp
    val innerGap = adaptiveInnerGapDp(n).dp
    val outerGap = if (n > 16) 1.dp else 4.dp
    val labelsVisible = showLabels && shouldShowBarLabels(n)
    val topLabelsVisible = groups.any { it.topLabel != null }
    Column(modifier) {
        if (topLabelsVisible) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(outerGap),
            ) {
                groups.forEach { group ->
                    Text(
                        text = group.topLabel.orEmpty(),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = TextStyle(
                            fontSize = if (n > 9) 10.sp else 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ZeroTheme.colors.onSurface,
                        ),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(barAreaHeight),
            horizontalArrangement = Arrangement.spacedBy(outerGap),
            verticalAlignment = Alignment.Bottom,
        ) {
            groups.forEach { group ->
                Row(
                    modifier = Modifier.weight(1f).height(barAreaHeight),
                    horizontalArrangement = Arrangement.spacedBy(innerGap, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    if (group.bars.isEmpty()) {
                        DashedBaseline(placeholderColor, Modifier.fillMaxWidth(0.6f))
                    } else {
                        group.bars.forEach { bar ->
                            if (!hasData) {
                                Box(
                                    modifier = Modifier
                                        .width(barW)
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(emptyBarColor),
                                )
                            } else {
                                val frac = (bar.value / maxValue).coerceIn(0f, 1f)
                                val h = (frac * barAreaHeight.value).coerceAtLeast(2f).dp
                                Box(
                                    modifier = Modifier
                                        .width(barW)
                                        .height(h)
                                        .clip(RoundedCornerShape(barCornerRadius))
                                        .background(bar.color),
                                )
                            }
                        }
                    }
                }
            }
        }
        if (labelsVisible) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(outerGap),
            ) {
                groups.forEach { group ->
                    Text(
                        text = group.label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = TextStyle(
                            fontSize = if (n > 9) 9.sp else 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ZeroTheme.colors.onSurfaceVariant,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun DashedBaseline(color: Color, modifier: Modifier) {
    Canvas(modifier.height(3.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = size.height,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f)),
        )
    }
}
