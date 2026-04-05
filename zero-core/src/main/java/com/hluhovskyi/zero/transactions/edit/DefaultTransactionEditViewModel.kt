package com.hluhovskyi.zero.transactions.edit

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

private const val TAG = "DefaultTransactionEditViewModel"

internal class DefaultTransactionEditViewModel(
    private val useCase: TransactionEditUseCase
) : TransactionEditViewModel {

    private val _showCategoryPicker = MutableStateFlow(false)

    override val state: Flow<TransactionEditViewModel.State> = combine(
        useCase.state,
        _showCategoryPicker
    ) { state, showPicker ->
        val selectedCategory = when (state) {
            is TransactionEditUseCase.State.Expense -> state.selectedCategory
            is TransactionEditUseCase.State.Income -> state.selectedCategory
            is TransactionEditUseCase.State.Transfer -> null
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
            showCategoryPicker = showPicker,
        )
    }

    override fun perform(action: TransactionEditViewModel.Action) {
        when (action) {
            is TransactionEditViewModel.Action.ShowAllCategories ->
                _showCategoryPicker.value = true

            is TransactionEditViewModel.Action.DismissCategoryPicker ->
                _showCategoryPicker.value = false

            else -> {
                val useCaseAction = when (action) {
                    is TransactionEditViewModel.Action.ChangeTransactionType ->
                        TransactionEditUseCase.Action.SwitchTransaction(action.type)
                    is TransactionEditViewModel.Action.ChangeDate ->
                        TransactionEditUseCase.Action.ChangeDate(action.date)
                    is TransactionEditViewModel.Action.Save ->
                        TransactionEditUseCase.Action.Save
                    is TransactionEditViewModel.Action.Discard ->
                        TransactionEditUseCase.Action.Discard
                    else -> error("Unsupported action: $action")
                }
                useCase.perform(useCaseAction)
            }
        }
    }
}