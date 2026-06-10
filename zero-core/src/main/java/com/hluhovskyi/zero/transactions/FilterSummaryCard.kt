package com.hluhovskyi.zero.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.DateFormatter
import com.hluhovskyi.zero.transactions.TransactionViewModel.FilterSummary
import com.hluhovskyi.zero.ui.theme.ZeroTheme

// On-island white text opacities (the surface is always navy; see ZeroColors island tokens).
private const val ALPHA_STRONG = 0.95f
private const val ALPHA_DIM = 0.45f
private const val ALPHA_FAINT = 0.4f
private const val ALPHA_DIVIDER = 0.08f

@Composable
internal fun FilterSummaryCard(
    summary: FilterSummary,
    amountFormatter: AmountFormatter,
    dateFormatter: DateFormatter,
    onShowBreakdown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp)
            .background(ZeroTheme.colors.islandBackground, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = pluralStringResource(R.plurals.filter_summary_count, summary.count, summary.count),
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = islandContent(ALPHA_STRONG)),
            )
            Text(
                text = dateFormatter.formatSpan(summary.dateSpan),
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = islandContent(ALPHA_DIM)),
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            summary.columns.forEachIndexed { index, column ->
                StatColumn(
                    column = column,
                    currencySymbol = summary.currencySymbol,
                    amountFormatter = amountFormatter,
                    align = when (index) {
                        0 -> TextAlign.Start
                        summary.columns.lastIndex -> TextAlign.End
                        else -> TextAlign.Center
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(thickness = 1.dp, color = islandContent(ALPHA_DIVIDER))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onShowBreakdown)
                .padding(top = 11.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.filter_summary_show_breakdown),
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ZeroTheme.colors.islandAction),
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = ZeroTheme.colors.islandAction,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun StatColumn(
    column: FilterSummary.Column,
    currencySymbol: String,
    amountFormatter: AmountFormatter,
    align: TextAlign,
    modifier: Modifier = Modifier,
) {
    val value = column.amount?.let { amount ->
        column.signPrefix() + amountFormatter.format(amount, currencySymbol, AmountFormatter.Style.Short)
    } ?: stringResource(R.string.filter_summary_placeholder)

    Column(modifier = modifier) {
        Text(
            text = stringResource(column.label.stringRes()).uppercase(),
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = islandContent(ALPHA_DIM),
                letterSpacing = 1.sp,
                textAlign = align,
            ),
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        )
        Text(
            text = value,
            style = TextStyle(
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = column.emphasis.color(),
                textAlign = align,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
@ReadOnlyComposable
private fun islandContent(alpha: Float): Color = ZeroTheme.colors.islandContent.copy(alpha = alpha)

private fun DateFormatter.formatSpan(span: FilterSummary.DateSpan): String {
    val start = format(span.start, DateFormatter.DayConfig.WithoutZero, DateFormatter.MonthConfig.Readable, DateFormatter.YearConfig.SkipCurrent)
    if (span.start == span.end) return start
    val end = format(span.end, DateFormatter.DayConfig.WithoutZero, DateFormatter.MonthConfig.Readable, DateFormatter.YearConfig.SkipCurrent)
    return "$start – $end"
}

private fun FilterSummary.Column.signPrefix(): String = when (label) {
    FilterSummary.Label.In, FilterSummary.Label.Received -> "+"
    FilterSummary.Label.Out, FilterSummary.Label.Spent -> "–"
    FilterSummary.Label.Net -> when (emphasis) {
        FilterSummary.Emphasis.Positive -> "+"
        FilterSummary.Emphasis.Negative -> "–"
        else -> ""
    }
    FilterSummary.Label.Avg, FilterSummary.Label.Largest -> ""
}

private fun FilterSummary.Label.stringRes(): Int = when (this) {
    FilterSummary.Label.Net -> R.string.filter_summary_label_net
    FilterSummary.Label.Out -> R.string.filter_summary_label_out
    FilterSummary.Label.In -> R.string.filter_summary_label_in
    FilterSummary.Label.Spent -> R.string.filter_summary_label_spent
    FilterSummary.Label.Avg -> R.string.filter_summary_label_avg
    FilterSummary.Label.Largest -> R.string.filter_summary_label_largest
    FilterSummary.Label.Received -> R.string.filter_summary_label_received
}

@Composable
@ReadOnlyComposable
private fun FilterSummary.Emphasis.color(): Color = when (this) {
    FilterSummary.Emphasis.Positive -> ZeroTheme.colors.islandPositive
    FilterSummary.Emphasis.Negative -> ZeroTheme.colors.islandNegative
    FilterSummary.Emphasis.Neutral -> islandContent(ALPHA_STRONG)
    FilterSummary.Emphasis.Faint -> islandContent(ALPHA_FAINT)
}
