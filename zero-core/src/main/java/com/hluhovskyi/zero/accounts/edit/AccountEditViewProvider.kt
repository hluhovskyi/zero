package com.hluhovskyi.zero.accounts.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExtendedFloatingActionButton
import androidx.compose.material.FloatingActionButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.hluhovskyi.zero.accounts.AccountCategory
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.transactions.edit.common.AmountDisplay
import com.hluhovskyi.zero.ui.ModalHeader
import com.hluhovskyi.zero.ui.SelectorCard

internal class AccountEditViewProvider(
    private val viewModel: AccountEditViewModel,
    private val onClose: OnCloseHandler,
) : ViewProvider {

    @Composable
    override fun View() {
        AccountEditView(
            viewModel = viewModel,
            onClose = onClose,
        )
    }
}

@Composable
private fun AccountEditView(
    viewModel: AccountEditViewModel,
    onClose: OnCloseHandler,
) {
    val state by viewModel.state.collectAsState(initial = AccountEditViewModel.State())
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column {
            ModalHeader(
                title = "New Account",
                onClose = { onClose.onClose() },
            )
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
            ) {
                AmountDisplay(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp, bottom = 40.dp),
                    amount = state.balance,
                    currencySymbol = state.selectedCurrency?.symbol ?: "",
                    focusRequester = focusRequester,
                    showCurrencySelector = false,
                    onAmountChange = { balance ->
                        viewModel.perform(AccountEditViewModel.Action.ChangeBalance(balance))
                    },
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SelectorCard(
                        modifier = Modifier.weight(1f),
                        label = "Currency",
                        value = state.selectedCurrency?.name ?: "",
                        items = state.currencies,
                        nameMapping = { it.name },
                        onItemSelected = { currency ->
                            viewModel.perform(AccountEditViewModel.Action.SelectCurrency(currency))
                        },
                    )
                    SelectorCard(
                        modifier = Modifier.weight(1f),
                        label = "Account Type",
                        value = state.category.displayName,
                        items = AccountCategory.entries,
                        nameMapping = { it.displayName },
                        onItemSelected = { category ->
                            viewModel.perform(AccountEditViewModel.Action.ChangeCategory(category))
                        },
                    )
                }

                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    value = state.name,
                    label = { Text(text = "Account Name") },
                    onValueChange = { name ->
                        viewModel.perform(AccountEditViewModel.Action.ChangeName(name))
                    },
                )

                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 96.dp),
                    value = state.details,
                    label = { Text(text = "Details") },
                    onValueChange = { details ->
                        viewModel.perform(AccountEditViewModel.Action.ChangeDetails(details))
                    },
                )
            }
        }

        ExtendedFloatingActionButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 32.dp),
            icon = { Icon(Icons.Filled.Check, contentDescription = "Save account") },
            text = { Text("Save") },
            onClick = { viewModel.perform(AccountEditViewModel.Action.Save) },
            elevation = FloatingActionButtonDefaults.elevation(8.dp),
        )
    }
}

private val AccountCategory.displayName: String
    get() = when (this) {
        AccountCategory.CASH -> "Cash"
        AccountCategory.BANK -> "Bank"
        AccountCategory.CREDIT_CARDS -> "Credit Cards"
        AccountCategory.DIGITAL_WALLETS -> "Digital Wallets"
        AccountCategory.CRYPTO -> "Crypto"
        AccountCategory.OTHER -> "Other"
    }
