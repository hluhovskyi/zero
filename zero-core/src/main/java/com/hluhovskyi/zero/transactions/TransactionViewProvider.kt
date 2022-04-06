package com.hluhovskyi.zero.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.DateFormatter
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.transaction.TransactionExpenseView
import com.hluhovskyi.zero.transaction.TransactionIncomeView

internal class TransactionViewProvider(
    private val viewModel: TransactionViewModel,
    private val imageLoader: ImageLoader,
    private val amountFormatter: AmountFormatter,
    private val dateFormatter: DateFormatter,
) : ViewProvider {

    @Composable
    override fun View() {
        TransactionView(
            viewModel = viewModel,
            imageLoader = imageLoader,
            amountFormatter = amountFormatter,
            dateFormatter = dateFormatter,
        )
    }
}

@Composable
private fun TransactionView(
    viewModel: TransactionViewModel,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
    dateFormatter: DateFormatter,
) {
    val state by viewModel.state.collectAsState(initial = TransactionViewModel.State())
    LazyColumn {
        val transactionModifier = Modifier
            .fillMaxWidth()
            // TODO: Handle click
            .clickable { }
            .padding(
                horizontal = 12.dp,
                vertical = 12.dp
            )

        items(state.transactions) { transaction ->
            when (transaction) {
                is TransactionViewModel.Item.Summary -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x33EEEEEE))
                            .padding(
                                vertical = 8.dp,
                                horizontal = 12.dp
                            )
                    ) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = dateFormatter.format(transaction)
                        )
                        Text(
                            text = amountFormatter.format(
                                amount = transaction.total,
                                currencySymbol = transaction.currencySymbol
                            ),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                is TransactionViewModel.Item.Transaction.Expense ->
                    TransactionExpenseView(
                        modifier = transactionModifier,
                        categoryColor = transaction.categoryColor,
                        categoryName = transaction.categoryName,
                        amount = amountFormatter.format(
                            amount = transaction.amount,
                            currencySymbol = transaction.currencySymbol
                        ),
                        accountName = transaction.accountName,
                        accountIcon = transaction.accountIcon.toComposable(
                            imageLoader = imageLoader,
                            modifier = Modifier
                                .alpha(ContentAlpha.medium)
                                .padding(end = 6.dp)
                                .size(20.dp),
                        ),
                        convertedAmount = transaction.conversion.format(amountFormatter),
                        icon = transaction.categoryIcon.toComposable(
                            imageLoader = imageLoader,
                            modifier = Modifier.size(24.dp),
                        ),
                    )
                is TransactionViewModel.Item.Transaction.Income -> {
                    TransactionIncomeView(
                        modifier = transactionModifier,
                        categoryColor = transaction.categoryColor,
                        categoryName = transaction.categoryName,
                        amount = amountFormatter.format(
                            amount = transaction.amount,
                            currencySymbol = transaction.currencySymbol,
                        ),
                        accountName = transaction.accountName,
                        convertedAmount = transaction.conversion.format(amountFormatter),
                        icon = transaction.categoryIcon.toComposable(
                            imageLoader = imageLoader,
                            modifier = Modifier.size(24.dp),
                        ),
                    )
                }
                is TransactionViewModel.Item.Transaction.Transfer -> {
                    TransactionTransferView(
                        item = transaction,
                        modifier = transactionModifier
                    )
                }
            }
        }
    }
}

private fun TransactionViewModel.Conversion.format(
    amountFormatter: AmountFormatter
): String? = if (this is TransactionViewModel.Conversion.WithAmount) {
    amountFormatter.format(
        amount = amount,
        currencySymbol = currencySymbol,
    )
} else {
    null
}

private fun DateFormatter.format(
    transaction: TransactionViewModel.Item.Summary
): String = format(
    date = transaction.date,
    dayConfig = DateFormatter.DayConfig.WithoutZero,
    monthConfig = DateFormatter.MonthConfig.Readable,
    yearConfig = DateFormatter.YearConfig.SkipCurrent,
)

private fun Image.toComposable(
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier
): @Composable () -> Unit = {
    imageLoader.View(
        image = this,
        modifier = modifier,
    )
}


@Composable
fun TransactionTransferView(
    modifier: Modifier,
    item: TransactionViewModel.Item.Transaction.Transfer
) {
    Row(modifier = modifier) {
        Text(
            text = item.accountName,
            fontSize = 18.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            text = "-${item.amount.value.toPlainString()}"
        )
    }
}
