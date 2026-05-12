package com.hluhovskyi.zero.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExtendedFloatingActionButton
import androidx.compose.material.FloatingActionButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.CategoryIconView
import com.hluhovskyi.zero.ui.SegmentedToggle
import com.hluhovskyi.zero.ui.common.toUi
import com.hluhovskyi.zero.ui.theme.OnSurface
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant
import com.hluhovskyi.zero.ui.theme.Outline
import com.hluhovskyi.zero.ui.theme.Primary
import com.hluhovskyi.zero.ui.theme.SurfaceContainer
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLowest

internal class CategoryViewProvider(
    private val viewModel: CategoryViewModel,
    private val imageLoader: ImageLoader,
    private val amountFormatter: AmountFormatter,
    private val onAddCategory: OnAddCategoryHandler,
) : ViewProvider {

    @Composable
    override fun View() {
        CategoryView(
            viewModel = viewModel,
            imageLoader = imageLoader,
            amountFormatter = amountFormatter,
            onAddCategory = onAddCategory,
        )
    }
}

@Composable
private fun CategoryView(
    viewModel: CategoryViewModel,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
    onAddCategory: OnAddCategoryHandler,
) {
    val state by viewModel.state.collectAsState(initial = CategoryViewModel.State())

    val currencySymbol = state.currencySymbol
    val grandTotal = state.grandTotal
    val active = remember(state.categories) {
        state.categories.filter { it.spending is CategoryViewModel.Spending.Active }
    }
    val inactive = remember(state.categories) {
        state.categories.filter { it.spending is CategoryViewModel.Spending.None }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)) {
            item {
                Text(
                    text = stringResource(R.string.category_title),
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                    style = TextStyle(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Primary,
                    ),
                )
            }

            item {
                val expenseLabel = stringResource(R.string.transaction_type_expense)
                val incomeLabel = stringResource(R.string.transaction_type_income)
                SegmentedToggle(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 6.dp),
                    items = listOf(CategoryType.EXPENSE, CategoryType.INCOME),
                    selectedItem = state.selectedTab,
                    onItemSelected = { viewModel.perform(CategoryViewModel.Action.SelectTab(it)) },
                    labelMapping = { if (it == CategoryType.EXPENSE) expenseLabel else incomeLabel },
                )
            }

            items(active, key = { it.id.value }) { category ->
                val spending = category.spending as CategoryViewModel.Spending.Active
                val barFraction = if (grandTotal > 0L) {
                    (spending.totalAmount / grandTotal).toFloat().coerceIn(0f, 1f)
                } else {
                    0f
                }
                val percentOfTotal = if (grandTotal > 0L) {
                    (spending.totalAmount / grandTotal * 100).toInt()
                } else {
                    0
                }

                ActiveCategoryCard(
                    category = category,
                    spending = spending,
                    formattedTotal = amountFormatter.format(spending.totalAmount, currencySymbol),
                    barFraction = barFraction,
                    percentOfTotal = percentOfTotal,
                    barColor = category.colorScheme.toUi().primary,
                    onClick = { viewModel.perform(CategoryViewModel.Action.SelectCategory(category)) },
                    imageLoader = imageLoader,
                )
            }

            if (inactive.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.category_unused_this_month),
                        modifier = Modifier.padding(
                            start = 20.dp,
                            end = 20.dp,
                            top = 10.dp,
                            bottom = 6.dp,
                        ),
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Outline,
                            letterSpacing = 0.7.sp,
                        ),
                    )
                }
                items(inactive, key = { it.id.value }) { category ->
                    InactiveCategoryCard(
                        category = category,
                        onClick = { viewModel.perform(CategoryViewModel.Action.SelectCategory(category)) },
                        imageLoader = imageLoader,
                    )
                }
            }
        }

        ExtendedFloatingActionButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 32.dp),
            icon = { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.category_add_description)) },
            text = { Text(stringResource(R.string.category_add)) },
            onClick = { onAddCategory.onAdd(state.selectedTab) },
            elevation = FloatingActionButtonDefaults.elevation(8.dp),
        )
    }
}

@Composable
private fun ActiveCategoryCard(
    category: CategoryViewModel.CategoryItem,
    spending: CategoryViewModel.Spending.Active,
    formattedTotal: String,
    barFraction: Float,
    percentOfTotal: Int,
    barColor: Color,
    onClick: () -> Unit,
    imageLoader: ImageLoader,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(SurfaceContainerLowest, shape = RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryIconView(colorScheme = category.colorScheme.toUi(), size = 40.dp) { tint ->
                imageLoader.View(
                    image = category.icon,
                    modifier = Modifier
                        .sizeIn(maxHeight = 24.dp, maxWidth = 24.dp)
                        .aspectRatio(1f),
                    scale = ImageLoader.Scale.Crop,
                    tint = tint,
                )
            }
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = category.name,
                        modifier = Modifier.weight(1f),
                        style = TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurface,
                        ),
                    )
                    Text(
                        text = formattedTotal,
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Primary,
                            letterSpacing = (-0.1).sp,
                        ),
                    )
                }
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = pluralStringResource(R.plurals.category_transaction_count, spending.transactionCount, spending.transactionCount),
                        modifier = Modifier.weight(1f),
                        style = TextStyle(fontSize = 12.sp, color = OnSurfaceVariant),
                    )
                    Text(
                        text = stringResource(R.string.category_percent_of_total, percentOfTotal),
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = OnSurfaceVariant,
                        ),
                    )
                }
            }
        }
        SpendingBar(
            fraction = barFraction,
            color = barColor,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun InactiveCategoryCard(
    category: CategoryViewModel.CategoryItem,
    onClick: () -> Unit,
    imageLoader: ImageLoader,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(SurfaceContainerLowest, shape = RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIconView(colorScheme = category.colorScheme.toUi(), size = 40.dp) { tint ->
            imageLoader.View(
                image = category.icon,
                modifier = Modifier
                    .sizeIn(maxHeight = 24.dp, maxWidth = 24.dp)
                    .aspectRatio(1f),
                scale = ImageLoader.Scale.Crop,
                tint = tint,
            )
        }
        Text(
            text = category.name,
            modifier = Modifier
                .padding(start = 14.dp)
                .weight(1f),
            style = TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurface,
            ),
        )
        Text(
            text = stringResource(R.string.category_no_activity),
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Outline,
            ),
        )
    }
}

@Composable
private fun SpendingBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(SurfaceContainer, shape = RoundedCornerShape(2.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(4.dp)
                .background(color.copy(alpha = 0.75f), shape = RoundedCornerShape(2.dp)),
        )
    }
}
