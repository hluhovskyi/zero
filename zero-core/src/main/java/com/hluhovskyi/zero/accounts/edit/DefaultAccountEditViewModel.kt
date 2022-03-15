package com.hluhovskyi.zero.accounts.edit

import com.hluhovskyi.zero.common.Closeables
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultAccountEditViewModel(
    private val coroutineScope: CoroutineScope = CoroutineScope(context = Dispatchers.IO)
) : AccountEditViewModel {

    private val mutableState = MutableStateFlow(AccountEditViewModel.State())
    override val state: Flow<AccountEditViewModel.State> = mutableState

    override fun perform(action: AccountEditViewModel.Action) {
        when(action) {

        }
    }

    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch {

        }
    }
}