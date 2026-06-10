package com.hluhovskyi.zero.categories.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.AttachWithView
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.transactions.TransactionComponent
import com.hluhovskyi.zero.ui.CollapsibleHeroLayout
import com.hluhovskyi.zero.ui.DetailStatColumn
import com.hluhovskyi.zero.ui.DetailTopBar
import com.hluhovskyi.zero.ui.UiColorScheme
import com.hluhovskyi.zero.ui.ZeroFab
import com.hluhovskyi.zero.ui.chart.BarChart
import com.hluhovskyi.zero.ui.chart.BarChartData
import com.hluhovskyi.zero.ui.chart.BarGroup
import com.hluhovskyi.zero.ui.chart.BarValue
import com.hluhovskyi.zero.ui.common.toUi
import com.hluhovskyi.zero.ui.theme.ZeroTheme
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal class CategoryDetailViewProvider(
    private val viewModel: CategoryDetailViewModel,
    private val transactionComponent: TransactionComponent,
    private val imageLoader: ImageLoader,
    private val amountFormatter: AmountFormatter,
) : ViewProvider {

    @Composable
    override fun View() {
        val state by viewModel.state.collectAsState(initial = CategoryDetailViewModel.State())
        val colorScheme = state.categoryColorScheme.toUi()

        Box(Modifier.fillMaxSize()) {
            CollapsibleHeroLayout(
                topBar = {
                    DetailTopBar(
                        title = state.categoryName,
                        onBack = { viewModel.perform(CategoryDetailViewModel.Action.Back) },
                        trailing = {
                            var menuExpanded by remember { mutableStateOf(false) }
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = stringResource(R.string.category_detail_more_options_description),
                                    tint = ZeroTheme.colors.primaryContainer,
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.category_detail_edit)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.Edit,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.perform(CategoryDetailViewModel.Action.Edit)
                                    },
                                )
                            }
                        },
                    )
                },
                hero = {
                    Column {
                        HeroCard(state, colorScheme, imageLoader, amountFormatter)
                        TrendCard(state, colorScheme)
                    }
                },
                content = { transactionComponent.AttachWithView() },
            )
            ZeroFab(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 32.dp),
                onClick = { viewModel.perform(CategoryDetailViewModel.Action.CreateTransaction) },
                icon = Icons.Filled.Add,
                contentDescription = stringResource(R.string.category_detail_add_transaction),
                expanded = true,
                text = stringResource(R.string.category_detail_add_transaction),
            )
        }
    }
}

@Composable
private fun HeroCard(
    state: CategoryDetailViewModel.State,
    colorScheme: UiColorScheme,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
) {
    Box(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colorScheme.background)
            .fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .alpha(0.15f)
                .size(80.dp),
        ) {
            imageLoader.View(
                image = state.categoryIcon,
                modifier = Modifier.fillMaxSize(),
                tint = colorScheme.primary,
            )
        }

        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = state.periodDate
                    ?.toJavaLocalDate()
                    ?.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
                    ?.uppercase()
                    .orEmpty(),
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary.copy(alpha = 0.8f),
                    letterSpacing = 1.2.sp,
                ),
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = amountFormatter.format(state.totalAmount, state.currencySymbol),
                style = TextStyle(
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colorScheme.primary,
                    letterSpacing = (-0.72).sp,
                ),
            )
            Spacer(Modifier.size(16.dp))
            Row {
                DetailStatColumn(
                    label = stringResource(R.string.category_detail_stat_transactions).uppercase(),
                    value = state.transactionCount.toString(),
                    labelColor = colorScheme.primary.copy(alpha = 0.7f),
                    valueColor = colorScheme.primary,
                )
                Spacer(Modifier.width(24.dp))
                DetailStatColumn(
                    label = stringResource(R.string.category_detail_stat_avg_per_tx).uppercase(),
                    value = amountFormatter.format(state.averageAmount, state.currencySymbol),
                    labelColor = colorScheme.primary.copy(alpha = 0.7f),
                    valueColor = colorScheme.primary,
                )
                Spacer(Modifier.width(24.dp))
                DetailStatColumn(
                    label = stringResource(R.string.category_detail_stat_largest).uppercase(),
                    value = amountFormatter.format(state.largestAmount, state.currencySymbol),
                    labelColor = colorScheme.primary.copy(alpha = 0.7f),
                    valueColor = colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun TrendCard(
    state: CategoryDetailViewModel.State,
    colorScheme: UiColorScheme,
) {
    if (state.trend.isEmpty()) return
    val accent = colorScheme.primary
    val dim = colorScheme.primary.copy(alpha = 0.4f)
    val data = BarChartData(
        state.trend.map { point ->
            BarGroup(
                label = point.label,
                bars = listOf(BarValue(point.value, if (point.isCurrent) accent else dim)),
                topLabel = point.amountLabel,
            )
        },
    )
    Column(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(ZeroTheme.colors.surfaceContainerLowest)
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 14.dp),
    ) {
        Text(
            text = stringResource(R.string.category_detail_trend_title).uppercase(),
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = ZeroTheme.colors.onSurfaceVariant,
                letterSpacing = 1.0.sp,
            ),
        )
        Spacer(Modifier.size(14.dp))
        BarChart(
            data = data,
            modifier = Modifier.fillMaxWidth(),
            barCornerRadius = 6.dp,
            barWidth = 28.dp,
        )
    }
}
