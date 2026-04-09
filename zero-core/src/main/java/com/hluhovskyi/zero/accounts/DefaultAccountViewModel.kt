package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.common.BaseViewModel
import com.hluhovskyi.zero.common.coroutines.DispatcherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class DefaultAccountViewModel(
    private val useCase: AccountUseCase,
    private val dispatchers: DispatcherProvider,
) : BaseViewModel(dispatchers),
    AccountViewModel {

    private val mutableState = MutableStateFlow(AccountViewModel.State())
    override val state: Flow<AccountViewModel.State> = mutableState

    override fun perform(action: AccountViewModel.Action) {
        when (action) {
            else -> {}
        }
    }

    override fun attachOnMain() {
        scope.launch {
            useCase.state
                .map { it.accounts }
                .collectLatest { accounts ->
                    mutableState.update { state ->
                        state.copy(
                            accounts = accounts,
                        )
                    }
                }
        }
    }
}
