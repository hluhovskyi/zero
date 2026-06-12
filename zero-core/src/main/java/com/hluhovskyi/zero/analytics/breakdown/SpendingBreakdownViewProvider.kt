package com.hluhovskyi.zero.analytics.breakdown

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.analytics.breakdown.SpendingBreakdownViewModel.Dimension
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.DateFormatter
import com.hluhovskyi.zero.common.DateRange
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.CategoryIconView
import com.hluhovskyi.zero.ui.DetailTopBar
import com.hluhovskyi.zero.ui.SegmentedToggle
import com.hluhovskyi.zero.ui.chart.DonutChart
import com.hluhovskyi.zero.ui.chart.DonutChartData
import com.hluhovskyi.zero.ui.chart.DonutSegment
import com.hluhovskyi.zero.ui.common.toUi
import com.hluhovskyi.zero.ui.theme.ZeroTheme

internal class SpendingBreakdownViewProvider(
    private val viewModel: SpendingBreakdownViewModel,
    private val imageLoader: ImageLoader,
    private val amountFormatter: AmountFormatter,
    private val dateFormatter: DateFormatter,
) : ViewProvider {

    @Composable
    override fun View() {
        val state by viewModel.state.collectAsState(initial = SpendingBreakdownViewModel.State())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ZeroTheme.colors.surface),
        ) {
            DetailTopBar(
                title = stringResource(R.string.breakdown_title),
                onBack = { viewModel.perform(SpendingBreakdownViewModel.Action.Back) },
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            ) {
                FilteredByChips(state.context, dateFormatter)
                Spacer(Modifier.height(16.dp))
                TotalHeader(state, amountFormatter)
                if (state.showAccountDimension) {
                    Spacer(Modifier.height(16.dp))
                    DimensionToggle(state.selectedDimension) {
                        viewModel.perform(SpendingBreakdownViewModel.Action.SelectDimension(it))
                    }
                }
                Spacer(Modifier.height(14.dp))
                SplitBlock(state, imageLoader, amountFormatter)
            }
        }
    }
}

@Composable
private fun FilteredByChips(
    context: SpendingBreakdownViewModel.Context,
    dateFormatter: DateFormatter,
) {
    Text(
        text = stringResource(R.string.breakdown_filtered_by).uppercase(),
        style = TextStyle(
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = ZeroTheme.colors.onSurfaceVariant,
            letterSpacing = 1.sp,
        ),
        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (context.categoryCount > 0) {
            ContextChip(pluralStringResource(R.plurals.filter_chip_categories, context.categoryCount, context.categoryCount))
        }
        if (context.accountCount > 0) {
            ContextChip(pluralStringResource(R.plurals.filter_chip_accounts, context.accountCount, context.accountCount))
        }
        context.dateRange?.let { range ->
            ContextChip(dateFormatter.formatSpan(range))
        }
    }
}

@Composable
private fun ContextChip(label: String) {
    Text(
        text = label,
        style = TextStyle(
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = ZeroTheme.colors.onSurface,
        ),
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(ZeroTheme.colors.surfaceContainerLow)
            .padding(horizontal = 13.dp, vertical = 7.dp),
    )
}

@Composable
private fun TotalHeader(
    state: SpendingBreakdownViewModel.State,
    amountFormatter: AmountFormatter,
) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = amountFormatter.format(state.totalAmount, state.currencySymbol, AmountFormatter.Style.Short),
            style = TextStyle(
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                color = ZeroTheme.colors.primary,
                letterSpacing = (-0.6).sp,
            ),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.breakdown_spent),
            style = TextStyle(fontSize = 13.sp, color = ZeroTheme.colors.onSurfaceVariant),
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
    Text(
        text = pluralStringResource(R.plurals.filter_summary_count, state.transactionCount, state.transactionCount),
        style = TextStyle(fontSize = 12.5.sp, color = ZeroTheme.colors.onSurfaceVariant),
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun DimensionToggle(
    selected: Dimension,
    onSelect: (Dimension) -> Unit,
) {
    val categoryLabel = stringResource(R.string.breakdown_dimension_category)
    val accountLabel = stringResource(R.string.breakdown_dimension_account)
    SegmentedToggle(
        modifier = Modifier.fillMaxWidth(),
        items = Dimension.entries,
        selectedItem = selected,
        onItemSelected = onSelect,
        labelMapping = { dimension ->
            when (dimension) {
                Dimension.Category -> categoryLabel
                Dimension.Account -> accountLabel
            }
        },
    )
}

@Composable
private fun SplitBlock(
    state: SpendingBreakdownViewModel.State,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(ZeroTheme.colors.surfaceContainerLowest)
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DonutChart(
                data = DonutChartData(state.segments.map { DonutSegment(it.value, it.colorScheme.primary.toUi()) }),
                modifier = Modifier.size(132.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.breakdown_donut_spent).uppercase(),
                        style = TextStyle(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = ZeroTheme.colors.onSurfaceVariant,
                            letterSpacing = 1.sp,
                        ),
                    )
                    Text(
                        text = amountFormatter.format(state.totalAmount, state.currencySymbol, AmountFormatter.Style.Short),
                        style = TextStyle(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ZeroTheme.colors.primary,
                        ),
                    )
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                state.rows.forEach { row -> LegendItem(row) }
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(thickness = 1.dp, color = ZeroTheme.colors.surfaceContainer)
        state.rows.forEach { row ->
            SpendingBreakdownRow(row, imageLoader, amountFormatter, state.currencySymbol)
        }
    }
}

@Composable
private fun LegendItem(row: SpendingBreakdownViewModel.Row) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(row.colorScheme.primary.toUi()),
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = row.name,
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ZeroTheme.colors.onSurface),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.breakdown_share_percent, row.sharePercent),
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ZeroTheme.colors.onSurface),
        )
    }
}

@Composable
private fun SpendingBreakdownRow(
    row: SpendingBreakdownViewModel.Row,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
    currencySymbol: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIconView(
            colorScheme = row.colorScheme.toUi(),
            size = 38.dp,
            contentPadding = 8.dp,
        ) { iconTint ->
            imageLoader.View(
                modifier = Modifier.sizeIn(maxHeight = 20.dp, maxWidth = 20.dp),
                image = row.icon,
                tint = iconTint,
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row {
                Text(
                    text = row.name,
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ZeroTheme.colors.onSurface),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = amountFormatter.format(row.amount, currencySymbol, AmountFormatter.Style.Short),
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = ZeroTheme.colors.onSurface),
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShareBar(row.shareFraction, row.colorScheme.primary.toUi(), modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = pluralStringResource(R.plurals.breakdown_row_txns, row.transactionCount, row.transactionCount),
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = ZeroTheme.colors.onSurfaceVariant),
                )
            }
        }
    }
}

@Composable
private fun ShareBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(5.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(ZeroTheme.colors.surfaceContainer),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
    }
}

private fun DateFormatter.formatSpan(range: DateRange): String {
    val start = format(range.start, DateFormatter.DayConfig.WithoutZero, DateFormatter.MonthConfig.Readable, DateFormatter.YearConfig.SkipCurrent)
    if (range.start == range.end) return start
    val end = format(range.end, DateFormatter.DayConfig.WithoutZero, DateFormatter.MonthConfig.Readable, DateFormatter.YearConfig.SkipCurrent)
    return "$start – $end"
}
