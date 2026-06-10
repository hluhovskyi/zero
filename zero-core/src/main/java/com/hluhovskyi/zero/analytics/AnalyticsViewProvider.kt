package com.hluhovskyi.zero.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.CategoryIconView
import com.hluhovskyi.zero.ui.chart.BarChart
import com.hluhovskyi.zero.ui.chart.BarChartData
import com.hluhovskyi.zero.ui.chart.BarGroup
import com.hluhovskyi.zero.ui.chart.BarValue
import com.hluhovskyi.zero.ui.chart.DonutChart
import com.hluhovskyi.zero.ui.chart.DonutChartData
import com.hluhovskyi.zero.ui.chart.DonutSegment
import com.hluhovskyi.zero.ui.common.toUi
import com.hluhovskyi.zero.ui.theme.ZeroTheme

internal class AnalyticsViewProvider(
    private val viewModel: AnalyticsViewModel,
    private val amountFormatter: AmountFormatter,
    private val imageLoader: ImageLoader,
) : ViewProvider {

    @Composable
    override fun View() {
        val state by viewModel.state.collectAsState(initial = AnalyticsViewModel.State())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ZeroTheme.colors.surface)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Header()
            state.cashFlow?.let { FlowCard(it, state.currencySymbol, amountFormatter) }
            state.breakdown?.let { breakdown ->
                BreakdownCard(
                    breakdown = breakdown,
                    currencySymbol = state.currencySymbol,
                    amountFormatter = amountFormatter,
                    imageLoader = imageLoader,
                    onCategory = { viewModel.perform(AnalyticsViewModel.Action.SelectCategory(it)) },
                    onSeeAll = { viewModel.perform(AnalyticsViewModel.Action.SeeAllCategories) },
                )
            }
        }
    }
}

@Composable
private fun Header() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.analytics_title),
            style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = ZeroTheme.colors.primary),
        )
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
private fun FlowCard(
    cashFlow: AnalyticsViewModel.CashFlow,
    currencySymbol: String,
    amountFormatter: AmountFormatter,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(ZeroTheme.colors.islandBackground)
            .padding(20.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(
                    text = stringResource(R.string.analytics_net_cash_flow),
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ZeroTheme.colors.islandContent.copy(alpha = 0.5f)),
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = amountFormatter.format(cashFlow.net, currencySymbol),
                    style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = ZeroTheme.colors.islandContent),
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                FlowLegend(ZeroTheme.colors.islandPositive, stringResource(R.string.analytics_cash_in), amountFormatter.format(cashFlow.totalIn, currencySymbol))
                FlowLegend(ZeroTheme.colors.islandNegative, stringResource(R.string.analytics_cash_out), amountFormatter.format(cashFlow.totalOut, currencySymbol))
            }
        }
        Spacer(Modifier.height(18.dp))
        BarChart(
            data = BarChartData(
                cashFlow.bars.map {
                    BarGroup(
                        label = it.label,
                        bars = listOf(
                            BarValue(it.income, ZeroTheme.colors.islandPositive),
                            BarValue(it.expense, ZeroTheme.colors.islandNegative),
                        ),
                    )
                },
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FlowLegend(color: Color, label: String, amount: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(label, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ZeroTheme.colors.islandContent.copy(alpha = 0.5f)))
        Text(amount, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ZeroTheme.colors.islandContent))
    }
}

