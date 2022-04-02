package com.hluhovskyi.zero.imports.transactions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.imports.ImportTransaction

internal class ImportTransactionPreviewViewProvider(
    private val viewModel: ImportTransactionPreviewViewModel
) : ViewProvider {

    @Composable
    override fun View() {
        ImportTransactionPreviewView(viewModel)
    }
}

@Composable
private fun ImportTransactionPreviewView(
    viewModel: ImportTransactionPreviewViewModel
) {
    val state by viewModel.state.collectAsState(initial = ImportTransactionPreviewViewModel.State())
    Column {
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.items) { item ->
                when (item) {
                    is ImportTransaction.Expense -> {
                        Text(text = "Expense -> -${item.amount.value.toPlainString()}${item.currencyId.value}")
                    }
                    is ImportTransaction.Income -> {
                        Text(text = "Expense <- +${item.amount.value.toPlainString()}${item.currencyId.value}")
                    }
                    is ImportTransaction.Transfer -> {
                        Text(text = "Transfer <-> ${item.amount.value.toPlainString()}${item.currencyId.value}")
                    }
                }
            }
        }
        Button(onClick = { viewModel.perform(ImportTransactionPreviewViewModel.Action.Submit) }) {
            Text(text = "Submit")
        }
    }


}