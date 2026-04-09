package com.hluhovskyi.zero.transactions.edit.common

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hluhovskyi.zero.transactions.edit.TransactionEditAccount
import com.hluhovskyi.zero.ui.TextFieldDropdownMenu

@Composable
fun TransactionEditAccountSelect(
    modifier: Modifier = Modifier,
    accounts: List<TransactionEditAccount>,
    selectedAccount: TransactionEditAccount?,
    onAccountSelected: (TransactionEditAccount) -> Unit,
) {
    TextFieldDropdownMenu(
        modifier = modifier,
        items = accounts,
        label = {
            Text(text = "Account")
        },
        nameMapping = { it.name },
        selectedItem = selectedAccount,
        onItemSelected = onAccountSelected,
    )
}
