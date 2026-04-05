package com.hluhovskyi.zero.transactions.edit.income

import com.hluhovskyi.zero.transactions.edit.TransactionEditUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map

internal class DefaultTransactionEditIncomeViewModel(
    private val useCase: TransactionEditUseCase
) : TransactionEditIncomeViewModel {

    override val state: Flow<TransactionEditIncomeViewModel.State> = useCase.state
        .filterIsInstance<TransactionEditUseCase.State.Income>()
        .map { state ->
            TransactionEditIncomeViewModel.State(
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
        }

    override fun perform(action: TransactionEditIncomeViewModel.Action) {
        val useCaseAction = when (action) {
            is TransactionEditIncomeViewModel.Action.ChangeAmount ->
                TransactionEditUseCase.Action.ChangeAmount(action.amount)
            is TransactionEditIncomeViewModel.Action.ChangeRate ->
                TransactionEditUseCase.Action.ChangeRate(action.rate)
            is TransactionEditIncomeViewModel.Action.ChangeDate ->
                TransactionEditUseCase.Action.ChangeDate(action.date)
            is TransactionEditIncomeViewModel.Action.EditCategories ->
                TransactionEditUseCase.Action.EditCategories
            is TransactionEditIncomeViewModel.Action.SelectAccount ->
                TransactionEditUseCase.Action.SelectAccount(action.account)
            is TransactionEditIncomeViewModel.Action.SelectCategory ->
                TransactionEditUseCase.Action.SelectCategory(action.category)
            is TransactionEditIncomeViewModel.Action.SelectCurrency ->
                TransactionEditUseCase.Action.SelectCurrency(action.currency)
            is TransactionEditIncomeViewModel.Action.ShowAllCategories ->
                TransactionEditUseCase.Action.ShowAllCategories
        }
        useCase.perform(useCaseAction)
    }
}