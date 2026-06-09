package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.common.ActionStateModel
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Currency

interface AccountUseCase : ActionStateModel<AccountUseCase.Action, AccountUseCase.State> {

    sealed interface Action

    data class State(
        val balance: Amount = Amount.zero(),
        val assets: Amount = Amount.zero(),
        val liabilities: Amount = Amount.zero(),
        val currency: Currency? = null,
        val accounts: List<Account> = emptyList(),
        val netWorthTrend: List<Amount> = emptyList(),
        val netWorthChange: NetWorthChange? = null,
    )
}
