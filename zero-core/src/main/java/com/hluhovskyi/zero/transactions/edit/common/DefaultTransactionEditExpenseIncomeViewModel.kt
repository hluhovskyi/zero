package com.hluhovskyi.zero.transactions.edit.common

import com.hluhovskyi.zero.transactions.edit.TransactionEditAccount
import com.hluhovskyi.zero.transactions.edit.TransactionEditCategory
import com.hluhovskyi.zero.transactions.edit.TransactionEditCurrency
import com.hluhovskyi.zero.transactions.edit.TransactionEditFocusTarget
import com.hluhovskyi.zero.transactions.edit.TransactionEditUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDateTime

internal class DefaultTransactionEditExpenseIncomeViewModel(
    private val useCase: TransactionEditUseCase,
) : TransactionEditExpenseIncomeViewModel {

    override val state: Flow<TransactionEditExpenseIncomeViewModel.State> = useCase.state
        .filter { it is TransactionEditUseCase.State.Expense || it is TransactionEditUseCase.State.Income }
        .map { state ->
            when (state) {
                is TransactionEditUseCase.State.Expense -> buildState(
                    accounts = state.accounts,
                    selectedAccount = state.selectedAccount,
                    categories = state.categories,
                    selectedCategory = state.selectedCategory,
                    currencies = state.currencies,
                    selectedCurrency = state.selectedCurrency,
                    amount = state.amount,
                    rate = state.rate,
                    rateAuto = state.rateAuto,
                    editTarget = state.editTarget,
                    convertedAmountText = state.convertedAmountText,
                    date = state.date,
                )
                is TransactionEditUseCase.State.Income -> buildState(
                    accounts = state.accounts,
                    selectedAccount = state.selectedAccount,
                    categories = state.categories,
                    selectedCategory = state.selectedCategory,
                    currencies = state.currencies,
                    selectedCurrency = state.selectedCurrency,
                    amount = state.amount,
                    rate = state.rate,
                    rateAuto = state.rateAuto,
                    editTarget = state.editTarget,
                    convertedAmountText = state.convertedAmountText,
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
            is TransactionEditExpenseIncomeViewModel.Action.FocusRate ->
                TransactionEditUseCase.Action.FocusRate
            is TransactionEditExpenseIncomeViewModel.Action.ResetRate ->
                TransactionEditUseCase.Action.ResetRate
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
            is TransactionEditExpenseIncomeViewModel.Action.ShowAllCurrencies ->
                TransactionEditUseCase.Action.ShowAllCurrencies
        }
        useCase.perform(useCaseAction)
    }

    private fun buildState(
        accounts: List<TransactionEditAccount>,
        selectedAccount: TransactionEditAccount?,
        categories: List<TransactionEditCategory>,
        selectedCategory: TransactionEditCategory?,
        currencies: List<TransactionEditCurrency>,
        selectedCurrency: TransactionEditCurrency?,
        amount: String,
        rate: String,
        rateAuto: Boolean,
        editTarget: TransactionEditFocusTarget,
        convertedAmountText: String,
        date: LocalDateTime?,
    ): TransactionEditExpenseIncomeViewModel.State {
        val accountCurrency = currencies.firstOrNull { it.id == selectedAccount?.currencyId }
        val showRate = selectedCurrency != null && selectedAccount != null &&
            selectedCurrency.id != selectedAccount.currencyId
        return TransactionEditExpenseIncomeViewModel.State(
            accounts = accounts,
            selectedAccount = selectedAccount,
            categories = categories,
            selectedCategory = selectedCategory,
            currencies = currencies,
            selectedCurrency = selectedCurrency,
            amount = amount,
            rate = rate,
            rateAuto = rateAuto,
            editTarget = editTarget,
            convertedAmountText = convertedAmountText,
            accountCurrencyName = accountCurrency?.name.orEmpty(),
            accountCurrencySymbol = accountCurrency?.currencySymbol.orEmpty(),
            txCurrencySymbol = selectedCurrency?.currencySymbol.orEmpty(),
            showRate = showRate,
            date = date,
        )
    }
}
