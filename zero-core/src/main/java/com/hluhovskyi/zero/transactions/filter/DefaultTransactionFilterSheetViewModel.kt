package com.hluhovskyi.zero.transactions.filter

import com.hluhovskyi.zero.common.OnBackHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultTransactionFilterSheetViewModel(
    availableCategories: List<TransactionFilterSheetViewModel.FilterCategory>,
    availableAccounts: List<TransactionFilterSheetViewModel.FilterAccount>,
    private val transactionFilterUseCase: TransactionFilterUseCase,
    private val onBackHandler: OnBackHandler,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : TransactionFilterSheetViewModel {

    private val mutableState = MutableStateFlow(
        TransactionFilterSheetViewModel.State(
            activeFilter = transactionFilterUseCase.activeFilter.value,
            availableCategories = availableCategories,
            availableAccounts = availableAccounts,
        )
    )
    override val state: Flow<TransactionFilterSheetViewModel.State> = mutableState

    override fun perform(action: TransactionFilterSheetViewModel.Action) {
        when (action) {
            is TransactionFilterSheetViewModel.Action.Apply -> {
                transactionFilterUseCase.apply(action.filter)
                coroutineScope.launch(Dispatchers.Main) { onBackHandler.onBack() }
            }
            TransactionFilterSheetViewModel.Action.Close -> {
                coroutineScope.launch(Dispatchers.Main) { onBackHandler.onBack() }
            }
        }
    }

    override fun attach(): Closeable = Closeable { }
}
