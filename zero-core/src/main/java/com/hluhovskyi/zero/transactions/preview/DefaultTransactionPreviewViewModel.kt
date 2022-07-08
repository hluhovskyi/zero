package com.hluhovskyi.zero.transactions.preview

import com.hluhovskyi.zero.common.BaseViewModel
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.coroutines.DispatcherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.Closeable

internal class DefaultTransactionPreviewViewModel(
    private val dispatchers: DispatcherProvider
) : BaseViewModel(dispatchers), TransactionPreviewViewModel {

    private val mutableState = MutableStateFlow(TransactionPreviewViewModel.State())
    override val state: Flow<TransactionPreviewViewModel.State> = mutableState

    override fun perform(action: TransactionPreviewViewModel.Action) {
        when (action) {

        }
    }

    override fun attach(): Closeable = Closeables.empty()
}