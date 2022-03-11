package com.hluhovskyi.zero.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hluhovskyi.zero.common.Transaction
import com.hluhovskyi.zero.common.ViewProvider

internal class TransactionViewProvider(
    private val transactionRepository: TransactionRepository
) : ViewProvider {

    @Composable
    override fun View() {
        val transactions by transactionRepository.query(TransactionRepository.Criteria.All())
            .collectAsState(initial = emptyList())

        TransactionView(
            transactions = transactions
        )
    }
}

@Composable
private fun TransactionView(
    transactions: List<Transaction>
) {
    LazyColumn {
        itemsIndexed(transactions) { index, transaction ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { }
                    .padding(
                        horizontal = 16.dp,
                        vertical = 8.dp
                    )
            ) {
                Row {
                    Text(text = "${transaction.amount.value.toPlainString()}")
                }
            }
        }
    }
}