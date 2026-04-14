// zero-core/src/main/java/com/hluhovskyi/zero/imports/transactionspreview/TransactionsPreviewViewProvider.kt
package com.hluhovskyi.zero.imports.transactionspreview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.common.ViewProvider

internal class TransactionsPreviewViewProvider(
    private val viewModel: TransactionsPreviewViewModel,
) : ViewProvider {

    @Composable
    override fun View() {
        TransactionsPreviewView(viewModel = viewModel)
    }
}

@Composable
private fun TransactionsPreviewView(viewModel: TransactionsPreviewViewModel) {
    val state by viewModel.state.collectAsState(initial = TransactionsPreviewViewModel.State())

    Column(modifier = Modifier.fillMaxSize()) {
        IconButton(onClick = { viewModel.perform(TransactionsPreviewViewModel.Action.Back) }) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text(
            text = "Review & Finalize",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Text(
            text = "STEP 4 OF 4",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.transactions, key = { it.primaryText + it.date + it.amount }) { tx ->
                TransactionRow(transaction = tx)
            }
        }
        Text(
            text = "${state.totalCount} Transactions ready to import",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Button(
            onClick = { viewModel.perform(TransactionsPreviewViewModel.Action.Confirm) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(text = "Confirm & Import")
        }
    }
}

@Composable
private fun TransactionRow(transaction: TransactionsPreviewViewModel.DisplayTransaction) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            text = transaction.primaryText,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "${transaction.amount} · ${transaction.accountName} · ${transaction.date}",
            fontSize = 12.sp,
        )
    }
}
