package com.hluhovskyi.zero.transactions.edit.expense

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.transactions.edit.common.TransactionEditAccountSelect
import com.hluhovskyi.zero.transactions.edit.common.TransactionEditAmountTextField
import com.hluhovskyi.zero.transactions.edit.common.TransactionEditCategorySelectWithEditButton
import com.hluhovskyi.zero.transactions.edit.common.TransactionEditCurrencySelect
import com.hluhovskyi.zero.transactions.edit.common.TransactionEditRateTextField

internal class TransactionEditExpenseViewProvider(
    private val viewModel: TransactionEditExpenseViewModel,
    private val imageLoader: ImageLoader,
) : ViewProvider {

    @Composable
    override fun View() {
        TransactionEditExpenseView(
            viewModel = viewModel,
            imageLoader = imageLoader
        )
    }
}

@Composable
private fun TransactionEditExpenseView(
    viewModel: TransactionEditExpenseViewModel,
    imageLoader: ImageLoader
) {
    Column {
        val defaultModifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        val state by viewModel.state.collectAsState(initial = TransactionEditExpenseViewModel.State())
        TransactionEditAccountSelect(
            modifier = defaultModifier,
            accounts = state.accounts,
            selectedAccount = state.selectedAccount,
            onAccountSelected = {
                viewModel.perform(TransactionEditExpenseViewModel.Action.SelectAccount(it))
            }
        )
        TransactionEditCategorySelectWithEditButton(
            modifier = defaultModifier,
            imageLoader = imageLoader,
            categories = state.categories,
            selectedCategory = state.selectedCategory,
            onCategorySelected = {
                viewModel.perform(TransactionEditExpenseViewModel.Action.SelectCategory(it))
            },
            onCategoryEdit = {
                viewModel.perform(TransactionEditExpenseViewModel.Action.EditCategories)
            }
        )
        TransactionEditCurrencySelect(
            modifier = defaultModifier,
            currencies = state.currencies,
            selectedCurrency = state.selectedCurrency,
            onCurrencySelected = {
                viewModel.perform(TransactionEditExpenseViewModel.Action.SelectCurrency(it))
            }
        )
        TransactionEditAmountTextField(
            modifier = defaultModifier,
            amount = state.amount,
            onValueChange = {
                viewModel.perform(TransactionEditExpenseViewModel.Action.ChangeAmount(it))
            }
        )
        AnimatedVisibility(visible = state.showRate) {
            TransactionEditRateTextField(
                modifier = defaultModifier,
                rate = state.rate,
                onValueChange = { rate ->
                    viewModel.perform(TransactionEditExpenseViewModel.Action.ChangeRate(rate))
                }
            )
        }
    }
}