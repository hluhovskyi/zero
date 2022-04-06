package com.hluhovskyi.zero.transaction

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.common.ColorValue
import com.hluhovskyi.zero.common.toCompose

@Composable
fun TransactionExpenseView(
    modifier: Modifier,
    categoryColor: ColorValue,
    categoryName: String,
    amount: String,
    accountName: String,
    accountIcon: (@Composable () -> Unit)? = null,
    convertedAmount: String? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    TransactionView(
        modifier = modifier,
        categoryColor = categoryColor,
        categoryName = categoryName,
        // TODO: Change to appropriate color
        amountColor = ColorValue.unspecified(),
        amount = "-$amount",
        accountName = accountName,
        accountIcon = accountIcon,
        convertedAmount = convertedAmount,
        icon = icon,
    )
}

@Composable
fun TransactionIncomeView(
    modifier: Modifier,
    categoryColor: ColorValue,
    categoryName: String,
    amount: String,
    accountName: String,
    accountIcon: (@Composable () -> Unit)? = null,
    convertedAmount: String? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    TransactionView(
        modifier = modifier,
        categoryColor = categoryColor,
        categoryName = categoryName,
        // TODO: Change to appropriate color
        amountColor = ColorValue(Color.Green.value),
        amount = "+$amount",
        accountName = accountName,
        accountIcon = accountIcon,
        convertedAmount = convertedAmount,
        icon = icon,
    )
}

@Composable
fun TransactionView(
    modifier: Modifier,
    categoryColor: ColorValue,
    categoryName: String,
    amountColor: ColorValue,
    amount: String,
    accountName: String,
    accountIcon: (@Composable () -> Unit)? = null,
    convertedAmount: String? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { iconView ->
            Column(
            ) {
                Box(
                    modifier = Modifier
                        .background(categoryColor.toCompose(), shape = CircleShape)
                        .size(40.dp)
                        .padding(8.dp)
                ) {
                    iconView()
                }
            }
        }
        Column(
            modifier = Modifier.padding(start = 16.dp)
        ) {
            Row {
                Text(
                    text = categoryName,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    fontSize = 18.sp,
                    color = amountColor.toCompose(),
                    text = amount
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                    accountIcon?.invoke()
                    Text(
                        text = accountName,
                        modifier = Modifier.weight(1f),
                    )
                }
                convertedAmount?.let {
                    Text(
                        fontSize = 14.sp,
                        text = convertedAmount,
                    )
                }
            }
        }
    }
}