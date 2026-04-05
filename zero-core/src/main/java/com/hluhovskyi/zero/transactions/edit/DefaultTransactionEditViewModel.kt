package com.hluhovskyi.zero.transactions.edit

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val TAG = "DefaultTransactionEditViewModel"

internal class DefaultTransactionEditViewModel(
    private val useCase: TransactionEditUseCase
) : TransactionEditViewModel {

    override val state: Flow<TransactionEditViewModel.State> = useCase.state
        .map { state ->
            val (selectedCategory, showCategoryPicker) = when (state) {
                is TransactionEditUseCase.State.Expense -> state.selectedCategory to state.showCategoryPicker
                is TransactionEditUseCase.State.Income -> state.selectedCategory to state.showCategoryPicker
                is TransactionEditUseCase.State.Transfer -> null to false
            }
            TransactionEditViewModel.State(
                transactionTypes = TransactionEditType.values().toList(),
                selectedTransactionType = when (state) {
                    is TransactionEditUseCase.State.Expense -> TransactionEditType.EXPENSE
                    is TransactionEditUseCase.State.Income -> TransactionEditType.INCOME
                    is TransactionEditUseCase.State.Transfer -> TransactionEditType.TRANSFER
                },
                date = state.date,
                selectedCategory = selectedCategory,
                showCategoryPicker = showCategoryPicker,
            )
        }

    override fun perform(action: TransactionEditViewModel.Action) {
        val useCaseAction = when (action) {
            is TransactionEditViewModel.Action.ChangeTransactionType ->
                TransactionEditUseCase.Action.SwitchTransaction(action.type)

            is TransactionEditViewModel.Action.ChangeDate ->
                TransactionEditUseCase.Action.ChangeDate(action.date)

            is TransactionEditViewModel.Action.Save ->
                TransactionEditUseCase.Action.Save

            is TransactionEditViewModel.Action.Discard ->
                TransactionEditUseCase.Action.Discard

            is TransactionEditViewModel.Action.DismissCategoryPicker ->
                TransactionEditUseCase.Action.DismissCategoryPicker
        }
        useCase.perform(useCaseAction)
    }
}