package com.hluhovskyi.zero.transactions.edit.transfer

import com.hluhovskyi.zero.transactions.edit.TransactionEditUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map

internal class DefaultTransactionEditTransferViewModel(
    private val useCase: TransactionEditUseCase
) : TransactionEditTransferViewModel {

    override val state: Flow<TransactionEditTransferViewModel.State> = useCase.state
        .filterIsInstance<TransactionEditUseCase.State.Transfer>()
        .map { state ->
            TransactionEditTransferViewModel.State(
                accounts = state.accounts,
                selectedAccount = state.selectedAccount,
                targetAccounts = state.targetAccounts,
                selectedTargetAccount = state.selectedTargetAccount,
                amount = state.amount,
                targetAmount = state.targetAmount,
                transferRateMode = state.transferRateMode,
                sourceCurrencySymbol = state.sourceCurrencySymbol,
                targetCurrencySymbol = state.targetCurrencySymbol,
                date = state.date,
            )
        }

    override fun perform(action: TransactionEditTransferViewModel.Action) {
        val useCaseAction = when (action) {
            is TransactionEditTransferViewModel.Action.ChangeAmount ->
                TransactionEditUseCase.Action.ChangeAmount(action.amount)
            is TransactionEditTransferViewModel.Action.SelectAccount ->
                TransactionEditUseCase.Action.SelectAccount(action.account)
            is TransactionEditTransferViewModel.Action.SelectTargetAccount ->
                TransactionEditUseCase.Action.SelectTargetAccount(action.account)
            is TransactionEditTransferViewModel.Action.ChangeTargetAmount ->
                TransactionEditUseCase.Action.ChangeTargetAmount(action.amount)
            is TransactionEditTransferViewModel.Action.ChangeTransferRate ->
                TransactionEditUseCase.Action.ChangeTransferRate(action.rate)
            is TransactionEditTransferViewModel.Action.ChangeDate ->
                TransactionEditUseCase.Action.ChangeDate(action.date)
            is TransactionEditTransferViewModel.Action.CycleRateMode ->
                TransactionEditUseCase.Action.CycleTransferRateMode
            is TransactionEditTransferViewModel.Action.SwapAccounts ->
                TransactionEditUseCase.Action.SwapAccounts
        }
        useCase.perform(useCaseAction)
    }
}