@Composable
private fun BreakdownCard(
    breakdown: AnalyticsViewModel.Breakdown,
    currencySymbol: String,
    amountFormatter: AmountFormatter,
    imageLoader: ImageLoader,
    onCategory: (Id.Known) -> Unit,
    onSeeAll: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(ZeroTheme.colors.surfaceContainerLowest)
            .padding(18.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.analytics_by_category), style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = ZeroTheme.colors.onSurface))
            Text(stringResource(R.string.analytics_vs_prior), style = TextStyle(fontSize = 12.sp, color = ZeroTheme.colors.onSurfaceVariant))
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DonutChart(
                data = DonutChartData(breakdown.donut.map { DonutSegment(it.value, it.colorScheme?.primary?.toUi() ?: ZeroTheme.colors.outlineVariant) }),
                modifier = Modifier.size(140.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.analytics_spent), style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ZeroTheme.colors.onSurfaceVariant))
                    Text(
                        text = amountFormatter.format(breakdown.totalSpent, currencySymbol, AmountFormatter.Style.Whole),
                        style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = ZeroTheme.colors.primary),
                    )
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                breakdown.legend.forEach { LegendRow(it) }
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(ZeroTheme.colors.surfaceContainer))
        breakdown.rows.forEach { row ->
            CategoryRow(row, currencySymbol, amountFormatter, imageLoader, onCategory)
        }
        SeeAllRow(breakdown.categoryCount, onSeeAll)
    }
}

@Composable
private fun LegendRow(item: AnalyticsViewModel.LegendItem) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(item.colorScheme?.primary?.toUi() ?: ZeroTheme.colors.outlineVariant))
        Text(
            text = item.name ?: stringResource(R.string.analytics_other),
            modifier = Modifier.weight(1f),
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ZeroTheme.colors.onSurface),
        )
        Text(
            text = stringResource(R.string.analytics_percent, item.sharePercent),
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ZeroTheme.colors.onSurface),
        )
    }
}

@Composable
private fun CategoryRow(
    row: AnalyticsViewModel.Row,
    currencySymbol: String,
    amountFormatter: AmountFormatter,
    imageLoader: ImageLoader,
    onCategory: (Id.Known) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCategory(row.categoryId) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        CategoryIconView(colorScheme = row.colorScheme.toUi(), size = 38.dp) { tint ->
            imageLoader.View(
                image = row.icon,
                modifier = Modifier.sizeIn(maxHeight = 22.dp, maxWidth = 22.dp),
                scale = ImageLoader.Scale.Crop,
                tint = tint,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.name,
                    modifier = Modifier.weight(1f),
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ZeroTheme.colors.onSurface),
                )
                TrendChip(row.trend)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = amountFormatter.format(row.amount, currencySymbol),
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = ZeroTheme.colors.onSurface),
                )
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(ZeroTheme.colors.surfaceContainer)) {
                Box(
                    Modifier
                        .fillMaxWidth(row.barFraction)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(row.colorScheme.primary.toUi()),
                )
            }
        }
    }
}

@Composable
private fun TrendChip(trend: AnalyticsViewModel.Trend) {
    when (trend) {
        is AnalyticsViewModel.Trend.New ->
            Text(stringResource(R.string.analytics_trend_new), style = TextStyle(fontSize = 11.sp, color = ZeroTheme.colors.outline))

        is AnalyticsViewModel.Trend.Flat ->
            Text(stringResource(R.string.analytics_trend_flat), style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = ZeroTheme.colors.onSurfaceVariant))

        is AnalyticsViewModel.Trend.Up ->
            TrendArrow(Icons.Filled.ArrowDropUp, ZeroTheme.colors.error, trend.percent)

        is AnalyticsViewModel.Trend.Down ->
            TrendArrow(Icons.Filled.ArrowDropDown, ZeroTheme.colors.secondary, trend.percent)
    }
}

@Composable
private fun TrendArrow(icon: ImageVector, color: Color, percent: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(
            text = stringResource(R.string.analytics_percent, percent),
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color),
        )
    }
}

@Composable
private fun SeeAllRow(categoryCount: Int, onSeeAll: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSeeAll)
            .padding(top = 14.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = pluralStringResource(R.plurals.analytics_see_all_categories, categoryCount, categoryCount),
            style = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = ZeroTheme.colors.primary),
        )
        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = ZeroTheme.colors.primary, modifier = Modifier.size(18.dp))
    }
}
