package com.hluhovskyi.zero.transactions.edit

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.accounts.StubAccountRepository
import com.hluhovskyi.zero.common.Account
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Currency
import com.hluhovskyi.zero.common.IdGenerator
import com.hluhovskyi.zero.common.Rate
import com.hluhovskyi.zero.common.Transaction
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.currencies.CurrencyRepository
import com.hluhovskyi.zero.currencies.StubCurrencyRepository
import com.hluhovskyi.zero.transactions.TransactionRepository
import com.hluhovskyi.zero.ui.TextFieldDropdownMenu
import kotlinx.coroutines.launch

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
                    viewModel.action(TransactionEditViewModel.Action.ChangeAmount(
                        amount = it
                    ))
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