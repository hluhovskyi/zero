package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.common.BaseViewModel
import com.hluhovskyi.zero.common.coroutines.DispatcherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class DefaultAccountViewModel(
    private val useCase: AccountUseCase,
    private val dispatchers: DispatcherProvider,
    private val onAccountSelectedHandler: OnAccountSelectedHandler = OnAccountSelectedHandler.Noop,
    private val onEditAccountHandler: OnEditAccountHandler = OnEditAccountHandler.Noop,
    private val accountRepository: AccountRepository = AccountRepository.Noop,
) : BaseViewModel(dispatchers),
    AccountViewModel {

    private val mutableState = MutableStateFlow(AccountViewModel.State())
    override val state: Flow<AccountViewModel.State> = mutableState

    override fun perform(action: AccountViewModel.Action) {
        when (action) {
            is AccountViewModel.Action.Select -> scope.launch(dispatchers.main()) {
                onAccountSelectedHandler.onSelected(action.accountId)
            }
            is AccountViewModel.Action.Edit -> scope.launch(dispatchers.main()) {
                onEditAccountHandler.onEdit(action.accountId)
            }
            is AccountViewModel.Action.Archive -> scope.launch(dispatchers.io()) {
                accountRepository.archive(action.accountId)
            }
            is AccountViewModel.Action.Unarchive -> scope.launch(dispatchers.io()) {
                accountRepository.unarchive(action.accountId)
            }
        }
    }

    override fun attachOnMain() {
        scope.launch {
            useCase.state
                .collectLatest { useCaseState ->
                    mutableState.update { state ->
                        state.copy(
                            balance = useCaseState.balance,
                            currency = useCaseState.currency,
                            accounts = useCaseState.accounts,
                        )
                    }
                }
        }
    }
}
