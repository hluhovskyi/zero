package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.common.Closeables
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultAccountViewModel(
    private val useCase: AccountUseCase,
    private val coroutineScope: CoroutineScope = CoroutineScope(context = Dispatchers.IO)
) : AccountViewModel {

    private val mutableState = MutableStateFlow(AccountViewModel.State())
    override val state: Flow<AccountViewModel.State> = mutableState

    override fun perform(action: AccountViewModel.Action) {
        when (action) {

        }
    }

    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch {
            useCase.accounts.collectLatest { accounts ->
                mutableState.update { state ->
                    state.copy(
                        accounts = accounts
                    )
                }
            }
        }
    }
}