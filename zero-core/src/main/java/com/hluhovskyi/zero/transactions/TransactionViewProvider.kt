package com.hluhovskyi.zero.transactions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ContentAlpha
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ImageLoader
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

internal class TransactionViewProvider(
    private val viewModel: TransactionViewModel,
    private val imageLoader: ImageLoader,
    private val amountFormatter: AmountFormatter,
    private val dateFormatter: DateFormatter,
    private val displayConfig: DisplayConfig = DisplayConfig(),
    private val header: TransactionHeader = TransactionHeader.None,
) : ViewProvider {

    @Composable
    override fun View() {
        TransactionView(
            viewModel = viewModel,
            imageLoader = imageLoader,
            amountFormatter = amountFormatter,
            dateFormatter = dateFormatter,
            displayConfig = displayConfig,
            header = header,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransactionView(
    viewModel: TransactionViewModel,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
    dateFormatter: DateFormatter,
    displayConfig: DisplayConfig = DisplayConfig(),
    header: TransactionHeader = TransactionHeader.None,
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

    Column(modifier = Modifier.fillMaxSize().focusTarget()) {
        if (displayConfig.showSearchBar) {
            SearchBar(
                query = state.searchQuery,
                onQueryChange = { viewModel.perform(TransactionViewModel.Action.UpdateSearchQuery(it)) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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
                    text = "No transactions found",
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
                item { header.Content() }
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
                                                text = "Delete",
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
