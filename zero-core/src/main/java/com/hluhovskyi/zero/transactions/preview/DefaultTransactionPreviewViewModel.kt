package com.hluhovskyi.zero.transactions.preview

import com.hluhovskyi.zero.common.BaseViewModel
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.coroutines.DispatcherProvider
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

internal class DefaultTransactionPreviewViewModel(
    private val transactionId: Id,
    private val transactionRepository: TransactionRepository,
    dispatchers: DispatcherProvider
) : BaseViewModel(dispatchers), TransactionPreviewViewModel {

    private val mutableState = MutableStateFlow(TransactionPreviewViewModel.State())
    override val state: Flow<TransactionPreviewViewModel.State> = mutableState

    override fun perform(action: TransactionPreviewViewModel.Action) {
        when (action) {

        }
    }

    override fun attachOnMain() {
        scope.launch {
        }
    }
}