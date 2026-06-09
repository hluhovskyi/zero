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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ui.DetailTopBar
import com.hluhovskyi.zero.ui.theme.ZeroTheme

// Each chart type is shown in the same three modes so they can be compared side by side.
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
    GalleryCard("Data") {
        LineChart(LineChartData(ChartMockData.netWorth6), ZeroTheme.colors.secondary, Modifier.fillMaxWidth().height(60.dp))
    }
    GalleryCard("1 point") {
        LineChart(LineChartData(listOf(42f)), ZeroTheme.colors.secondary, Modifier.fillMaxWidth().height(60.dp))
    }
    GalleryCard("Empty") { LineEmpty() }
}

@Composable
private fun BarSection() {
    SectionHeader("Bar chart (cash in / out)")
    GalleryCard("Data") { BarChart(flowBars(ChartMockData.flow6), Modifier.fillMaxWidth()) }
    GalleryCard("1 point") { BarChart(flowBars(ChartMockData.flow6.take(1)), Modifier.fillMaxWidth()) }
    GalleryCard("Empty") { BarChart(BarChartData(emptyList()), Modifier.fillMaxWidth()) }
}

@Composable
private fun DonutSection() {
    SectionHeader("Donut chart")
    GalleryCard("Data") { DonutBox(donut(ChartMockData.categories.size)) }
    GalleryCard("1 point") { DonutBox(donut(1)) }
    GalleryCard("Empty") { DonutEmpty() }
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
private fun LineEmpty() {
    Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
        LineChart(LineChartData(emptyList()), ZeroTheme.colors.secondary, Modifier.fillMaxWidth().height(60.dp))
        Text(
            text = "Not enough history to plot a trend",
            modifier = Modifier
                .background(ZeroTheme.colors.surfaceContainerLowest, RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp, vertical = 5.dp),
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ZeroTheme.colors.onSurfaceVariant),
        )
    }
}

@Composable
private fun DonutEmpty() {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        DonutChart(DonutChartData(emptyList()), Modifier.size(140.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("SPENT", style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ZeroTheme.colors.onSurfaceVariant))
                Text("$0", style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = ZeroTheme.colors.outline))
            }
        }
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
