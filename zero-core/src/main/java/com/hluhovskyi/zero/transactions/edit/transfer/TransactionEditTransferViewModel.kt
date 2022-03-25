package com.hluhovskyi.zero.transactions.edit.transfer

import com.hluhovskyi.zero.common.ActionStateModel
import com.hluhovskyi.zero.transactions.edit.TransactionEditAccount

interface TransactionEditTransferViewModel
    : ActionStateModel<TransactionEditTransferViewModel.Action, TransactionEditTransferViewModel.State> {

    sealed interface Action {
        data class SelectAccount(val account: TransactionEditAccount) : Action
        data class SelectTargetAccount(val account: TransactionEditAccount) : Action
        data class ChangeAmount(val amount: String) : Action
    }

    data class State(
        val accounts: List<TransactionEditAccount> = emptyList(),
        val selectedAccount: TransactionEditAccount? = null,
        val targetAccounts: List<TransactionEditAccount> = emptyList(),
        val selectedTargetAccount: TransactionEditAccount? = null,
        val amount: String = ""
    )
}