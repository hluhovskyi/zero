package com.hluhovskyi.zero.transactions.filter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.transactions.TransactionFilterSheet

internal class TransactionFilterSheetViewProvider(
    private val viewModel: TransactionFilterSheetViewModel,
    private val imageLoader: ImageLoader,
) : ViewProvider {

    @Composable
    override fun View() {
        val state by viewModel.state.collectAsStateWithLifecycle(
            initialValue = TransactionFilterSheetViewModel.State(),
        )
        TransactionFilterSheet(
            activeFilter = state.activeFilter,
            availableCategories = state.availableCategories,
            availableAccounts = state.availableAccounts,
            imageLoader = imageLoader,
            onApply = { filter -> viewModel.perform(TransactionFilterSheetViewModel.Action.Apply(filter)) },
            onClose = { viewModel.perform(TransactionFilterSheetViewModel.Action.Close) },
        )
    }
}
