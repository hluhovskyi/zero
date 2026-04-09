package com.hluhovskyi.zero.transactions.edit.common

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun TransactionEditAmountTextField(
    modifier: Modifier,
    amount: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        modifier = modifier,
        value = amount,
        label = { Text(text = "Amount") },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
        ),
        onValueChange = onValueChange,
    )
}
