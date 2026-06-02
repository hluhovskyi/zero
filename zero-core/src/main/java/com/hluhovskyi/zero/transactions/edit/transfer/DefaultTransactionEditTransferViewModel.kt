package com.hluhovskyi.zero.transactions.edit.transfer

import com.hluhovskyi.zero.transactions.edit.TransactionEditUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map

internal class DefaultTransactionEditTransferViewModel(
    private val useCase: TransactionEditUseCase,
) : TransactionEditTransferViewModel {

    override val state: Flow<TransactionEditTransferViewModel.State> = useCase.state
        .filterIsInstance<TransactionEditUseCase.State.Transfer>()
        .map { state ->
            val needsFx = state.selectedAccount != null && state.selectedTargetAccount != null &&
                state.selectedAccount.currencyId != state.selectedTargetAccount.currencyId
            TransactionEditTransferViewModel.State(
                accounts = state.accounts,
                selectedAccount = state.selectedAccount,
                targetAccounts = state.targetAccounts,
                selectedTargetAccount = state.selectedTargetAccount,
                amount = state.amount,
                targetAmount = state.targetAmount,
                rate = state.rate,
                rateAuto = state.rateAuto,
                editTarget = state.editTarget,
                sourceCurrencySymbol = state.sourceCurrencySymbol,
                targetCurrencySymbol = state.targetCurrencySymbol,
                needsFx = needsFx,
                date = state.date,
            )
        }

    override fun perform(action: TransactionEditTransferViewModel.Action) {
        val useCaseAction = when (action) {
            is TransactionEditTransferViewModel.Action.SelectAccount ->
                TransactionEditUseCase.Action.SelectAccount(action.account)
            is TransactionEditTransferViewModel.Action.SelectTargetAccount ->
                TransactionEditUseCase.Action.SelectTargetAccount(action.account)
            is TransactionEditTransferViewModel.Action.ChangeDate ->
                TransactionEditUseCase.Action.ChangeDate(action.date)
            is TransactionEditTransferViewModel.Action.FocusAmount ->
                TransactionEditUseCase.Action.FocusAmount
            is TransactionEditTransferViewModel.Action.FocusReceived ->
                TransactionEditUseCase.Action.FocusReceived
            is TransactionEditTransferViewModel.Action.FocusRate ->
                TransactionEditUseCase.Action.FocusRate
            is TransactionEditTransferViewModel.Action.ResetRate ->
                TransactionEditUseCase.Action.ResetRate
            is TransactionEditTransferViewModel.Action.SwapAccounts ->
                TransactionEditUseCase.Action.SwapAccounts
        }
        useCase.perform(useCaseAction)
    }
}
