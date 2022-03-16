package com.hluhovskyi.zero.transactions.edit.expense

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.transactions.edit.common.TransactionEditAccountSelect
import com.hluhovskyi.zero.transactions.edit.common.TransactionEditCategorySelect
import com.hluhovskyi.zero.transactions.edit.common.TransactionEditCurrencySelect

internal class TransactionEditExpenseViewProvider(
    private val viewModel: TransactionEditExpenseViewModel
) : ViewProvider {

    @Composable
    override fun View() {
        TransactionEditExpenseView(viewModel)
    }
}

@Composable
private fun TransactionEditExpenseView(
    viewModel: TransactionEditExpenseViewModel
) {
    Column {
        val state by viewModel.state.collectAsState(initial = TransactionEditExpenseViewModel.State())
        TransactionEditAccountSelect(
            accounts = state.accounts,
            selectedAccount = state.selectedAccount,
            onAccountSelected = {
                viewModel.perform(TransactionEditExpenseViewModel.Action.SelectAccount(it))
            }
        )
        Row(
            modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            TransactionEditCategorySelect(
                modifier = Modifier.weight(1f),
                categories = state.categories,
                selectedCategory = state.selectedCategory,
                onCategorySelected = {
                    viewModel.perform(TransactionEditExpenseViewModel.Action.SelectCategory(it))
                }
            )
            Button(
                modifier = Modifier
                    .padding(start = 16.dp, top = 8.dp)
                    .sizeIn(maxHeight = 54.dp)
                    .aspectRatio(1f, true),
                onClick = {
                    viewModel.perform(TransactionEditExpenseViewModel.Action.EditCategories)
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Edit categories",
                    tint = Color.White
                )
            }
        }
        TransactionEditCurrencySelect(
            modifier = Modifier.padding(top = 16.dp),
            currencies = state.currencies,
            selectedCurrency = state.selectedCurrency,
            onCurrencySelected = {
                viewModel.perform(TransactionEditExpenseViewModel.Action.SelectCurrency(it))
            }
        )
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            value = state.amount,
            label = { Text(text = "Amount") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number
            ),
            onValueChange = { amount ->
                viewModel.perform(TransactionEditExpenseViewModel.Action.ChangeAmount(amount))
            }
        )
        val rateVisible = if (state.selectedCurrency != null && state.selectedAccount != null) {
            state.selectedCurrency?.id != state.selectedAccount?.currencyId
        } else {
            false
        }
        AnimatedVisibility(visible = rateVisible) {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                value = state.rate,
                label = { Text(text = "Rate") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                onValueChange = { rate ->
                    viewModel.perform(TransactionEditExpenseViewModel.Action.ChangeRate(rate))
                }
            )
        }
    }
}