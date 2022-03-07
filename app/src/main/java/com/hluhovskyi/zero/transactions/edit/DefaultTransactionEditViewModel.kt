package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.currencies.CurrencyRepository
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultTransactionEditViewModel(
    private val accountRepository: AccountRepository,
    private val currencyRepository: CurrencyRepository,
    private val transactionRepository: TransactionRepository,
    private val coroutineScope: CoroutineScope = CoroutineScope(context = Dispatchers.IO)
): TransactionEditViewModel {

    private val mutableState = MutableStateFlow(TransactionEditViewModel.State())
    override val state: Flow<TransactionEditViewModel.State> = mutableState

    override fun action(action: TransactionEditViewModel.Action) {
        when (action) {
            is TransactionEditViewModel.Action.ChangeAmount -> {
                mutableState.update { state ->
                    state.copy(amount = action.amount)
                }
            }
            TransactionEditViewModel.Action.Save -> {

            }
            is TransactionEditViewModel.Action.SelectAccount -> {

            }
            is TransactionEditViewModel.Action.SelectCurrency -> {

            }
        }
    }

    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch {
            accountRepository.query(AccountRepository.Criteria.All())
                .collectLatest { accounts ->
                    mutableState.update { state ->
                        state.copy(accounts = accounts)
                    }
                }

            currencyRepository.query(CurrencyRepository.Criteria.All())
                .collectLatest { currencies ->
                    mutableState.update { state ->
                        state.copy(currencies = currencies)
                    }
                }
        }
    }
}