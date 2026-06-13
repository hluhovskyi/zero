package com.hluhovskyi.zero.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.CategoryIconView
import com.hluhovskyi.zero.ui.DetailTopBar
import com.hluhovskyi.zero.ui.chart.BarChart
import com.hluhovskyi.zero.ui.chart.BarChartData
import com.hluhovskyi.zero.ui.chart.BarGroup
import com.hluhovskyi.zero.ui.chart.BarValue
import com.hluhovskyi.zero.ui.chart.LineChart
import com.hluhovskyi.zero.ui.chart.LineChartData
import com.hluhovskyi.zero.ui.common.toUi
import com.hluhovskyi.zero.ui.theme.ZeroTheme

internal class CashFlowReportViewProvider(
    private val viewModel: CashFlowReportViewModel,
    private val amountFormatter: AmountFormatter,
    private val imageLoader: ImageLoader,
) : ViewProvider {

    @Composable
    override fun View() {
        val state by viewModel.state.collectAsState(initial = CashFlowReportViewModel.State())
        Column(modifier = Modifier.fillMaxSize().background(ZeroTheme.colors.surface)) {
            DetailTopBar(
                title = stringResource(R.string.cashflow_title),
                onBack = { viewModel.perform(CashFlowReportViewModel.Action.Back) },
            )
            val report = state.report ?: return
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PeriodChip()
                HeroCard(report, state.currencySymbol, amountFormatter)
                ByMonthCard(report, state.currencySymbol, amountFormatter)
                SavingsRateCard(report)
                if (report.incomeSources.isNotEmpty()) {
                    IncomeSourcesSection(report, state.currencySymbol, amountFormatter, imageLoader)
                }
                ComparisonSection(report, state.currencySymbol, amountFormatter)
            }
        }
    }
}

@Composable
private fun PeriodChip() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            text = stringResource(R.string.analytics_period_last_6_months),
            modifier = Modifier
                .clip(RoundedCornerShape(11.dp))
                .background(ZeroTheme.colors.surfaceContainerLow)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            style = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = ZeroTheme.colors.primary),
        )
    }
}

@Composable
private fun HeroCard(report: CashFlowReportViewModel.Report, currencySymbol: String, formatter: AmountFormatter) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ZeroTheme.colors.islandBackground)
            .padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.analytics_net_cash_flow).uppercase(),
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = ZeroTheme.colors.islandContent.copy(alpha = 0.5f),
                letterSpacing = 1.1.sp,
            ),
        )
        Text(
            text = formatter.whole(report.net, currencySymbol),
            modifier = Modifier.padding(top = 5.dp, bottom = 14.dp),
            style = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = ZeroTheme.colors.islandContent),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroTile(
                label = stringResource(R.string.analytics_cash_in).uppercase(),
                value = formatter.whole(report.totalIn, currencySymbol),
                valueColor = ZeroTheme.colors.islandPositive,
                background = ZeroTheme.colors.islandPositive.copy(alpha = 0.12f),
                modifier = Modifier.weight(1f),
            )
            HeroTile(
                label = stringResource(R.string.analytics_cash_out).uppercase(),
                value = formatter.whole(report.totalOut, currencySymbol),
                valueColor = ZeroTheme.colors.islandNegative,
                background = ZeroTheme.colors.islandNegative.copy(alpha = 0.12f),
                modifier = Modifier.weight(1f),
            )
            HeroTile(
                label = stringResource(R.string.cashflow_saved).uppercase(),
                value = stringResource(R.string.analytics_percent, report.savingsRate),
                valueColor = ZeroTheme.colors.islandContent,
                background = ZeroTheme.colors.islandContent.copy(alpha = 0.06f),
                modifier = Modifier.weight(1f),
            )
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
private fun ByMonthCard(report: CashFlowReportViewModel.Report, currencySymbol: String, formatter: AmountFormatter) {
    val inColor = ZeroTheme.colors.islandPositive
    val outColor = ZeroTheme.colors.islandNegative
    ReportCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.cashflow_by_month), style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ZeroTheme.colors.onSurface))
            report.latest?.let { latest ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "${latest.label} ${stringResource(R.string.analytics_cash_in)} ${formatter.whole(Amount(latest.income.toDouble()), currencySymbol)}",
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = inColor),
                    )
                    Text(
                        text = "${stringResource(R.string.analytics_cash_out)} ${formatter.whole(Amount(latest.expense.toDouble()), currencySymbol)}",
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = outColor),
                    )
                }
            }
        }
        BarChart(
            data = BarChartData(
                report.months.map { BarGroup(it.label, listOf(BarValue(it.income, inColor), BarValue(it.expense, outColor))) },
            ),
            modifier = Modifier.fillMaxWidth(),
            barAreaHeight = 120.dp,
        )
        Text(
            text = stringResource(R.string.cashflow_tap_month),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            textAlign = TextAlign.Center,
            style = TextStyle(fontSize = 11.sp, color = ZeroTheme.colors.onSurfaceVariant),
        )
    }
}

