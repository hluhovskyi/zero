package com.hluhovskyi.zero.accounts.edit

import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.currencies.CurrencyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultAccountEditViewModel(
    private val accountRepository: AccountRepository,
    private val currencyRepository: CurrencyRepository,
    private val onAccountSavedHandler: OnAccountSavedHandler,
    private val coroutineScope: CoroutineScope = CoroutineScope(context = Dispatchers.IO)
) : AccountEditViewModel {

    private val mutableState = MutableStateFlow(AccountEditViewModel.State())
    override val state: Flow<AccountEditViewModel.State> = mutableState

    override fun perform(action: AccountEditViewModel.Action) {
        when (action) {
            is AccountEditViewModel.Action.ChangeName -> mutableState.update { state ->
                state.copy(name = action.name)
            }
            is AccountEditViewModel.Action.SelectCurrency -> mutableState.update { state ->
                state.copy(selectedCurrency = action.currency)
            }
            is AccountEditViewModel.Action.Save -> coroutineScope.launch {
                val state = mutableState.value
                val selectedCurrency = state.selectedCurrency ?: return@launch
                accountRepository.insert(AccountRepository.AccountInsert(
                    name = state.name,
                    currencyId = selectedCurrency.id
                ))
                launch(context = Dispatchers.Main) {
                    onAccountSavedHandler.onSaved()
                }
            }
        }
    }

    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch {
            currencyRepository.query(CurrencyRepository.Criteria.All())
                .collectLatest { currencies ->
                    mutableState.update { state ->
                        state.copy(
                            currencies = currencies,
                            selectedCurrency = state.selectedCurrency ?: currencies.firstOrNull()
                        )
                    }
                }
        }
    }
}