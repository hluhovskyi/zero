// Debug-only chart gallery (Settings → Developer → Charts). Its labels are dev-facing scaffolding,
// never shipped to users, so they stay inline rather than in the localized string table.
@file:Suppress("HardcodedComposableString")

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
    HeroCard { BarChart(flowBars(ChartMockData.flow6), Modifier.fillMaxWidth()) }
}

@Composable
private fun DonutSection() {
    SectionHeader("Donut chart")
    GalleryCard("No data") { DonutBox(DonutChartData(emptyList())) }
    GalleryCard("1 segment") { DonutBox(donut(1)) }
    GalleryCard("3 segments") { DonutBox(donut(3)) }
    GalleryCard("All categories") { DonutBox(donut(ChartMockData.categories.size)) }
}

/** Builds grouped cash in/out bars from (label, in, out) rows using the theme accent colors. */
@Composable
private fun flowBars(rows: List<Triple<String, Float, Float>>): BarChartData {
    val inC = ZeroTheme.colors.chartCashIn
    val outC = ZeroTheme.colors.chartCashOut
    return BarChartData(rows.map { BarGroup(it.first, listOf(BarValue(it.second, inC), BarValue(it.third, outC))) })
}

private fun donut(n: Int): DonutChartData = DonutChartData(ChartMockData.categories.take(n).map { DonutSegment(it.third, it.second) })

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
