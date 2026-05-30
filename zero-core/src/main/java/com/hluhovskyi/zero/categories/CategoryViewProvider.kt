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
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.CategoryIconView
import com.hluhovskyi.zero.ui.SwipeableSegmentedTabs
import com.hluhovskyi.zero.ui.ZeroFab
import com.hluhovskyi.zero.ui.common.toUi
import com.hluhovskyi.zero.ui.theme.ZeroTheme

private val CATEGORY_TABS = listOf(CategoryType.EXPENSE, CategoryType.INCOME)

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
    val state by viewModel.state.collectAsState()
    val expenseLabel = stringResource(R.string.transaction_type_expense)
    val incomeLabel = stringResource(R.string.transaction_type_income)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.category_title),
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
                style = TextStyle(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = ZeroTheme.colors.primary,
                ),
            )

            SwipeableSegmentedTabs(
                items = CATEGORY_TABS,
                selectedItem = state.selectedTab,
                onItemSelected = { viewModel.perform(CategoryViewModel.Action.SelectTab(it)) },
                labelMapping = { if (it == CategoryType.EXPENSE) expenseLabel else incomeLabel },
                modifier = Modifier.fillMaxSize(),
                toggleModifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 6.dp),
            ) { tab ->
                CategoryPage(
                    active = state.activeCategoriesByType[tab].orEmpty(),
                    inactive = state.inactiveCategoriesByType[tab].orEmpty(),
                    grandTotal = state.grandTotalByType[tab] ?: Amount.zero(),
                    currencySymbol = state.currencySymbol,
                    amountFormatter = amountFormatter,
                    imageLoader = imageLoader,
                    onCategoryClick = { viewModel.perform(CategoryViewModel.Action.SelectCategory(it)) },
                )
            }
        }

        ZeroFab(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 32.dp),
            onClick = { onAddCategory.onAdd(state.selectedTab) },
            icon = Icons.Filled.Add,
            contentDescription = stringResource(R.string.category_add_description),
            expanded = !state.hasAddedCategory,
            text = stringResource(R.string.category_add),
        )
    }
}

@Composable
private fun CategoryPage(
    active: List<CategoryViewModel.CategoryItem>,
    inactive: List<CategoryViewModel.CategoryItem>,
    grandTotal: Amount,
    currencySymbol: String,
    amountFormatter: AmountFormatter,
    imageLoader: ImageLoader,
    onCategoryClick: (CategoryViewModel.CategoryItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        items(active, key = { it.id.value }) { category ->
            val spending = category.spending as CategoryViewModel.Spending.Active
            val fraction = if (grandTotal > 0L) {
                (spending.totalAmount / grandTotal).toFloat().coerceIn(0f, 1f)
            } else {
                0f
            }
            val percentOfTotal = (fraction * 100).toInt()

            ActiveCategoryCard(
                category = category,
                spending = spending,
                formattedTotal = amountFormatter.format(spending.totalAmount, currencySymbol),
                maxFraction = fraction,
                percentOfTotal = percentOfTotal,
                onClick = { onCategoryClick(category) },
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
                        color = ZeroTheme.colors.outline,
                        letterSpacing = 0.7.sp,
                    ),
                )
            }
            items(inactive, key = { it.id.value }) { category ->
                InactiveCategoryCard(
                    category = category,
                    onClick = { onCategoryClick(category) },
                    imageLoader = imageLoader,
                )
            }
        }
    }
}

@Composable
private fun ActiveCategoryCard(
    category: CategoryViewModel.CategoryItem,
    spending: CategoryViewModel.Spending.Active,
    formattedTotal: String,
    maxFraction: Float,
    percentOfTotal: Int,
    onClick: () -> Unit,
    imageLoader: ImageLoader,
) {
    val colorScheme = category.colorScheme.toUi()
    val categoryBg = colorScheme.background
    val rowBg = ZeroTheme.colors.surfaceContainerLowest
    val tintedBg = lerp(rowBg, categoryBg, 0.45f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .drawBehind {
                drawRect(rowBg)
                if (maxFraction > 0f) {
                    drawRect(tintedBg, size = Size(size.width * maxFraction, size.height))
                }
            }
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryIconView(colorScheme = colorScheme, size = 40.dp) { tint ->
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
                            color = ZeroTheme.colors.onSurface,
                        ),
                    )
                    Text(
                        text = formattedTotal,
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ZeroTheme.colors.primary,
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
                        style = TextStyle(fontSize = 12.sp, color = ZeroTheme.colors.onSurfaceVariant),
                    )
                    Text(
                        text = stringResource(R.string.category_percent_of_total, percentOfTotal),
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ZeroTheme.colors.onSurfaceVariant,
                        ),
                    )
                }
            }
        }

        // Bottom progress bar — track covers full width, fill shows relative spending vs max category
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(ZeroTheme.colors.surfaceContainer)
                .align(Alignment.BottomStart),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(maxFraction)
                    .height(3.dp)
                    .background(categoryBg),
            )
        }
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
            .background(ZeroTheme.colors.surfaceContainerLowest, shape = RoundedCornerShape(16.dp))
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
                color = ZeroTheme.colors.onSurface,
            ),
        )
        Text(
            text = stringResource(R.string.category_no_activity),
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = ZeroTheme.colors.outline,
            ),
        )
    }
}
