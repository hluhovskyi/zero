package com.hluhovskyi.zero.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ContentAlpha
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.DateFormatter
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.transaction.TransactionExpenseView
import com.hluhovskyi.zero.transaction.TransactionIncomeView
import com.hluhovskyi.zero.transaction.TransactionTransferView
import com.hluhovskyi.zero.ui.SearchBar
import com.hluhovskyi.zero.ui.common.toUi
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant
import com.hluhovskyi.zero.ui.theme.PrimaryContainer
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLow

internal class TransactionViewProvider(
    private val viewModel: TransactionViewModel,
    private val imageLoader: ImageLoader,
    private val amountFormatter: AmountFormatter,
    private val dateFormatter: DateFormatter,
    private val displayConfig: DisplayConfig = DisplayConfig(),
) : ViewProvider {

    @Composable
    override fun View() {
        TransactionView(
            viewModel = viewModel,
            imageLoader = imageLoader,
            amountFormatter = amountFormatter,
            dateFormatter = dateFormatter,
            displayConfig = displayConfig,
        )
    }
}

@Composable
private fun TransactionView(
    viewModel: TransactionViewModel,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
    dateFormatter: DateFormatter,
    displayConfig: DisplayConfig = DisplayConfig(),
) {
    val state by viewModel.state.collectAsState(initial = TransactionViewModel.State())
    var expandedItemId: Id.Known? by remember { mutableStateOf(null) }
    val lazyListState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = lazyListState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems == 0) return@derivedStateOf false
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index
                ?: return@derivedStateOf false
            lastVisibleIndex >= totalItems - 30
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.perform(TransactionViewModel.Action.LoadMore)
        }
    }

    Box(modifier = Modifier.fillMaxSize().focusTarget()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (displayConfig.showSearchBar || displayConfig.showFilterButton) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (displayConfig.showSearchBar) {
                        SearchBar(
                            placeholder = stringResource(R.string.transaction_search_placeholder),
                            query = state.searchQuery,
                            onQueryChange = { viewModel.perform(TransactionViewModel.Action.UpdateSearchQuery(it)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (displayConfig.showFilterButton) {
                        if (displayConfig.showSearchBar) {
                            Spacer(modifier = Modifier.size(8.dp))
                        }
                        FilterButton(
                            activeCount = state.activeFilter.activeCount,
                            onClick = { viewModel.perform(TransactionViewModel.Action.Filter.Open) },
                        )
                    }
                }
            }

            // Active filter chips
            if (state.activeFilter.isActive) {
                FilterChipsRow(
                    filter = state.activeFilter,
                    onRemovePeriod = { viewModel.perform(TransactionViewModel.Action.Filter.RemovePeriod) },
                    onRemoveType = { viewModel.perform(TransactionViewModel.Action.Filter.RemoveType) },
                    onRemoveCategories = { viewModel.perform(TransactionViewModel.Action.Filter.RemoveCategories) },
                    onRemoveAccounts = { viewModel.perform(TransactionViewModel.Action.Filter.RemoveAccounts) },
                    onClearAll = { viewModel.perform(TransactionViewModel.Action.Filter.Clear) },
                )
            }

            if (state.searchQuery.isNotBlank() && state.transactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.transaction_empty_state),
                        fontSize = 15.sp,
                        color = Color(0xFF44464F),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = lazyListState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp),
                ) {
                    items(state.transactions) { transaction ->
                        when (transaction) {
                            is TransactionViewModel.Item.Summary -> {
                                val isFirst = state.transactions.first() == transaction
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            top = if (isFirst) 8.dp else 20.dp,
                                            bottom = 8.dp,
                                            start = 4.dp,
                                            end = 4.dp,
                                        ),
                                ) {
                                    Text(
                                        modifier = Modifier.weight(1f),
                                        text = dateFormatter.format(transaction).uppercase(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF44464F),
                                        letterSpacing = 0.8.sp,
                                    )
                                    Text(
                                        text = amountFormatter.format(
                                            amount = transaction.total,
                                            currencySymbol = transaction.currencySymbol,
                                        ),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF44464F),
                                        letterSpacing = 0.8.sp,
                                    )
                                }
                            }
                            is TransactionViewModel.Item.Transaction -> {
                                val cardShape = RoundedCornerShape(12.dp)
                                val contentModifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { viewModel.perform(TransactionViewModel.Action.SelectTransaction(transaction)) },
                                        onLongClick = { expandedItemId = transaction.id },
                                    )
                                    .padding(horizontal = 16.dp, vertical = 14.dp)

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 12.dp)
                                        .clip(cardShape)
                                        .background(Color(0xFFFFFFFF)),
                                ) {
                                    when (transaction) {
                                        is TransactionViewModel.Item.Transaction.Expense ->
                                            TransactionExpenseView(
                                                modifier = contentModifier,
                                                categoryName = transaction.categoryName,
                                                amount = amountFormatter.format(
                                                    amount = transaction.amount,
                                                    currencySymbol = transaction.currencySymbol,
                                                ),
                                                accountName = transaction.accountName,
                                                iconColorScheme = transaction.categoryColorScheme.toUi(),
                                                accountIcon = transaction.accountIcon.toComposable(
                                                    imageLoader = imageLoader,
                                                    modifier = Modifier
                                                        .alpha(ContentAlpha.medium)
                                                        .padding(end = 6.dp)
                                                        .size(20.dp),
                                                ),
                                                convertedAmount = transaction.conversion.format(amountFormatter),
                                                icon = transaction.categoryIcon.toTintedComposable(
                                                    imageLoader = imageLoader,
                                                    modifier = Modifier.size(24.dp),
                                                ),
                                            )
                                        is TransactionViewModel.Item.Transaction.Income -> {
                                            TransactionIncomeView(
                                                modifier = contentModifier,
                                                categoryName = transaction.categoryName,
                                                amount = amountFormatter.format(
                                                    amount = transaction.amount,
                                                    currencySymbol = transaction.currencySymbol,
                                                ),
                                                accountName = transaction.accountName,
                                                iconColorScheme = transaction.categoryColorScheme.toUi(),
                                                convertedAmount = transaction.conversion.format(amountFormatter),
                                                icon = transaction.categoryIcon.toTintedComposable(
                                                    imageLoader = imageLoader,
                                                    modifier = Modifier.size(24.dp),
                                                ),
                                            )
                                        }
                                        is TransactionViewModel.Item.Transaction.Transfer -> {
                                            TransactionTransferView(
                                                modifier = contentModifier,
                                                sourceAccountName = transaction.accountName,
                                                targetAccountName = transaction.targetAccountName,
                                                sourceAmount = amountFormatter.format(
                                                    amount = transaction.amount,
                                                    currencySymbol = transaction.currencySymbol,
                                                ),
                                                targetAmount = amountFormatter.format(
                                                    amount = transaction.targetAmount,
                                                    currencySymbol = transaction.targetCurrencySymbol,
                                                ),
                                                transferIconColorScheme = transaction.transferColorScheme.toUi(),
                                                transferIcon = transaction.transferIcon.toTintedComposable(
                                                    imageLoader = imageLoader,
                                                    modifier = Modifier.size(24.dp),
                                                ),
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = expandedItemId == transaction.id,
                                        onDismissRequest = { expandedItemId = null },
                                    ) {
                                        DropdownMenuItem(
                                            onClick = {
                                                viewModel.perform(TransactionViewModel.Action.DeleteTransaction(transaction.id))
                                                expandedItemId = null
                                            },
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Delete,
                                                    contentDescription = null,
                                                    tint = Color(0xFFBA1A1A),
                                                    modifier = Modifier.size(20.dp),
                                                )
                                                Spacer(modifier = Modifier.size(8.dp))
                                                Text(
                                                    text = stringResource(R.string.transaction_delete),
                                                    color = Color(0xFFBA1A1A),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterButton(
    activeCount: Int,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (activeCount > 0) PrimaryContainer else SurfaceContainerLow)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.FilterList,
            contentDescription = stringResource(R.string.transaction_filter_icon_description),
            tint = if (activeCount > 0) Color.White else OnSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        if (activeCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 6.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE53935)),
            )
        }
    }
}

