package com.hluhovskyi.zero.budget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.common.AmountFormatter
import kotlin.math.roundToInt

private val SummaryBg = Color(0xFF1A2E52)
private val SummaryTextStrong = Color(0xE6FFFFFF)
private val SummaryTextDim = Color(0x73FFFFFF)
private val SummaryTrack = Color(0x14FFFFFF)
private val GreenAccent = Color(0xFF5DDBA8)
private val OrangeAccent = Color(0xFFFFB74D)
private val RedAccent = Color(0xFFFF8A65)
private val OverPillBg = Color(0x2EFF641E)
private val OnTrackPillBg = Color(0x265DDBA8)

@Composable
internal fun SummaryBar(
    summary: BudgetUseCase.Summary,
    amountFormatter: AmountFormatter,
    modifier: Modifier = Modifier,
) {
    val fillColor = when {
        summary.isOver -> RedAccent
        summary.overallPct > 0.85f -> OrangeAccent
        else -> GreenAccent
    }
    val diff = if (summary.isOver) {
        summary.totalSpent - summary.totalBudgeted
    } else {
        summary.totalBudgeted - summary.totalSpent
    }
    val remainingDisplay = if (summary.isOver) {
        "-${amountFormatter.format(diff)}"
    } else {
        amountFormatter.format(diff)
    }
    val remainingColor = if (summary.isOver) RedAccent else GreenAccent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(SummaryBg, RoundedCornerShape(20.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Donut(pct = summary.overallPct, fillColor = fillColor)
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                LabeledNumber(
                    label = stringResource(R.string.budget_summary_spent),
                    value = amountFormatter.format(summary.totalSpent),
                    valueColor = SummaryTextStrong,
                    horizontalAlignment = Alignment.Start,
                )
                LabeledNumber(
                    label = stringResource(R.string.budget_summary_remaining),
                    value = remainingDisplay,
                    valueColor = remainingColor,
                    horizontalAlignment = Alignment.CenterHorizontally,
                )
                LabeledNumber(
                    label = stringResource(R.string.budget_summary_budget),
                    value = amountFormatter.format(summary.totalBudgeted),
                    valueColor = SummaryTextDim,
                    valueWeight = FontWeight.Bold,
                    horizontalAlignment = Alignment.End,
                )
            }
            StatusPill(overCount = summary.overCount, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun Donut(pct: Float, fillColor: Color) {
    Box(modifier = Modifier.size(68.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = 7.dp.toPx()
            val diameter = 52.dp.toPx()
            val topLeft = Offset(
                x = (size.width - diameter) / 2f,
                y = (size.height - diameter) / 2f,
            )
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = SummaryTrack,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx),
            )
            if (pct > 0f) {
                drawArc(
                    color = fillColor,
                    startAngle = -90f,
                    sweepAngle = 360f * pct,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${(pct * 100f).roundToInt()}%",
                style = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = SummaryTextStrong,
                ),
            )
            Text(
                text = stringResource(R.string.budget_summary_used).uppercase(),
                style = TextStyle(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SummaryTextDim,
                    letterSpacing = 0.5.sp,
                ),
            )
        }
    }
}

@Composable
private fun LabeledNumber(
    label: String,
    value: String,
    valueColor: Color,
    horizontalAlignment: Alignment.Horizontal,
    valueWeight: FontWeight = FontWeight.ExtraBold,
) {
    Column(horizontalAlignment = horizontalAlignment) {
        Text(
            text = label.uppercase(),
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = SummaryTextDim,
                letterSpacing = 0.5.sp,
            ),
        )
        Text(
            text = value,
            style = TextStyle(
                fontSize = 17.sp,
                fontWeight = valueWeight,
                color = valueColor,
            ),
        )
    }
}

@Composable
private fun StatusPill(overCount: Int, modifier: Modifier = Modifier) {
    val onTrack = overCount == 0
    val bg = if (onTrack) OnTrackPillBg else OverPillBg
    val iconTint = if (onTrack) GreenAccent else RedAccent
    val text = if (onTrack) {
        stringResource(R.string.budget_summary_on_track)
    } else {
        pluralStringResource(R.plurals.budget_summary_over_budget, overCount, overCount)
    }
    val icon = if (onTrack) Icons.Filled.CheckCircle else Icons.Filled.Warning
    Row(
        modifier = modifier
            .background(bg, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = iconTint,
        )
        Text(
            text = text,
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = SummaryTextStrong,
            ),
        )
    }
}
