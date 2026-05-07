package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface AccountDetailSpendingUseCase {

    fun queryForAccount(accountId: Id.Known, period: Period): Flow<AccountSpending?>

    data class AccountSpending(
        val totalIn: Amount,
        val totalOut: Amount,
        val transactionCount: Int,
    )

    sealed interface Period {
        object CurrentMonth : Period
    }

    object Noop : AccountDetailSpendingUseCase {
        override fun queryForAccount(accountId: Id.Known, period: Period): Flow<AccountSpending?> = emptyFlow()
    }
}
