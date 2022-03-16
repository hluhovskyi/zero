package com.hluhovskyi.zero.transactions.edit.common

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hluhovskyi.zero.transactions.edit.TransactionEditCurrency
import com.hluhovskyi.zero.ui.TextFieldDropdownMenu

@Composable
internal fun TransactionEditCurrencySelect(
    modifier: Modifier = Modifier,
    currencies: List<TransactionEditCurrency>,
    selectedCurrency: TransactionEditCurrency?,
    onCurrencySelected: (TransactionEditCurrency) -> Unit
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