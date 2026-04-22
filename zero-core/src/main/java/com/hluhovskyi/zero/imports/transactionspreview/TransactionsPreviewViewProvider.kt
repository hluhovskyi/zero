package com.hluhovskyi.zero.imports.transactionspreview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.ImportStepHeader
import com.hluhovskyi.zero.ui.theme.OnSurface
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant
import com.hluhovskyi.zero.ui.theme.Secondary

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
        ImportStepHeader(
            title = "Review Transactions",
            step = 3,
            totalSteps = 4,
            onBack = { viewModel.perform(TransactionsPreviewViewModel.Action.Back) },
        )
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
        ) {
            item {
                Text(
                    text = "${state.totalCount} TRANSACTIONS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp, start = 4.dp),
                )
            }
            items(state.transactions, key = { it.primaryText + it.date + it.amount }) { transaction ->
                TransactionRow(transaction = transaction)
            }
            item { Box(modifier = Modifier.padding(bottom = 8.dp)) }
        }
        Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp).padding(bottom = 16.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Secondary)
                    .clickable { viewModel.perform(TransactionsPreviewViewModel.Action.Confirm) }
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Import ${state.totalCount} Transactions",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun TransactionRow(transaction: TransactionsPreviewViewModel.DisplayTransaction) {
    Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
        Text(
            text = transaction.primaryText,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = OnSurface,
        )
        Text(
            text = "${transaction.amount} · ${transaction.accountName} · ${transaction.date}",
            fontSize = 12.sp,
            color = OnSurfaceVariant,
        )
    }
}
