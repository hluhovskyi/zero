package com.hluhovskyi.zero.transactions.edit.transfer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.transactions.edit.common.TransactionEditAccountSelect
import com.hluhovskyi.zero.transactions.edit.common.TransactionEditAmountTextField

internal class TransactionEditTransferViewProvider(
    private val viewModel: TransactionEditTransferViewModel
) : ViewProvider {

    @Composable
    override fun View() {
        TransactionEditTransferView(viewModel = viewModel)
    }
}

@Composable
private fun TransactionEditTransferView(
    viewModel: TransactionEditTransferViewModel
) {
    Column {
        val defaultModifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
        val state by viewModel.state.collectAsState(initial = TransactionEditTransferViewModel.State())
        TransactionEditAccountSelect(
            modifier = defaultModifier,
            accounts = state.accounts,
            selectedAccount = state.selectedAccount,
            onAccountSelected = {
                viewModel.perform(TransactionEditTransferViewModel.Action.SelectAccount(it))
            }
        )
        TransactionEditAccountSelect(
            modifier = defaultModifier,
            accounts = state.targetAccounts,
            selectedAccount = state.selectedTargetAccount,
            onAccountSelected = {
                viewModel.perform(TransactionEditTransferViewModel.Action.SelectTargetAccount(it))
            }
        )
        TransactionEditAmountTextField(
            modifier = defaultModifier,
            amount = state.amount,
            onValueChange = { amount ->
                viewModel.perform(TransactionEditTransferViewModel.Action.ChangeAmount(amount))
            }
        )
    }
}