@Composable
private fun SavingsRateCard(report: CashFlowReportViewModel.Report) {
    ReportCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.cashflow_savings_rate).uppercase(),
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
                        stringResource(R.string.analytics_percent, report.savingsRate),
                        style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = ZeroTheme.colors.primary),
                    )
                    Text(
                        " ${stringResource(R.string.cashflow_savings_avg)}",
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ZeroTheme.colors.onSurfaceVariant),
                    )
                }
            }
            Text(
                stringResource(R.string.cashflow_savings_range, report.savingsRateMin, report.savingsRateMax),
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
private fun IncomeSourcesSection(
    report: CashFlowReportViewModel.Report,
    currencySymbol: String,
    formatter: AmountFormatter,
    imageLoader: ImageLoader,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.cashflow_income_sources).uppercase())
        report.incomeSources.forEach { source ->
            IncomeRow(source, currencySymbol, formatter, imageLoader)
        }
    }
}

@Composable
private fun IncomeRow(
    source: CashFlowReportViewModel.IncomeShare,
    currencySymbol: String,
    formatter: AmountFormatter,
    imageLoader: ImageLoader,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ZeroTheme.colors.surfaceContainerLowest)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIconView(colorScheme = source.colorScheme.toUi(), size = 38.dp) { tint ->
            imageLoader.View(
                image = source.icon,
                modifier = Modifier.sizeIn(maxHeight = 22.dp, maxWidth = 22.dp),
                scale = ImageLoader.Scale.Crop,
                tint = tint,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(source.name, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = ZeroTheme.colors.onSurface))
                Text(formatter.whole(source.amount, currencySymbol), style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = ZeroTheme.colors.onSurface))
            }
            ShareBar(fraction = source.sharePercent / 100f, color = source.colorScheme.primary.toUi())
            Text(
                text = stringResource(R.string.cashflow_income_share, source.sharePercent),
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
private fun ComparisonSection(report: CashFlowReportViewModel.Report, currencySymbol: String, formatter: AmountFormatter) {
    Column {
        SectionLabel(stringResource(R.string.cashflow_vs_previous).uppercase())
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(ZeroTheme.colors.surfaceContainerLowest)
                .padding(horizontal = 16.dp),
        ) {
            MoneyComparisonRow(stringResource(R.string.cashflow_money_in), report.moneyIn, currencySymbol, formatter)
            HorizontalDivider(color = ZeroTheme.colors.surfaceContainer, thickness = 1.dp)
            MoneyComparisonRow(stringResource(R.string.cashflow_money_out), report.moneyOut, currencySymbol, formatter)
            HorizontalDivider(color = ZeroTheme.colors.surfaceContainer, thickness = 1.dp)
            RateComparisonRow(stringResource(R.string.cashflow_savings_rate), report.savingsRateChange)
        }
    }
}

@Composable
private fun MoneyComparisonRow(label: String, delta: CashFlowReportViewModel.Delta, currencySymbol: String, formatter: AmountFormatter) {
    ComparisonRow(
        label = label,
        value = formatter.whole(delta.now, currencySymbol),
        deltaText = formatter.signed(delta.magnitude, delta.isPositive, currencySymbol),
        isPositive = delta.isPositive,
    )
}

@Composable
private fun RateComparisonRow(label: String, change: CashFlowReportViewModel.RateChange) {
    ComparisonRow(
        label = label,
        value = stringResource(R.string.analytics_percent, change.nowPercent),
        deltaText = (if (change.isPositive) "+" else "−") + stringResource(R.string.cashflow_points, change.magnitudePoints),
        isPositive = change.isPositive,
    )
}

@Composable
private fun ComparisonRow(label: String, value: String, deltaText: String, isPositive: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = TextStyle(fontSize = 14.sp, color = ZeroTheme.colors.onSurface))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            Text(value, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ZeroTheme.colors.onSurface))
            Text(
                text = deltaText,
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPositive) ZeroTheme.colors.secondary else ZeroTheme.colors.error,
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

private fun AmountFormatter.whole(amount: Amount, currencySymbol: String): String = format(amount, currencySymbol, AmountFormatter.Style.Whole)

private fun AmountFormatter.signed(magnitude: Amount, isPositive: Boolean, currencySymbol: String): String = (if (isPositive) "+" else "−") + format(magnitude, currencySymbol, AmountFormatter.Style.Whole)
