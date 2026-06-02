package com.hluhovskyi.zero.transactions.edit.transfer

import com.hluhovskyi.zero.common.ActionStateModel
import com.hluhovskyi.zero.transactions.edit.TransactionEditAccount
import com.hluhovskyi.zero.transactions.edit.TransactionEditFocusTarget
import kotlinx.datetime.LocalDateTime

interface TransactionEditTransferViewModel : ActionStateModel<TransactionEditTransferViewModel.Action, TransactionEditTransferViewModel.State> {

    sealed interface Action {
        data class SelectAccount(val account: TransactionEditAccount) : Action
        data class SelectTargetAccount(val account: TransactionEditAccount) : Action
        data class ChangeDate(val date: LocalDateTime) : Action
        object FocusAmount : Action
        object FocusReceived : Action
        object FocusRate : Action
        object ResetRate : Action
        object SwapAccounts : Action
    }

    data class State(
        val accounts: List<TransactionEditAccount> = emptyList(),
        val selectedAccount: TransactionEditAccount? = null,
        val targetAccounts: List<TransactionEditAccount> = emptyList(),
        val selectedTargetAccount: TransactionEditAccount? = null,
        val amount: String = "",
        val targetAmount: String = "",
        val rate: String = "",
        val rateAuto: Boolean = true,
        val editTarget: TransactionEditFocusTarget = TransactionEditFocusTarget.Amount,
        val sourceCurrencySymbol: String = "",
        val targetCurrencySymbol: String = "",
        val needsFx: Boolean = false,
        val date: LocalDateTime? = null,
    )
}
