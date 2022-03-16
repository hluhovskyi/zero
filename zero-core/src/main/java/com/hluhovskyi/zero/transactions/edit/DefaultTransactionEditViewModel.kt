package com.hluhovskyi.zero.transactions.edit

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val TAG = "DefaultTransactionEditViewModel"

internal class DefaultTransactionEditViewModel(
    private val useCase: TransactionEditUseCase
) : TransactionEditViewModel {
    override val state: Flow<TransactionEditViewModel.State> = useCase.state
        .map { state ->
            TransactionEditViewModel.State(
                transactionTypes = TransactionEditType.values().toList(),
                selectedTransactionType = when (state) {
                    is TransactionEditUseCase.State.Expense -> TransactionEditType.EXPENSE
                    is TransactionEditUseCase.State.Income -> TransactionEditType.INCOME
                    is TransactionEditUseCase.State.Transfer -> TransactionEditType.TRANSFER
                }
            )
        }

    override fun perform(action: TransactionEditViewModel.Action) {
        val useCaseAction = when (action) {
            is TransactionEditViewModel.Action.ChangeTransactionType ->
                TransactionEditUseCase.Action.SwitchTransaction(action.type)
            is TransactionEditViewModel.Action.Save ->
                TransactionEditUseCase.Action.Save
        }

        useCase.perform(useCaseAction)
    }
}