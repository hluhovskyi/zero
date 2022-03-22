package com.hluhovskyi.zero.transactions.edit.income

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

internal class TransactionEditIncomeViewProvider(
    private val viewModel: TransactionEditIncomeViewModel,
    private val imageLoader: ImageLoader,
) : ViewProvider {

    @Composable
    override fun View() {
        TransactionEditIncomeView(
            viewModel = viewModel,
            imageLoader = imageLoader
        )
    }
}

@Composable
private fun TransactionEditIncomeView(
    viewModel: TransactionEditIncomeViewModel,
    imageLoader: ImageLoader
) {
    Column {
        val defaultModifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
        val state by viewModel.state.collectAsState(initial = TransactionEditIncomeViewModel.State())
        TransactionEditAccountSelect(
            modifier = defaultModifier,
            accounts = state.accounts,
            selectedAccount = state.selectedAccount,
            onAccountSelected = {
                viewModel.perform(TransactionEditIncomeViewModel.Action.SelectAccount(it))
            }
        )
        TransactionEditCategorySelectWithEditButton(
            modifier = defaultModifier,
            imageLoader = imageLoader,
            categories = state.categories,
            selectedCategory = state.selectedCategory,
            onCategorySelected = {
                viewModel.perform(TransactionEditIncomeViewModel.Action.SelectCategory(it))
            },
            onCategoryEdit = {
                viewModel.perform(TransactionEditIncomeViewModel.Action.EditCategories)
            }
        )
        TransactionEditCurrencySelect(
            modifier = defaultModifier,
            currencies = state.currencies,
            selectedCurrency = state.selectedCurrency,
            onCurrencySelected = {
                viewModel.perform(TransactionEditIncomeViewModel.Action.SelectCurrency(it))
            }
        )
        TransactionEditAmountTextField(
            modifier = defaultModifier,
            amount = state.amount,
            onValueChange = {
                viewModel.perform(TransactionEditIncomeViewModel.Action.ChangeAmount(it))
            }
        )
        AnimatedVisibility(visible = state.showRate) {
            TransactionEditRateTextField(
                modifier = defaultModifier,
                rate = state.rate,
                onValueChange = { rate ->
                    viewModel.perform(TransactionEditIncomeViewModel.Action.ChangeRate(rate))
                }
            )
        }
    }
}

