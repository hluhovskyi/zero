package com.hluhovskyi.zero.transactions.edit.common

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.hluhovskyi.zero.R
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
            Text(text = stringResource(R.string.transaction_edit_account_label))
        },
        nameMapping = { it.name },
        selectedItem = selectedAccount,
        onItemSelected = onAccountSelected,
    )
}
