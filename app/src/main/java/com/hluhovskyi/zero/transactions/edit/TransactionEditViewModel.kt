package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.common.Account
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Attachable
import com.hluhovskyi.zero.common.Currency
import kotlinx.coroutines.flow.Flow

interface TransactionEditViewModel : Attachable {

    val state: Flow<State>

    data class State(
        val accounts: List<Account> = emptyList(),
        val currencies: List<Currency> = emptyList(),
        val selectedAccount: Account? = null,
        val selectedCurrency: Currency? = null,
        val amount: String = ""
    )

    fun action(action: Action)

    sealed interface Action {
        data class SelectAccount(val account: Account) : Action
        data class SelectCurrency(val currency: Currency) : Action
        data class ChangeAmount(val amount: String) : Action
        object Save : Action
    }
}