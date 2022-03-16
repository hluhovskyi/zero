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
                amount = state.amount
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
        }
        useCase.perform(useCaseAction)
    }
}