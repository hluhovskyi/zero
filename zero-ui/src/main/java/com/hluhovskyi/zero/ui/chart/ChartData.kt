package com.hluhovskyi.zero.ui.chart

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** One line series. [labels] optional, parallel to [points]. */
@Immutable
data class LineChartData(
    val points: List<Float>,
    val labels: List<String> = emptyList(),
)

@Immutable
data class BarValue(val value: Float, val color: Color)

/**
 * One x-axis bucket. [bars] holds 1 bar (single series) or 2 (e.g. cash in/out); empty = no data.
 * [topLabel] is an optional value caption rendered above the bar(s) (e.g. "$290" on a trend chart).
 */
@Immutable
data class BarGroup(val label: String, val bars: List<BarValue>, val topLabel: String? = null)

@Immutable
data class BarChartData(val groups: List<BarGroup>)

@Immutable
data class DonutSegment(val value: Float, val color: Color)

@Immutable
data class DonutChartData(val segments: List<DonutSegment>)
