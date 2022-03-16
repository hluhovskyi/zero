package com.hluhovskyi.zero.transactions.edit.expense

import com.hluhovskyi.zero.transactions.edit.TransactionEditUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map

internal class DefaultTransactionEditExpenseViewModel(
    private val useCase: TransactionEditUseCase
): TransactionEditExpenseViewModel {

    override val state: Flow<TransactionEditExpenseViewModel.State> = useCase.state
        .filterIsInstance<TransactionEditUseCase.State.Expense>()
        .map { state ->
            TransactionEditExpenseViewModel.State(
                accounts = state.accounts,
                selectedAccount = state.selectedAccount,
                categories = state.categories,
                selectedCategory = state.selectedCategory,
                currencies = state.currencies,
                selectedCurrency = state.selectedCurrency,
                amount = state.amount,
                rate = state.rate,
            )
        }

    override fun perform(action: TransactionEditExpenseViewModel.Action) {
        val useCaseAction = when (action) {
            is TransactionEditExpenseViewModel.Action.ChangeAmount ->
                TransactionEditUseCase.Action.ChangeAmount(action.amount)
            is TransactionEditExpenseViewModel.Action.ChangeRate ->
                TransactionEditUseCase.Action.ChangeRate(action.rate)
            is TransactionEditExpenseViewModel.Action.EditCategories ->
                TransactionEditUseCase.Action.EditCategories
            is TransactionEditExpenseViewModel.Action.SelectAccount ->
                TransactionEditUseCase.Action.SelectAccount(action.account)
            is TransactionEditExpenseViewModel.Action.SelectCategory ->
                TransactionEditUseCase.Action.SelectCategory(action.category)
            is TransactionEditExpenseViewModel.Action.SelectCurrency ->
                TransactionEditUseCase.Action.SelectCurrency(action.currency)
        }
        useCase.perform(useCaseAction)
    }
}