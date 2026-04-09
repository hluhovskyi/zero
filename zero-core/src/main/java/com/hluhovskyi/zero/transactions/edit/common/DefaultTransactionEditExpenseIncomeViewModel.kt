package com.hluhovskyi.zero.transactions.edit.common

import com.hluhovskyi.zero.transactions.edit.TransactionEditUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

internal class DefaultTransactionEditExpenseIncomeViewModel(
    private val useCase: TransactionEditUseCase,
) : TransactionEditExpenseIncomeViewModel {

    override val state: Flow<TransactionEditExpenseIncomeViewModel.State> = useCase.state
        .filter { it is TransactionEditUseCase.State.Expense || it is TransactionEditUseCase.State.Income }
        .map { state ->
            when (state) {
                is TransactionEditUseCase.State.Expense -> TransactionEditExpenseIncomeViewModel.State(
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
                is TransactionEditUseCase.State.Income -> TransactionEditExpenseIncomeViewModel.State(
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

    override fun perform(action: TransactionEditExpenseIncomeViewModel.Action) {
        val useCaseAction = when (action) {
            is TransactionEditExpenseIncomeViewModel.Action.ChangeAmount ->
                TransactionEditUseCase.Action.ChangeAmount(action.amount)
            is TransactionEditExpenseIncomeViewModel.Action.ChangeRate ->
                TransactionEditUseCase.Action.ChangeRate(action.rate)
            is TransactionEditExpenseIncomeViewModel.Action.ChangeDate ->
                TransactionEditUseCase.Action.ChangeDate(action.date)
            is TransactionEditExpenseIncomeViewModel.Action.EditCategories ->
                TransactionEditUseCase.Action.EditCategories
            is TransactionEditExpenseIncomeViewModel.Action.SelectAccount ->
                TransactionEditUseCase.Action.SelectAccount(action.account)
            is TransactionEditExpenseIncomeViewModel.Action.SelectCategory ->
                TransactionEditUseCase.Action.SelectCategory(action.category)
            is TransactionEditExpenseIncomeViewModel.Action.SelectCurrency ->
                TransactionEditUseCase.Action.SelectCurrency(action.currency)
            is TransactionEditExpenseIncomeViewModel.Action.ShowAllCategories ->
                TransactionEditUseCase.Action.ShowAllCategories
        }
        useCase.perform(useCaseAction)
    }
}
