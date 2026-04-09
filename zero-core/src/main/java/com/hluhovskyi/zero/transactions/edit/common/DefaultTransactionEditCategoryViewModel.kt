package com.hluhovskyi.zero.transactions.edit.common

import com.hluhovskyi.zero.transactions.edit.TransactionEditUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

internal class DefaultTransactionEditCategoryViewModel(
    private val useCase: TransactionEditUseCase,
) : TransactionEditCategoryViewModel {

    override val state: Flow<TransactionEditCategoryViewModel.State> = useCase.state
        .filter { it is TransactionEditUseCase.State.Expense || it is TransactionEditUseCase.State.Income }
        .map { state ->
            when (state) {
                is TransactionEditUseCase.State.Expense -> TransactionEditCategoryViewModel.State(
                    accounts = state.accounts,
                    selectedAccount = state.selectedAccount,
                    categories = state.categories,
                    selectedCategory = state.selectedCategory,
                    currencies = state.currencies,
                    selectedCurrency = state.selectedCurrency,
                    amount = state.amount,
                    rate = state.rate,
                    date = state.date,
                )
                is TransactionEditUseCase.State.Income -> TransactionEditCategoryViewModel.State(
                    accounts = state.accounts,
                    selectedAccount = state.selectedAccount,
                    categories = state.categories,
                    selectedCategory = state.selectedCategory,
                    currencies = state.currencies,
                    selectedCurrency = state.selectedCurrency,
                    amount = state.amount,
                    rate = state.rate,
                    date = state.date,
                )
                else -> throw IllegalStateException("Unexpected state: $state")
            }
        }

    override fun perform(action: TransactionEditCategoryViewModel.Action) {
        val useCaseAction = when (action) {
            is TransactionEditCategoryViewModel.Action.ChangeAmount ->
                TransactionEditUseCase.Action.ChangeAmount(action.amount)
            is TransactionEditCategoryViewModel.Action.ChangeRate ->
                TransactionEditUseCase.Action.ChangeRate(action.rate)
            is TransactionEditCategoryViewModel.Action.ChangeDate ->
                TransactionEditUseCase.Action.ChangeDate(action.date)
            is TransactionEditCategoryViewModel.Action.EditCategories ->
                TransactionEditUseCase.Action.EditCategories
            is TransactionEditCategoryViewModel.Action.SelectAccount ->
                TransactionEditUseCase.Action.SelectAccount(action.account)
            is TransactionEditCategoryViewModel.Action.SelectCategory ->
                TransactionEditUseCase.Action.SelectCategory(action.category)
            is TransactionEditCategoryViewModel.Action.SelectCurrency ->
                TransactionEditUseCase.Action.SelectCurrency(action.currency)
            is TransactionEditCategoryViewModel.Action.ShowAllCategories ->
                TransactionEditUseCase.Action.ShowAllCategories
        }
        useCase.perform(useCaseAction)
    }
}
