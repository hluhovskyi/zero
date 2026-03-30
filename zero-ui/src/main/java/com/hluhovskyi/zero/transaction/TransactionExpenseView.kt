package com.hluhovskyi.zero.transaction

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ui.CategoryIconView

@Composable
fun TransactionExpenseView(
    modifier: Modifier,
    categoryName: String,
    amount: String,
    accountName: String,
    iconColor: Color? = null,
    accountIcon: (@Composable () -> Unit)? = null,
    convertedAmount: String? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    TransactionView(
        modifier = modifier,
        categoryName = categoryName,
        amount = "-$amount",
        accountName = accountName,
        iconColor = iconColor,
        accountIcon = accountIcon,
        convertedAmount = convertedAmount,
        icon = icon,
    )
}

@Composable
fun TransactionIncomeView(
    modifier: Modifier,
    categoryName: String,
    amount: String,
    accountName: String,
    iconColor: Color? = null,
    accountIcon: (@Composable () -> Unit)? = null,
    convertedAmount: String? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    TransactionView(
        modifier = modifier,
        categoryName = categoryName,
        amount = "+$amount",
        accountName = accountName,
        iconColor = iconColor,
        accountIcon = accountIcon,
        convertedAmount = convertedAmount,
        icon = icon,
    )
}

@Composable
fun TransactionView(
    modifier: Modifier,
    categoryName: String,
    amount: String,
    accountName: String,
    iconColor: Color? = null,
    accountIcon: (@Composable () -> Unit)? = null,
    convertedAmount: String? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null && iconColor != null) {
            CategoryIconView(color = iconColor) {
                icon()
            }
        }
        Column(
            modifier = Modifier.padding(start = 16.dp)
        ) {
            Row {
                Text(
                    text = categoryName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1B1B1F),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B1B1F),
                    text = amount,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                accountIcon?.invoke()
                Text(
                    text = accountName,
                    fontSize = 13.sp,
                    color = Color(0xFF44464F),
                    modifier = Modifier.weight(1f),
                )
                convertedAmount?.let {
                    Text(
                        fontSize = 12.sp,
                        color = Color(0xFF44464F),
                        text = convertedAmount,
                    )
                }
            }
        }
    }
}
