# Charts Library (zero-ui) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add reusable line / bar / donut chart composables to `zero-ui`, plus a debug-only gallery (Settings → Developer → Charts) that renders each across data states with mock data.

**Architecture:** Compose `Canvas`/layout composables in `com.hluhovskyi.zero.ui.chart` (mirrors `budget/SummaryBar.kt`'s Canvas donut). Charts are color/data-agnostic (accept primitives + `Color`). Accent colors are added to `ZeroExtraColors` (theme package → lint-clean). The gallery is a dumb zero-ui view fed by an internal mock-data object; it is reached via a new `Destinations.Dev.Charts` route gated to debug builds through the existing `isDebugBuild` (`BuildConfig.DEBUG`) pattern.

**Tech Stack:** Kotlin, Jetpack Compose (foundation Canvas + Material 3), Dagger, JUnit.

Spec: `docs/superpowers/specs/2026-06-04-charts-lib-design.md`.

---

### Task 1: Chart data models + math helper

**Files:**
- Create: `zero-ui/src/main/java/com/hluhovskyi/zero/ui/chart/ChartData.kt`
- Create: `zero-ui/src/main/java/com/hluhovskyi/zero/ui/chart/ChartMath.kt`
- Test: `zero-ui/src/test/java/com/hluhovskyi/zero/ui/chart/ChartMathTest.kt`

- [ ] **Step 1: Write `ChartMathTest.kt`** (the only logic worth testing)

```kotlin
package com.hluhovskyi.zero.ui.chart

import org.junit.Assert.assertEquals
import org.junit.Test

class ChartMathTest {
    @Test fun `empty values normalize to empty`() {
        assertEquals(emptyList<Float>(), normalizeToFractions(emptyList()))
    }

    @Test fun `single value normalizes to mid`() {
        assertEquals(listOf(0.5f), normalizeToFractions(listOf(42f)))
    }

    @Test fun `flat series normalizes to mid for all`() {
        assertEquals(listOf(0.5f, 0.5f, 0.5f), normalizeToFractions(listOf(7f, 7f, 7f)))
    }

    @Test fun `min maps to 0 and max maps to 1`() {
        val out = normalizeToFractions(listOf(10f, 20f, 30f))
        assertEquals(0f, out.first(), 0.0001f)
        assertEquals(1f, out.last(), 0.0001f)
        assertEquals(0.5f, out[1], 0.0001f)
    }

    @Test fun `adaptive bar width shrinks as buckets grow`() {
        assertEquals(12, adaptiveBarWidthDp(6))
        assertEquals(10, adaptiveBarWidthDp(9))
        assertEquals(8, adaptiveBarWidthDp(12))
        assertEquals(5, adaptiveBarWidthDp(16))
        assertEquals(3, adaptiveBarWidthDp(24))
    }

    @Test fun `labels hidden beyond 14 buckets`() {
        assertEquals(true, shouldShowBarLabels(14))
        assertEquals(false, shouldShowBarLabels(15))
    }
}
```

- [ ] **Step 2: Run, expect FAIL** — `./gradlew :zero-ui:testDebugUnitTest --tests "*ChartMathTest*"` → unresolved references.

- [ ] **Step 3: Write `ChartMath.kt`**

```kotlin
package com.hluhovskyi.zero.ui.chart

/** y-fraction in [0,1] where 1 == series max. Empty → empty; single/flat → all 0.5 (centered). */
internal fun normalizeToFractions(values: List<Float>): List<Float> {
    if (values.isEmpty()) return emptyList()
    val min = values.min()
    val max = values.max()
    if (max == min) return List(values.size) { 0.5f }
    return values.map { (it - min) / (max - min) }
}

/** Bar width (dp) chosen so a grouped/single bar chart stays readable (~5–14 buckets). */
internal fun adaptiveBarWidthDp(bucketCount: Int): Int = when {
    bucketCount <= 6 -> 12
    bucketCount <= 9 -> 10
    bucketCount <= 12 -> 8
    bucketCount <= 16 -> 5
    else -> 3
}

internal fun adaptiveInnerGapDp(bucketCount: Int): Int = if (bucketCount <= 12) 3 else 1

internal fun shouldShowBarLabels(bucketCount: Int): Boolean = bucketCount <= 14
```

- [ ] **Step 4: Write `ChartData.kt`**

```kotlin
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

/** One x-axis bucket. [bars] holds 1 bar (single series) or 2 (e.g. cash in/out). */
@Immutable
data class BarGroup(val label: String, val bars: List<BarValue>)

@Immutable
data class BarChartData(val groups: List<BarGroup>)

@Immutable
data class DonutSegment(val value: Float, val color: Color)

@Immutable
data class DonutChartData(val segments: List<DonutSegment>)
```

- [ ] **Step 5: Run tests, expect PASS.** Then commit.

```bash
git add zero-ui/src/main/java/com/hluhovskyi/zero/ui/chart/ zero-ui/src/test/java/com/hluhovskyi/zero/ui/chart/
git commit -m "feat(charts): chart data models + math helper"
```

---

### Task 2: Chart accent tokens

**Files:**
- Modify: `zero-ui/src/main/java/com/hluhovskyi/zero/ui/theme/ZeroColors.kt`

Add five fields to `ZeroExtraColors` (after `welcomeCardLine`, before `isLight`), populate both
`LightZeroExtraColors` and `DarkZeroExtraColors`, and add matching read-through getters on
`ZeroColors`. Vivid accents are identical in both themes (they sit on the navy card in the design).
This file is in the theme package, so `Color(0x…)` literals are lint-allowlisted (no `@Suppress`).

- [ ] **Step 1:** In `data class ZeroExtraColors`, add:
```kotlin
    val chartCashIn: Color,
    val chartCashOut: Color,
    val chartHeroSurface: Color,
    val chartHeroContent: Color,
    val chartHeroContentDim: Color,
```

- [ ] **Step 2:** In `LightZeroExtraColors` and `DarkZeroExtraColors`, add the same values to both:
```kotlin
    chartCashIn = Color(0xFF5DDBA8),
    chartCashOut = Color(0xFFEBA07C),
    chartHeroSurface = Color(0xFF1A2E52),
    chartHeroContent = Color(0xFFFFFFFF),
    chartHeroContentDim = Color(0x80FFFFFF),
```

- [ ] **Step 3:** In `class ZeroColors`, add getters near the other `extras` getters:
```kotlin
    val chartCashIn get() = extras.chartCashIn
    val chartCashOut get() = extras.chartCashOut
    val chartHeroSurface get() = extras.chartHeroSurface
    val chartHeroContent get() = extras.chartHeroContent
    val chartHeroContentDim get() = extras.chartHeroContentDim
```

- [ ] **Step 4: Build + commit** — `./gradlew :zero-ui:compileDebugKotlin 2>&1 | tail -5`
```bash
git add zero-ui/src/main/java/com/hluhovskyi/zero/ui/theme/ZeroColors.kt
git commit -m "feat(charts): add chart accent color tokens to ZeroExtraColors"
```

---

### Task 3: LineChart

**Files:**
- Create: `zero-ui/src/main/java/com/hluhovskyi/zero/ui/chart/LineChart.kt`

- [ ] **Step 1: Write `LineChart.kt`**

```kotlin
package com.hluhovskyi.zero.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Sparkline: min/max-normalized polyline with a gradient area fill and an end-point dot.
 * Degenerate: empty → nothing; 1 point → dot only; flat series → centered line.
 */
@Composable
fun LineChart(
    data: LineChartData,
    lineColor: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 2.5.dp,
    showArea: Boolean = true,
    showEndpoint: Boolean = true,
) {
    val points = data.points
    val fractions = remember(points) { normalizeToFractions(points) }
    Canvas(modifier = modifier) {
        if (points.isEmpty()) return@Canvas
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
```

- [ ] **Step 2: Build + commit** — `./gradlew :zero-ui:compileDebugKotlin 2>&1 | tail -5`
```bash
git add zero-ui/src/main/java/com/hluhovskyi/zero/ui/chart/LineChart.kt
git commit -m "feat(charts): LineChart sparkline composable"
```

---

### Task 4: BarChart

**Files:**
- Create: `zero-ui/src/main/java/com/hluhovskyi/zero/ui/chart/BarChart.kt`

Layout-based (Row of weighted groups, bottom-aligned bars with explicit heights). Bar width and
inner gap come from the `adaptiveBarWidthDp`/`adaptiveInnerGapDp` helpers; labels hidden past 14.
Honours the revised design's "keep the chart frame" rule: a bucket with no bars → **dashed baseline
placeholder**; an all-zero series → **faint solid baseline tracks** (not colored stubs).

- [ ] **Step 1: Write `BarChart.kt`**

```kotlin
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
 */
@Composable
fun BarChart(
    data: BarChartData,
    modifier: Modifier = Modifier,
    barAreaHeight: Dp = 104.dp,
    showLabels: Boolean = true,
    emptyBarColor: Color = ZeroTheme.colors.surfaceContainer,
    placeholderColor: Color = ZeroTheme.colors.outlineVariant,
) {
    val groups = data.groups
    if (groups.isEmpty()) {
        Box(modifier.height(barAreaHeight))
        return
    }
    val maxValue = groups.flatMap { it.bars }.maxOfOrNull { it.value } ?: 0f
    val hasData = maxValue > 0f
    val n = groups.size
    val barW = adaptiveBarWidthDp(n).dp
    val innerGap = adaptiveInnerGapDp(n).dp
    val outerGap = if (n > 16) 1.dp else 4.dp
    val labelsVisible = showLabels && shouldShowBarLabels(n)
    Column(modifier) {
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
                                        .clip(RoundedCornerShape(3.dp))
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
```

- [ ] **Step 2: Build + commit** — `./gradlew :zero-ui:compileDebugKotlin 2>&1 | tail -5`
```bash
git add zero-ui/src/main/java/com/hluhovskyi/zero/ui/chart/BarChart.kt
git commit -m "feat(charts): BarChart grouped/single composable"
```

---

### Task 5: DonutChart

**Files:**
- Create: `zero-ui/src/main/java/com/hluhovskyi/zero/ui/chart/DonutChart.kt`

Multi-segment ring over a full track, center `content` slot. Mirrors `budget/SummaryBar.kt`'s
`drawArc` approach but draws one arc per segment. Default `trackColor` = `surfaceContainer`.

- [ ] **Step 1: Write `DonutChart.kt`**

```kotlin
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hluhovskyi.zero.ui.theme.ZeroTheme

/**
 * Donut: one arc per segment (swept by value/total) over a full track ring, with a centered
 * [content] slot. Degenerate: total 0 / empty → track ring only (center can hold an empty label).
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
            drawArc(trackColor, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            if (total <= 0f) return@Canvas
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
```

- [ ] **Step 2: Build + commit** — `./gradlew :zero-ui:compileDebugKotlin 2>&1 | tail -5`
```bash
git add zero-ui/src/main/java/com/hluhovskyi/zero/ui/chart/DonutChart.kt
git commit -m "feat(charts): DonutChart multi-segment composable"
```

---

### Task 6: Mock data + gallery screen

**Files:**
- Create: `zero-ui/src/main/java/com/hluhovskyi/zero/ui/chart/ChartMockData.kt`
- Create: `zero-ui/src/main/java/com/hluhovskyi/zero/ui/chart/ChartsGalleryScreen.kt`

- [ ] **Step 1: Write `ChartMockData.kt`** (values from `analytics-kit.jsx` / `edgecases.jsx`; category
  swatches are dark-mapped entity hexes → file-level `@Suppress`).

```kotlin
@file:Suppress("ZeroThemeBypass")

package com.hluhovskyi.zero.ui.chart

import androidx.compose.ui.graphics.Color

/** Mock data for the debug Charts gallery. Numbers taken straight from the Analytics design. */
internal object ChartMockData {

    // Cash flow (in, out) per month — oldest → newest.
    val flow6: List<Triple<String, Float, Float>> = listOf(
        Triple("Nov", 5050f, 3980f),
        Triple("Dec", 5600f, 4820f),
        Triple("Jan", 5050f, 3640f),
        Triple("Feb", 5050f, 3910f),
        Triple("Mar", 5300f, 4180f),
        Triple("Apr", 5050f, 3760f),
    )

    val flow12: List<Triple<String, Float, Float>> = listOf(
        Triple("May", 4900f, 3700f), Triple("Jun", 5200f, 3850f),
        Triple("Jul", 4950f, 4020f), Triple("Aug", 5050f, 3680f),
        Triple("Sep", 5250f, 3990f), Triple("Oct", 5000f, 4500f),
        Triple("Nov", 5050f, 4900f), Triple("Dec", 5600f, 4820f),
        Triple("Jan", 5050f, 3640f), Triple("Feb", 5050f, 3910f),
        Triple("Mar", 5300f, 4180f), Triple("Apr", 5050f, 3760f),
    )

    // 1 month → weekly buckets (biweekly pay → lumpy income).
    val weekly: List<Triple<String, Float, Float>> = listOf(
        Triple("W1", 2525f, 1040f),
        Triple("W2", 0f, 880f),
        Triple("W3", 2525f, 1180f),
        Triple("W4", 0f, 640f),
        Triple("W5", 0f, 210f),
    )

    val netWorth6: List<Float> = listOf(18200f, 19000f, 20400f, 21500f, 22600f, 23900f)
    val netWorth12: List<Float> = listOf(
        15800f, 16200f, 16050f, 16900f, 17500f, 17300f,
        18200f, 19000f, 20400f, 21500f, 22600f, 23900f,
    )

    // Donut category split (name, dark-mapped swatch, spend). Dark-mapped because the app is dark.
    val categories: List<Triple<String, Color, Float>> = listOf(
        Triple("Housing", Color(0xFF9FAAE5), 9000f),
        Triple("Groceries", Color(0xFF9CC0FF), 2450f),
        Triple("Food & Drink", Color(0xFFFF9AA0), 2210f),
        Triple("Transport", Color(0xFFFFB272), 1580f),
        Triple("Shopping", Color(0xFFF58FB1), 1680f),
        Triple("Utilities", Color(0xFFF2C674), 1260f),
        Triple("Other", Color(0xFFBABCC6), 4090f),
    )
}
```

- [ ] **Step 2: Write `ChartsGalleryScreen.kt`** — scrollable, `DetailTopBar` + sections. Each chart
  type rendered across states. In/out colors pulled from `ZeroTheme.colors.chartCashIn/Out`; donut
  from mock category swatches.

```kotlin
package com.hluhovskyi.zero.ui.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ui.DetailTopBar
import com.hluhovskyi.zero.ui.theme.ZeroTheme

@Composable
fun ChartsGalleryScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(ZeroTheme.colors.surface),
    ) {
        DetailTopBar(title = "Charts", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            LineSection()
            BarSection()
            DonutSection()
        }
    }
}

@Composable
private fun LineSection() {
    SectionHeader("Line chart")
    GalleryCard("No data") { LineChart(LineChartData(emptyList()), ZeroTheme.colors.secondary, Modifier.fillMaxWidth().height(56.dp)) }
    GalleryCard("1 point") { LineChart(LineChartData(listOf(42f)), ZeroTheme.colors.secondary, Modifier.fillMaxWidth().height(56.dp)) }
    GalleryCard("6 months") { LineChart(LineChartData(ChartMockData.netWorth6), ZeroTheme.colors.secondary, Modifier.fillMaxWidth().height(60.dp)) }
    GalleryCard("12 months") { LineChart(LineChartData(ChartMockData.netWorth12), ZeroTheme.colors.secondary, Modifier.fillMaxWidth().height(60.dp)) }
}

@Composable
private fun BarSection() {
    SectionHeader("Bar chart (cash in / out)")
    GalleryCard("1 month → weekly") { BarChart(flowBars(ChartMockData.weekly), Modifier.fillMaxWidth()) }
    GalleryCard("6 months") { BarChart(flowBars(ChartMockData.flow6), Modifier.fillMaxWidth()) }
    GalleryCard("12 months") { BarChart(flowBars(ChartMockData.flow12), Modifier.fillMaxWidth()) }
    GalleryCard("No history (ghost + message)") { BarsNoHistory() }
    GalleryCard("Partial history (real + dashed)") { BarsPartial() }
    GalleryCard("Zero in period ($0 / $0)") { BarsZeroPeriod() }
    // Showcase: bars on the navy hero surface (how Insights will frame it).
    HeroCard { BarChart(flowBars(ChartMockData.flow6), Modifier.fillMaxWidth()) }
}

/** Builds grouped cash in/out bars from (label, in, out) rows using the theme accent colors. */
@Composable
private fun flowBars(rows: List<Triple<String, Float, Float>>): BarChartData {
    val inC = ZeroTheme.colors.chartCashIn
    val outC = ZeroTheme.colors.chartCashOut
    return BarChartData(rows.map { BarGroup(it.first, listOf(BarValue(it.second, inC), BarValue(it.third, outC))) })
}

@Composable
private fun BarsNoHistory() {
    val inC = ZeroTheme.colors.chartCashIn
    val outC = ZeroTheme.colors.chartCashOut
    val ghosts = listOf(0.5f, 0.8f, 0.4f, 0.65f, 0.55f, 0.9f)
    val data = BarChartData(ghosts.map { BarGroup("", listOf(BarValue(it, inC), BarValue(it * 0.7f, outC))) })
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        BarChart(data, Modifier.fillMaxWidth().alpha(0.18f), showLabels = false)
        Text(
            text = "Waiting for your first transactions",
            modifier = Modifier
                .background(ZeroTheme.colors.surfaceContainerLowest, RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp, vertical = 5.dp),
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ZeroTheme.colors.onSurfaceVariant),
        )
    }
}

@Composable
private fun BarsPartial() {
    val inC = ZeroTheme.colors.chartCashIn
    val outC = ZeroTheme.colors.chartCashOut
    val groups = listOf(
        BarGroup("Nov", emptyList()),
        BarGroup("Dec", emptyList()),
        BarGroup("Jan", emptyList()),
        BarGroup("Feb", emptyList()),
        BarGroup("Mar", listOf(BarValue(5050f, inC), BarValue(4180f, outC))),
        BarGroup("Apr", listOf(BarValue(5050f, inC), BarValue(3760f, outC))),
    )
    BarChart(BarChartData(groups), Modifier.fillMaxWidth())
}

@Composable
private fun BarsZeroPeriod() {
    val inC = ZeroTheme.colors.chartCashIn
    val outC = ZeroTheme.colors.chartCashOut
    val months = listOf("Nov", "Dec", "Jan", "Feb", "Mar", "Apr")
    val data = BarChartData(months.map { BarGroup(it, listOf(BarValue(0f, inC), BarValue(0f, outC))) })
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        BarChart(data, Modifier.fillMaxWidth())
        Text(
            text = "$0 in  ·  $0 out",
            modifier = Modifier
                .background(ZeroTheme.colors.surfaceContainerLowest, RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp, vertical = 5.dp),
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ZeroTheme.colors.onSurfaceVariant),
        )
    }
}

@Composable
private fun DonutSection() {
    fun donut(n: Int) = DonutChartData(ChartMockData.categories.take(n).map { DonutSegment(it.third, it.second) })
    SectionHeader("Donut chart")
    GalleryCard("No data") { DonutBox(DonutChartData(emptyList())) }
    GalleryCard("1 segment") { DonutBox(donut(1)) }
    GalleryCard("3 segments") { DonutBox(donut(3)) }
    GalleryCard("All categories") { DonutBox(donut(ChartMockData.categories.size)) }
}

@Composable
private fun DonutBox(data: DonutChartData) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        DonutChart(data, Modifier.size(140.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("SPENT", style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ZeroTheme.colors.onSurfaceVariant))
                Text(
                    text = data.segments.fold(0f) { a, s -> a + s.value }.toInt().toString(),
                    style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = ZeroTheme.colors.primary),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = ZeroTheme.colors.onSurface),
    )
}

@Composable
private fun GalleryCard(caption: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ZeroTheme.colors.surfaceContainerLowest, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(caption, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ZeroTheme.colors.onSurfaceVariant))
        content()
    }
}

@Composable
private fun HeroCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ZeroTheme.colors.chartHeroSurface, RoundedCornerShape(22.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("NET CASH FLOW", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ZeroTheme.colors.chartHeroContentDim))
        content()
    }
}
```

- [ ] **Step 3: Build + commit** — `./gradlew :zero-ui:compileDebugKotlin 2>&1 | tail -5`
```bash
git add zero-ui/src/main/java/com/hluhovskyi/zero/ui/chart/ChartMockData.kt zero-ui/src/main/java/com/hluhovskyi/zero/ui/chart/ChartsGalleryScreen.kt
git commit -m "feat(charts): mock data + debug Charts gallery screen"
```

---

### Task 7: Wire Developer section + nav (debug only)

Threads `BuildConfig.DEBUG` into Settings and adds the route. Boilerplate follows existing analogs —
keep changes minimal and mirror them exactly.

**7a. Destination** — Modify `app/.../activity/navigation/Destinations.kt`. After `object Settings`:
```kotlin
    sealed interface Dev : Destination {
        object Charts : Dev, Destination by destinationOf("dev/charts")
    }
```

**7b. Handler** — Create `zero-core/.../settings/OnDevChartsSelectedHandler.kt`, copy
`OnBackupSelectedHandler.kt` verbatim and rename the type (keep its `Noop` object).

**7c. SettingsComponent** — Modify `zero-core/.../settings/SettingsComponent.kt`:
- Add `@BindsInstance fun onDevChartsSelectedHandler(handler: OnDevChartsSelectedHandler): Builder`
  and `@BindsInstance fun isDebugBuild(isDebug: Boolean): Builder` to `Builder` (mirror the existing
  `onBackupSelectedHandler` binding).
- In `companion.builder(...)` default-bind `.onDevChartsSelectedHandler(OnDevChartsSelectedHandler.Noop).isDebugBuild(false)`.
- Thread both into the `viewModel(...)` `@Provides` and the `DefaultSettingsViewModel(...)` ctor call.

**7d. SettingsViewModel** — Modify `zero-core/.../settings/SettingsViewModel.kt`:
- Add `object OpenDevCharts : Action`.
- Add `val showDeveloperOptions: Boolean = false` to `State`.

**7e. DefaultSettingsViewModel** — Modify `zero-core/.../settings/DefaultSettingsViewModel.kt`:
- Add ctor params `onDevChartsSelected: OnDevChartsSelectedHandler` and `isDebugBuild: Boolean`.
- Initialize `MutableStateFlow(SettingsViewModel.State(showDeveloperOptions = isDebugBuild))`.
- Handle `Action.OpenDevCharts -> coroutineScope.launch(Dispatchers.Main) { onDevChartsSelected.onSelected() }`
  (mirror `OpenBackup`).

**7f. SettingsViewProvider** — Modify `zero-core/.../settings/SettingsViewProvider.kt`. After the
Security section `item {}` and before the trailing `Spacer` item, add (mirrors the existing
`MoreSection`/`MoreRow` usage):
```kotlin
            if (state.showDeveloperOptions) {
                item {
                    MoreSection(title = stringResource(R.string.settings_section_developer).uppercase()) {
                        MoreRow(
                            icon = Icons.Outlined.BarChart,
                            primaryText = stringResource(R.string.settings_dev_charts),
                            secondaryText = stringResource(R.string.settings_dev_charts_description),
                            onClick = { viewModel.perform(SettingsViewModel.Action.OpenDevCharts) },
                        )
                    }
                }
            }
```
Add import `androidx.compose.material.icons.outlined.BarChart`.

**7g. Strings** — Add to `zero-core/src/main/res/values/strings.xml` (find the settings block):
```xml
    <string name="settings_section_developer">Developer</string>
    <string name="settings_dev_charts">Charts</string>
    <string name="settings_dev_charts_description">Preview chart components</string>
```

**7h. App wiring** — Modify `app/.../activity/screens/MainActivityScreenComponent.kt`:
- In `settingsNavigationEntry` (the `buildable(Destinations.Settings)` block, ~line 954) add to the
  builder chain: `.isDebugBuild(BuildConfig.DEBUG)` and
  `.onDevChartsSelectedHandler { navigator.navigateTo(Destinations.Dev.Charts) }`. Add
  `import com.hluhovskyi.zero.BuildConfig` if absent.
- Add a new `@Provides @IntoSet @MainActivityScreenScope` nav entry, structured like
  `categoryNavigationEntry` (the `composable` one, ~line 507):
```kotlin
        @Provides
        @IntoSet
        @MainActivityScreenScope
        fun devChartsNavigationEntry(
            navigatorScope: NavigatorScope,
        ): NavigatorEntry = navigatorScope.composable(Destinations.Dev.Charts) {
            ChartsGalleryScreen(onBack = { navigator.back() })
        }
```
Add imports: `com.hluhovskyi.zero.ui.chart.ChartsGalleryScreen` and (if absent)
`com.hluhovskyi.zero.activity.navigation.back`.

- [ ] **Step 1:** Apply 7a–7h.
- [ ] **Step 2: Build** — `./gradlew :app:compileDebugKotlin 2>&1 | tail -20`. Fix any signature mismatches.
- [ ] **Step 3: Commit**
```bash
git add -A
git commit -m "feat(charts): wire debug Settings → Developer → Charts gallery"
```

---

### Task 8: Verification

- [ ] **Step 1: Tests + lint (one invocation)** — `./gradlew testDebugUnitTest lintDebug 2>&1 | tail -25`.
  Both must pass; lint confirms no `ZeroThemeBypass` violation.
- [ ] **Step 2: Format** — `./gradlew spotlessApply 2>&1 | tail -5`, then commit if it changed files.
- [ ] **Step 3: UI** — install debug, open Settings → Developer → Charts, and use
  `zero-project:android-ui-inspector` to confirm each chart renders in every state (line: none/1/6/12;
  bars: none/weekly/6/12 + navy hero; donut: none/1/3/all). Screenshot. A chart is not done until the
  inspector confirms it on device.

---

## Self-Review

- **Spec coverage:** line/bar/donut composables (T3/4/5), data models (T1), accent tokens (T2),
  mock data (T6), gallery with states (T6), debug Settings section + nav (T7), tests+lint+UI (T8).
  All spec sections map to a task.
- **Type consistency:** `LineChartData(points,labels)`, `BarChartData(groups)` / `BarGroup(label,bars)`
  / `BarValue(value,color)`, `DonutChartData(segments)` / `DonutSegment(value,color)` used uniformly
  across T1/T3/T4/T5/T6. Token getters `chartCashIn/chartCashOut/chartHeroSurface/chartHeroContent/
  chartHeroContentDim` defined in T2, consumed in T6. `OnDevChartsSelectedHandler`, `OpenDevCharts`,
  `showDeveloperOptions`, `isDebugBuild` consistent across T7.
- **Placeholders:** none — mock arrays carry real values; wiring tasks name exact files + analogs.
</content>
