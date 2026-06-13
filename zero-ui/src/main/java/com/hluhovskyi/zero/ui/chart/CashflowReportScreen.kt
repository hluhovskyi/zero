// Debug-only cash-flow report (Settings → Developer → Cash flow), rebuilt from the Analytics
// Exploration design. Dev-facing labels stay inline rather than in the localized string table.
@file:Suppress("HardcodedComposableString")

package com.hluhovskyi.zero.ui.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ui.CategoryIconView
import com.hluhovskyi.zero.ui.DetailTopBar
import com.hluhovskyi.zero.ui.theme.ZeroTheme
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun CashflowReportScreen(onBack: () -> Unit) {
    val report = remember { cashflowReport() }
    Column(
        modifier = Modifier.fillMaxSize().background(ZeroTheme.colors.surface),
    ) {
        DetailTopBar(title = "Cash flow", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PeriodChip()
            HeroCard(report)
            ByMonthCard(report)
            SavingsRateCard(report)
            IncomeSourcesSection(report)
            ComparisonSection(report)
        }
    }
}

@Composable
private fun PeriodChip() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            text = "Last 6 months",
            modifier = Modifier
                .clip(RoundedCornerShape(11.dp))
                .background(ZeroTheme.colors.surfaceContainerLow)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            style = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = ZeroTheme.colors.primary),
        )
    }
}

@Composable
private fun HeroCard(report: CashflowReport) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ZeroTheme.colors.islandBackground)
            .padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 16.dp),
    ) {
        Text(
            text = "NET CASH FLOW",
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = ZeroTheme.colors.islandContent.copy(alpha = 0.5f),
                letterSpacing = 1.1.sp,
            ),
        )
        Text(
            text = fmtSign(report.net),
            modifier = Modifier.padding(top = 5.dp, bottom = 14.dp),
            style = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = ZeroTheme.colors.islandContent),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroTile("IN", fmt0(report.totalIn), ZeroTheme.colors.islandPositive, ZeroTheme.colors.islandPositive.copy(alpha = 0.12f), Modifier.weight(1f))
            HeroTile("OUT", fmt0(report.totalOut), ZeroTheme.colors.islandNegative, ZeroTheme.colors.islandNegative.copy(alpha = 0.12f), Modifier.weight(1f))
            HeroTile("SAVED", "${report.savingsRate}%", ZeroTheme.colors.islandContent, ZeroTheme.colors.islandContent.copy(alpha = 0.06f), Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeroTile(label: String, value: String, valueColor: Color, background: Color, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(bottom = 3.dp),
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = ZeroTheme.colors.islandContent.copy(alpha = 0.5f),
                letterSpacing = 0.8.sp,
            ),
        )
        Text(value, style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = valueColor))
    }
}

@Composable
private fun ByMonthCard(report: CashflowReport) {
    val inColor = ZeroTheme.colors.islandPositive
    val outColor = ZeroTheme.colors.islandNegative
    ReportCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("By month", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ZeroTheme.colors.onSurface))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "${report.latest.label} in ${fmt0(report.latest.moneyIn)}",
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = inColor),
                )
                Text(
                    "out ${fmt0(report.latest.moneyOut)}",
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = outColor),
                )
            }
        }
        BarChart(
            data = BarChartData(
                report.months.map { BarGroup(it.label, listOf(BarValue(it.moneyIn, inColor), BarValue(it.moneyOut, outColor))) },
            ),
            modifier = Modifier.fillMaxWidth(),
            barAreaHeight = 120.dp,
        )
        Text(
            text = "Tap a month for its breakdown",
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            textAlign = TextAlign.Center,
            style = TextStyle(fontSize = 11.sp, color = ZeroTheme.colors.onSurfaceVariant),
        )
    }
}

@Composable
private fun SavingsRateCard(report: CashflowReport) {
    ReportCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                Text(
                    text = "SAVINGS RATE",
                    modifier = Modifier.padding(bottom = 4.dp),
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ZeroTheme.colors.onSurfaceVariant,
                        letterSpacing = 0.7.sp,
                    ),
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${report.savingsRate}%",
                        style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = ZeroTheme.colors.primary),
                    )
                    Text(
                        " avg",
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ZeroTheme.colors.onSurfaceVariant),
                    )
                }
            }
            Text(
                "${report.savingsRateMin}–${report.savingsRateMax}% range",
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ZeroTheme.colors.onSurfaceVariant),
            )
        }
        LineChart(
            data = LineChartData(report.savingsTrend),
            lineColor = ZeroTheme.colors.secondary,
            modifier = Modifier.fillMaxWidth().height(50.dp),
        )
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            report.months.forEach { month ->
                Text(
                    text = month.label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = TextStyle(fontSize = 10.sp, color = ZeroTheme.colors.onSurfaceVariant),
                )
            }
        }
    }
}

@Composable
private fun IncomeSourcesSection(report: CashflowReport) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("INCOME SOURCES")
        report.incomeSources.forEachIndexed { index, source ->
            IncomeRow(source, incomeIcon(index))
        }
    }
}

@Composable
private fun IncomeRow(source: IncomeShare, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ZeroTheme.colors.surfaceContainerLowest)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIconView(color = source.swatch, size = 38.dp) {
            Icon(imageVector = icon, contentDescription = null, tint = ZeroTheme.colors.surface)
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(source.name, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = ZeroTheme.colors.onSurface))
                Text(fmt0(source.amount), style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = ZeroTheme.colors.onSurface))
            }
            ShareBar(fraction = source.sharePercent / 100f, color = source.swatch)
            Text(
                text = "${source.sharePercent}% of income",
                modifier = Modifier.padding(top = 6.dp),
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = ZeroTheme.colors.onSurfaceVariant),
            )
        }
    }
}

@Composable
private fun ShareBar(fraction: Float, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(ZeroTheme.colors.surfaceContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        ) {}
    }
}

@Composable
private fun ComparisonSection(report: CashflowReport) {
    Column {
        SectionLabel("VS PREVIOUS 6 MONTHS")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(ZeroTheme.colors.surfaceContainerLowest)
                .padding(horizontal = 16.dp),
        ) {
            report.comparisons.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(color = ZeroTheme.colors.surfaceContainer, thickness = 1.dp)
                ComparisonRow(row)
            }
        }
    }
}

@Composable
private fun ComparisonRow(row: PeriodComparison) {
    val up = row.delta >= 0f
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(row.label, style = TextStyle(fontSize = 14.sp, color = ZeroTheme.colors.onSurface))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                text = if (row.isMoney) fmt0(row.nowValue) else "${row.nowValue.roundToInt()}%",
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ZeroTheme.colors.onSurface),
            )
            Text(
                text = if (row.isMoney) fmtSign(row.delta) else "${if (up) "+" else ""}${row.delta.roundToInt()} pts",
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (up) ZeroTheme.colors.secondary else ZeroTheme.colors.error,
                ),
            )
        }
    }
}

@Composable
private fun ReportCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ZeroTheme.colors.surfaceContainerLowest)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        content()
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 2.dp),
        style = TextStyle(
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = ZeroTheme.colors.onSurfaceVariant,
            letterSpacing = 1.sp,
        ),
    )
}

private fun incomeIcon(index: Int): ImageVector = when (index) {
    0 -> Icons.Outlined.Payments
    1 -> Icons.Outlined.Work
    else -> Icons.Outlined.Savings
}

private fun fmt0(value: Float): String = "$" + "%,d".format(value.roundToInt())

private fun fmtSign(value: Float): String = (if (value >= 0f) "+$" else "–$") + "%,d".format(abs(value).roundToInt())
