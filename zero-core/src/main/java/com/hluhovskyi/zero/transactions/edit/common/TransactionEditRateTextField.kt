package com.hluhovskyi.zero.transactions.edit.common

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.hluhovskyi.zero.R
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun TransactionEditRateTextField(
    modifier: Modifier,
    rate: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        modifier = modifier,
        value = rate,
        label = { Text(text = stringResource(R.string.transaction_edit_rate_label)) },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
        ),
        onValueChange = onValueChange,
    )
}
