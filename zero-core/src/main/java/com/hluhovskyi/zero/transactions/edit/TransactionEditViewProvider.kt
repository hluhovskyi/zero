package com.hluhovskyi.zero.transactions.edit

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
import com.hluhovskyi.zero.common.Account
import com.hluhovskyi.zero.common.Category
import com.hluhovskyi.zero.common.Currency
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.TextFieldDropdownMenu

internal class TransactionEditViewProvider(
    private val viewModel: TransactionEditViewModel
) : ViewProvider {

    @Composable
    override fun View() {
        TransactionEditView(viewModel)
    }
}

@Composable
private fun TransactionEditView(
    viewModel: TransactionEditViewModel
) {
    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        val state by viewModel.state.collectAsState(initial = TransactionEditViewModel.State())
        AccountSelect(
            accounts = state.accounts,
            selectedAccount = state.selectedAccount,
            onAccountSelected = {
                viewModel.perform(TransactionEditViewModel.Action.SelectAccount(it))
            }
        )
        Row(
            modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            CategorySelect(
                modifier = Modifier.weight(1f),
                categories = state.categories,
                selectedCategory = state.selectedCategory,
                onCategorySelected = {
                    viewModel.perform(TransactionEditViewModel.Action.SelectCategory(it))
                }
            )
            Button(
                modifier = Modifier
                    .padding(start = 16.dp, top = 8.dp)
                    .sizeIn(maxHeight = 54.dp)
                    .aspectRatio(1f, true),
                onClick = {
                    viewModel.perform(TransactionEditViewModel.Action.EditCategories)
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Edit categories",
                    tint = Color.White
                )
            }
        }
        CurrencySelect(
            modifier = Modifier.padding(top = 16.dp),
            currencies = state.currencies,
            selectedCurrency = state.selectedCurrency,
            onCurrencySelected = {
                viewModel.perform(TransactionEditViewModel.Action.SelectCurrency(it))
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
                viewModel.perform(TransactionEditViewModel.Action.ChangeAmount(amount))
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
                    viewModel.perform(TransactionEditViewModel.Action.ChangeRate(rate))
                }
            )
        }
        Button(
            modifier = Modifier.padding(top = 16.dp),
            onClick = { viewModel.perform(TransactionEditViewModel.Action.Save) }
        ) {
            Text(text = "Save")
        }
    }
}

@Composable
private fun AccountSelect(
    modifier: Modifier = Modifier,
    accounts: List<Account>,
    selectedAccount: Account?,
    onAccountSelected: (Account) -> Unit
) {
    TextFieldDropdownMenu(
        modifier = modifier,
        items = accounts,
        label = {
            Text(text = "Account")
        },
        nameMapping = { it.name },
        selectedItem = selectedAccount,
        onItemSelected = onAccountSelected
    )
}

@Composable
private fun CategorySelect(
    modifier: Modifier = Modifier,
    categories: List<Category>,
    selectedCategory: Category?,
    onCategorySelected: (Category) -> Unit
) {
    TextFieldDropdownMenu(
        modifier = modifier,
        items = categories,
        label = {
            Text(text = "Category")
        },
        nameMapping = { it.name },
        selectedItem = selectedCategory,
        onItemSelected = onCategorySelected
    )
}

@Composable
private fun CurrencySelect(
    modifier: Modifier = Modifier,
    currencies: List<Currency>,
    selectedCurrency: Currency?,
    onCurrencySelected: (Currency) -> Unit
) {
    TextFieldDropdownMenu(
        modifier = modifier,
        items = currencies,
        label = {
            Text(text = "Currency")
        },
        nameMapping = { it.name },
        selectedItem = selectedCurrency,
        onItemSelected = onCurrencySelected
    )
}