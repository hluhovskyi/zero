package com.hluhovskyi.zero.accounts.edit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hluhovskyi.zero.common.Currency
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.TextFieldDropdownMenu

internal class AccountEditViewProvider(
    private val viewModel: AccountEditViewModel
) : ViewProvider {

    @Composable
    override fun View() {
        AccountEditView(
            viewModel = viewModel
        )
    }
}

@Composable
private fun AccountEditView(
    viewModel: AccountEditViewModel
) {
    val state by viewModel.state.collectAsState(initial = AccountEditViewModel.State())
    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.name,
            label = { Text(text = "Name") },
            onValueChange = { name ->
                viewModel.perform(AccountEditViewModel.Action.ChangeName(name))
            }
        )
        CurrencySelect(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            currencies = state.currencies,
            selectedCurrency = state.selectedCurrency,
            onCurrencySelected = { currency ->
                viewModel.perform(AccountEditViewModel.Action.SelectCurrency(currency))
            }
        )
        Button(
            modifier = Modifier.padding(top = 16.dp),
            onClick = { viewModel.perform(AccountEditViewModel.Action.Save) }
        ) {
            Text(text = "Save account")
        }
    }
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