@Composable
private fun FilterChipsRow(
    filter: TransactionFilter,
    onRemovePeriod: () -> Unit,
    onRemoveType: () -> Unit,
    onRemoveCategories: () -> Unit,
    onRemoveAccounts: () -> Unit,
    onClearAll: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Scrollable chips area
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            filter.period?.let { period ->
                FilterChip(label = period.label, onRemove = onRemovePeriod)
            }
            if (filter.type != TransactionFilter.TransactionType.All) {
                FilterChip(label = filter.type.label, onRemove = onRemoveType)
            }
            filter.categoryIds?.let { ids ->
                FilterChip(
                    label = pluralStringResource(R.plurals.filter_chip_categories, ids.size, ids.size),
                    onRemove = onRemoveCategories,
                )
            }
            filter.accountIds?.let { ids ->
                FilterChip(
                    label = pluralStringResource(R.plurals.filter_chip_accounts, ids.size, ids.size),
                    onRemove = onRemoveAccounts,
                )
            }
        }
        Spacer(modifier = Modifier.size(6.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceContainerLow)
                .clickable(onClick = onClearAll)
                .padding(horizontal = 12.dp, vertical = 5.dp),
        ) {
            Text(
                text = stringResource(R.string.transaction_clear_all),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(PrimaryContainer)
            .padding(start = 12.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.transaction_remove_filter_description),
                tint = Color.White,
                modifier = Modifier.size(10.dp),
            )
        }
    }
}

private fun TransactionViewModel.Conversion.format(
    amountFormatter: AmountFormatter,
): String? = if (this is TransactionViewModel.Conversion.WithAmount) {
    amountFormatter.format(
        amount = amount,
        currencySymbol = currencySymbol,
    )
} else {
    null
}

private fun DateFormatter.format(
    transaction: TransactionViewModel.Item.Summary,
): String = format(
    date = transaction.date,
    dayConfig = DateFormatter.DayConfig.WithoutZero,
    monthConfig = DateFormatter.MonthConfig.Readable,
    yearConfig = DateFormatter.YearConfig.SkipCurrent,
)

private fun Image.toComposable(
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
): @Composable () -> Unit = {
    imageLoader.View(
        image = this,
        modifier = modifier,
    )
}

private fun Image.toTintedComposable(
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
): @Composable (tint: Color) -> Unit = { tint ->
    imageLoader.View(
        image = this,
        modifier = modifier,
        tint = tint,
    )
}
