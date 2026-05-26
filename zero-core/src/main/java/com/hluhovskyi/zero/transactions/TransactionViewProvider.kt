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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusTarget
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
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.transaction.TransactionExpenseView
import com.hluhovskyi.zero.transaction.TransactionIncomeView
import com.hluhovskyi.zero.transaction.TransactionTransferView
import com.hluhovskyi.zero.ui.SearchBar
import com.hluhovskyi.zero.ui.ZeroFab
import com.hluhovskyi.zero.ui.common.toUi
import com.hluhovskyi.zero.ui.theme.ZeroTheme
import kotlinx.coroutines.flow.drop

internal class TransactionViewProvider(
    private val viewModel: TransactionViewModel,
    private val imageLoader: ImageLoader,
    private val amountFormatter: AmountFormatter,
    private val dateFormatter: DateFormatter,
    private val displayConfig: DisplayConfig = DisplayConfig(),
    private val onAddTransaction: OnAddTransactionHandler = OnAddTransactionHandler.Noop,
) : ViewProvider {

    @Composable
    override fun View() {
        TransactionView(
            viewModel = viewModel,
            imageLoader = imageLoader,
            amountFormatter = amountFormatter,
            dateFormatter = dateFormatter,
            displayConfig = displayConfig,
            onAddTransaction = onAddTransaction,
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
    onAddTransaction: OnAddTransactionHandler = OnAddTransactionHandler.Noop,
) {
    val state by viewModel.state.collectAsState(initial = TransactionViewModel.State())
    val lazyListState = rememberLazyListState()

    BackHandler(enabled = state.inSelectionMode) {
        viewModel.perform(TransactionViewModel.Action.ExitSelection)
    }

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

    var fabExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        snapshotFlow {
            state.transactions.isEmpty() &&
                state.searchQuery.isBlank() &&
                !state.activeFilter.isActive
        }
            .drop(1)
            .collect { fabExpanded = it }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusTarget(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (state.inSelectionMode) {
                SelectionBar(
                    count = state.selectionCount,
                    onClose = { viewModel.perform(TransactionViewModel.Action.ExitSelection) },
                    onDelete = { viewModel.perform(TransactionViewModel.Action.DeleteSelected) },
                )
            } else if (displayConfig.showSearchBar || displayConfig.showFilterButton) {
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
                            onQueryChange = {
                                viewModel.perform(
                                    TransactionViewModel.Action.UpdateSearchQuery(
                                        it,
                                    ),
                                )
                            },
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
            if (!state.inSelectionMode && state.activeFilter.isActive) {
                FilterChipsRow(
                    filter = state.activeFilter,
                    onRemovePeriod = { viewModel.perform(TransactionViewModel.Action.Filter.RemovePeriod) },
                    onRemoveType = { viewModel.perform(TransactionViewModel.Action.Filter.RemoveType) },
                    onRemoveCategories = { viewModel.perform(TransactionViewModel.Action.Filter.RemoveCategories) },
                    onRemoveAccounts = { viewModel.perform(TransactionViewModel.Action.Filter.RemoveAccounts) },
                    onClearAll = { viewModel.perform(TransactionViewModel.Action.Filter.Clear) },
                )
            }

            val showEmpty by remember {
                derivedStateOf { state.searchQuery.isNotBlank() && state.transactions.isEmpty() }
            }

            if (showEmpty) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.transaction_empty_state),
                        fontSize = 15.sp,
                        color = ZeroTheme.colors.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = lazyListState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp),
                ) {
                    items(
                        items = state.transactions,
                        contentType = { item ->
                            when (item) {
                                is TransactionViewModel.Item.Summary -> "summary"
                                is TransactionViewModel.Item.Transaction.Expense -> "expense"
                                is TransactionViewModel.Item.Transaction.Income -> "income"
                                is TransactionViewModel.Item.Transaction.Transfer -> "transfer"
                            }
                        },
                        key = { item ->
                            when (item) {
                                is TransactionViewModel.Item.Summary -> item.date.toEpochDays()
                                is TransactionViewModel.Item.Transaction -> item.id.value
                            }
                        },
                    ) { transaction ->
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
                                        color = ZeroTheme.colors.onSurfaceVariant,
                                        letterSpacing = 0.8.sp,
                                    )
                                    Text(
                                        text = amountFormatter.format(
                                            amount = transaction.total,
                                            currencySymbol = transaction.currencySymbol,
                                        ),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ZeroTheme.colors.onSurfaceVariant,
                                        letterSpacing = 0.8.sp,
                                    )
                                }
                            }

                            is TransactionViewModel.Item.Transaction ->
                                TransactionRow(
                                    transaction = transaction,
                                    imageLoader = imageLoader,
                                    amountFormatter = amountFormatter,
                                    selected = state.isSelected(transaction.id),
                                    onClick = {
                                        if (state.inSelectionMode) {
                                            viewModel.perform(TransactionViewModel.Action.ToggleSelection(transaction.id))
                                        } else {
                                            viewModel.perform(TransactionViewModel.Action.SelectTransaction(transaction))
                                        }
                                    },
                                    onLongPress = {
                                        viewModel.perform(TransactionViewModel.Action.ToggleSelection(transaction.id))
                                    },
                                )
                        }
                    }
                }
            }
        }

        if (displayConfig.showFab && !state.inSelectionMode) {
            ZeroFab(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 32.dp),
                onClick = { onAddTransaction.onAddTransaction() },
                icon = Icons.Filled.Add,
                contentDescription = stringResource(R.string.transaction_add),
                expanded = fabExpanded,
                text = stringResource(R.string.transaction_add),
            )
        }
    }
}

