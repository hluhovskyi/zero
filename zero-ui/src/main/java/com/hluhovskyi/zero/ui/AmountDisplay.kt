package com.hluhovskyi.zero.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant

@Composable
fun AmountDisplay(
    modifier: Modifier = Modifier,
    amount: String,
    currencySymbol: String,
    focusRequester: FocusRequester,
    onAmountChange: (String) -> Unit,
) {
    AmountDisplayInternal(
        modifier = modifier,
        amount = amount,
        currencySymbol = currencySymbol,
        focusRequester = focusRequester,
        onAmountChange = onAmountChange,
        currencyContent = null,
    )
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun <T> AmountDisplay(
    modifier: Modifier = Modifier,
    amount: String,
    currencySymbol: String,
    focusRequester: FocusRequester,
    onAmountChange: (String) -> Unit,
    currencies: List<T>,
    currencyLabel: (T) -> String = { it.toString() },
    onCurrencySelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    AmountDisplayInternal(
        modifier = modifier,
        amount = amount,
        currencySymbol = currencySymbol,
        focusRequester = focusRequester,
        onAmountChange = onAmountChange,
        currencyContent = {
            Box(
                modifier = Modifier.clickable { expanded = true },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = currencySymbol,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceVariant,
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = OnSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    currencies.forEach { item ->
                        DropdownMenuItem(
                            onClick = {
                                onCurrencySelected(item)
                                expanded = false
                            },
                        ) {
                            Text(text = currencyLabel(item))
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun AmountDisplayInternal(
    modifier: Modifier = Modifier,
    amount: String,
    currencySymbol: String,
    focusRequester: FocusRequester,
    onAmountChange: (String) -> Unit,
    currencyContent: (@Composable () -> Unit)?,
) {
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = amount, selection = TextRange(amount.length)))
    }

    LaunchedEffect(amount) {
        if (textFieldValue.text != amount) {
            textFieldValue = textFieldValue.copy(text = amount, selection = TextRange(amount.length))
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "AMOUNT",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = OnSurfaceVariant,
            letterSpacing = 3.sp,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            // Currency pinned to left — fixed position regardless of amount width
            Box(modifier = Modifier.align(Alignment.CenterStart)) {
                if (currencyContent != null) {
                    currencyContent()
                } else {
                    Text(
                        text = currencySymbol,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceVariant,
                    )
                }
            }

            // Amount right-aligned, independent of currency position
            BasicTextField(
                value = textFieldValue,
                onValueChange = {
                    textFieldValue = it
                    onAmountChange(it.text)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 70.dp)
                    .focusRequester(focusRequester),
                textStyle = TextStyle(
                    fontSize = 56.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colors.primary,
                    textAlign = TextAlign.Right,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                cursorBrush = SolidColor(MaterialTheme.colors.primary),
                singleLine = true,
                decorationBox = { innerTextField ->
                    if (textFieldValue.text.isEmpty()) {
                        Text(
                            text = "0.00",
                            fontSize = 56.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colors.primary.copy(alpha = 0.3f),
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    innerTextField()
                },
            )
        }
    }
}
