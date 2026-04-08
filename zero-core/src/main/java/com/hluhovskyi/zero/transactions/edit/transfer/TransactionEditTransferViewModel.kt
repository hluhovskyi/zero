package com.hluhovskyi.zero.transactions.edit.transfer

import com.hluhovskyi.zero.common.ActionStateModel
import com.hluhovskyi.zero.common.Rate
import com.hluhovskyi.zero.transactions.edit.TransactionEditAccount
import com.hluhovskyi.zero.transactions.edit.TransferRateMode
import kotlinx.datetime.LocalDateTime

interface TransactionEditTransferViewModel
    : ActionStateModel<TransactionEditTransferViewModel.Action, TransactionEditTransferViewModel.State> {

    sealed interface Action {
        data class SelectAccount(val account: TransactionEditAccount) : Action
        data class SelectTargetAccount(val account: TransactionEditAccount) : Action
        data class ChangeAmount(val amount: String) : Action
        data class ChangeTargetAmount(val amount: String) : Action
        data class ChangeTransferRate(val rate: String) : Action
        data class ChangeDate(val date: LocalDateTime) : Action
        object CycleRateMode : Action
        object SwapAccounts : Action
    }

    data class State(
        val accounts: List<TransactionEditAccount> = emptyList(),
        val selectedAccount: TransactionEditAccount? = null,
        val targetAccounts: List<TransactionEditAccount> = emptyList(),
        val selectedTargetAccount: TransactionEditAccount? = null,
        val amount: String = "",
        val targetAmount: String = "",
        val transferRateMode: TransferRateMode = TransferRateMode.Default(Rate.Same),
        val sourceCurrencySymbol: String = "",
        val targetCurrencySymbol: String = "",
        val date: LocalDateTime = LocalDateTime(1970, 1, 1, 0, 0, 0),
    )
}