@Composable
private fun TransactionRow(
    transaction: TransactionViewModel.Item.Transaction,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
    selected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val cardShape = RoundedCornerShape(12.dp)
    val contentModifier = Modifier
        .fillMaxWidth()
        .combinedClickable(
            onClick = onClick,
            onLongClick = onLongPress,
        )
        .padding(horizontal = 16.dp, vertical = 14.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 2.dp,
                end = 2.dp,
                top = 2.dp,
                bottom = 12.dp,
            )
            .clip(cardShape)
            .background(
                if (selected) ZeroTheme.colors.primaryContainerLight else ZeroTheme.colors.surfaceContainerLowest,
            ),
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
                    accountIcon = {
                        imageLoader.View(
                            image = transaction.accountIcon,
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(14.dp),
                            tint = ZeroTheme.colors.onSurfaceVariant,
                        )
                    },
                    convertedAmount = transaction.conversion.format(
                        amountFormatter,
                    ),
                    icon = { tint ->
                        imageLoader.View(
                            image = transaction.categoryIcon,
                            modifier = Modifier.size(24.dp),
                            tint = tint,
                        )
                    },
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
                    accountIcon = {
                        imageLoader.View(
                            image = transaction.accountIcon,
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(14.dp),
                            tint = ZeroTheme.colors.onSurfaceVariant,
                        )
                    },
                    convertedAmount = transaction.conversion.format(
                        amountFormatter,
                    ),
                    icon = { tint ->
                        imageLoader.View(
                            image = transaction.categoryIcon,
                            modifier = Modifier.size(24.dp),
                            tint = tint,
                        )
                    },
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
                    transferIcon = { tint ->
                        imageLoader.View(
                            image = transaction.transferIcon,
                            modifier = Modifier.size(24.dp),
                            tint = tint,
                        )
                    },
                )
            }
        }

        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 36.dp, top = 8.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(ZeroTheme.colors.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = ZeroTheme.colors.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    onClose: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.transaction_selection_exit_description),
                tint = ZeroTheme.colors.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            modifier = Modifier.weight(1f),
            text = pluralStringResource(R.plurals.transaction_selection_count, count, count),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = ZeroTheme.colors.onSurface,
        )
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.transaction_selection_delete_description),
                tint = ZeroTheme.colors.error,
                modifier = Modifier.size(20.dp),
            )
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
            .background(if (activeCount > 0) ZeroTheme.colors.primaryContainer else ZeroTheme.colors.surfaceContainerLow)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.FilterList,
            contentDescription = stringResource(R.string.transaction_filter_icon_description),
            tint = if (activeCount > 0) ZeroTheme.colors.onPrimary else ZeroTheme.colors.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        if (activeCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 6.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(ZeroTheme.colors.error),
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
                    label = pluralStringResource(
                        R.plurals.filter_chip_categories,
                        ids.size,
                        ids.size,
                    ),
                    onRemove = onRemoveCategories,
                )
            }
            filter.accountIds?.let { ids ->
                FilterChip(
                    label = pluralStringResource(
                        R.plurals.filter_chip_accounts,
                        ids.size,
                        ids.size,
                    ),
                    onRemove = onRemoveAccounts,
                )
            }
        }
        Spacer(modifier = Modifier.size(6.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(ZeroTheme.colors.surfaceContainerLow)
                .clickable(onClick = onClearAll)
                .padding(horizontal = 12.dp, vertical = 5.dp),
        ) {
            Text(
                text = stringResource(R.string.transaction_clear_all),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = ZeroTheme.colors.onSurfaceVariant,
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
            .background(ZeroTheme.colors.primaryContainer)
            .padding(start = 12.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = ZeroTheme.colors.onPrimary,
        )
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(ZeroTheme.colors.onPrimary.copy(alpha = 0.2f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.transaction_remove_filter_description),
                tint = ZeroTheme.colors.onPrimary,
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
