package com.hluhovskyi.zero.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.common.toCompose

internal class TransactionViewProvider(
    private val viewModel: TransactionViewModel,
    private val imageLoader: ImageLoader
) : ViewProvider {

    @Composable
    override fun View() {
        TransactionView(
            viewModel = viewModel,
            imageLoader = imageLoader
        )
    }
}

@Composable
private fun TransactionView(
    viewModel: TransactionViewModel,
    imageLoader: ImageLoader
) {
    val state by viewModel.state.collectAsState(initial = TransactionViewModel.State())

    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        val transactionModifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(
                horizontal = 16.dp,
                vertical = 12.dp
            )

        items(state.transactions) { transaction ->
            when (transaction) {
                is TransactionViewModel.Item.Transaction.Expense ->
                    TransactionExpenseView(
                        item = transaction,
                        imageLoader = imageLoader,
                        modifier = transactionModifier
                    )
                is TransactionViewModel.Item.Transaction.Transfer -> {
                    TransactionTransferView(
                        item = transaction,
                        modifier = transactionModifier
                    )
                }
                is TransactionViewModel.Item.Transaction.Income -> {
                    TransactionIncomeView(
                        item = transaction,
                        modifier = transactionModifier
                    )
                }
            }
        }
    }
}

@Composable
fun TransactionExpenseView(
    modifier: Modifier,
    item: TransactionViewModel.Item.Transaction.Expense,
    imageLoader: ImageLoader
) {
    Row(modifier = modifier) {
        Column {
            Box(
                modifier = Modifier
                    .background(item.categoryColor.toCompose(), shape = CircleShape)
                    .padding(10.dp)
            ) {
                imageLoader.View(
                    image = item.categoryIcon,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Column(
            modifier = Modifier.padding(start = 16.dp)
        ) {
            Row {
                Text(
                    text = item.categoryName,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    text = "-${item.amount.value.toPlainString()}${item.currencySymbol}"
                )
            }
            Row {
                Text(
                    fontSize = 14.sp,
                    text = item.accountName,
                    modifier = Modifier.weight(1f),
                )
                if (item.conversion is TransactionViewModel.Conversion.WithAmount) {
                    val rate = item.conversion.amount.value.toPlainString()
                    Text(
                        fontSize = 14.sp,
                        text = rate + item.conversion.currencySymbol,
                    )
                }
            }
        }
    }
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

@Composable
fun TransactionIncomeView(
    modifier: Modifier,
    item: TransactionViewModel.Item.Transaction.Income
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
            color = Color.Green,
            text = "+${item.amount.value.toPlainString()}"
        )
    }
}