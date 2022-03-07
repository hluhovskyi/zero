package com.hluhovskyi.zero.transactions.edit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hluhovskyi.zero.common.Account
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
        Row {
            AccountSelect(
                accounts = state.accounts,
                selectedAccount = state.selectedAccount,
                onAccountSelected = {
                    viewModel.action(TransactionEditViewModel.Action.SelectAccount(it))
                }
            )
        }
        Row(
            modifier = Modifier.padding(top = 16.dp)
        ) {
            CurrencySelect(
                currencies = state.currencies,
                selectedCurrency = state.selectedCurrency,
                onCurrencySelected = {
                    viewModel.action(TransactionEditViewModel.Action.SelectCurrency(it))
                }
            )
        }
        Row(
            modifier = Modifier.padding(top = 16.dp)
        ) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.amount,
                label = { Text(text = "Amount") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                onValueChange = {
                    viewModel.action(
                        TransactionEditViewModel.Action.ChangeAmount(
                            amount = it
                        )
                    )
                }
            )
        }
        val isVisible = if (state.selectedCurrency != null && state.selectedAccount != null) {
            state.selectedCurrency?.id != state.selectedAccount?.currencyId
        } else {
            false
        }
        AnimatedVisibility(visible = isVisible) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth()
                    .padding(top = 16.dp),
                value = state.rate,
                label = { Text(text = "Rate") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                onValueChange = { rate ->
                    viewModel.action(
                        TransactionEditViewModel.Action.ChangeRate(rate)
                    )
                }
            )
        }
        Row(
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Button(
                onClick = {
                    viewModel.action(TransactionEditViewModel.Action.Save)
                }
            ) {
                Text(text = "Save")
            }
        }

    }
}

@Composable
private fun AccountSelect(
    accounts: List<Account>,
    selectedAccount: Account?,
    onAccountSelected: (Account) -> Unit
) {
    TextFieldDropdownMenu(
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
private fun CurrencySelect(
    currencies: List<Currency>,
    selectedCurrency: Currency?,
    onCurrencySelected: (Currency) -> Unit
) {
    TextFieldDropdownMenu(
        items = currencies,
        label = {
            Text(text = "Currency")
        },
        nameMapping = { it.name },
        selectedItem = selectedCurrency,
        onItemSelected = onCurrencySelected
    )
}