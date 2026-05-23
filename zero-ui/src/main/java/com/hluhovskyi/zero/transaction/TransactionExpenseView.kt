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
import com.hluhovskyi.zero.ui.UiColorScheme
import com.hluhovskyi.zero.ui.theme.ZeroTheme

@Composable
fun TransactionExpenseView(
    modifier: Modifier,
    categoryName: String,
    amount: String,
    accountName: String,
    iconColorScheme: UiColorScheme? = null,
    accountIcon: (@Composable () -> Unit)? = null,
    convertedAmount: String? = null,
    icon: (@Composable (tint: Color) -> Unit)? = null,
) {
    TransactionView(
        modifier = modifier,
        primaryText = categoryName,
        primaryAmount = "-$amount",
        secondaryText = accountName,
        secondaryAmount = convertedAmount,
        iconColorScheme = iconColorScheme,
        secondaryIcon = accountIcon,
        mainIcon = icon,
    )
}

@Composable
fun TransactionIncomeView(
    modifier: Modifier,
    categoryName: String,
    amount: String,
    accountName: String,
    iconColorScheme: UiColorScheme? = null,
    accountIcon: (@Composable () -> Unit)? = null,
    convertedAmount: String? = null,
    icon: (@Composable (tint: Color) -> Unit)? = null,
) {
    TransactionView(
        modifier = modifier,
        primaryText = categoryName,
        primaryAmount = "+$amount",
        amountColor = ZeroTheme.colors.transactionIncome,
        secondaryText = accountName,
        secondaryAmount = convertedAmount,
        iconColorScheme = iconColorScheme,
        secondaryIcon = accountIcon,
        mainIcon = icon,
    )
}

@Composable
fun TransactionView(
    modifier: Modifier,
    primaryText: String,
    primaryAmount: String,
    amountColor: Color = ZeroTheme.colors.onSurface,
    secondaryText: String,
    secondaryAmount: String? = null,
    iconColorScheme: UiColorScheme? = null,
    secondaryIcon: (@Composable () -> Unit)? = null,
    mainIcon: (@Composable (tint: Color) -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (mainIcon != null && iconColorScheme != null) {
            CategoryIconView(colorScheme = iconColorScheme) { tint ->
                mainIcon(tint)
            }
        }
        Column(
            modifier = Modifier.padding(start = 16.dp),
        ) {
            Row {
                Text(
                    text = primaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ZeroTheme.colors.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = amountColor,
                    text = primaryAmount,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                secondaryIcon?.invoke()
                Text(
                    text = secondaryText,
                    fontSize = 13.sp,
                    color = ZeroTheme.colors.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                secondaryAmount?.let {
                    Text(
                        fontSize = 12.sp,
                        color = ZeroTheme.colors.onSurfaceVariant,
                        text = it,
                    )
                }
            }
        }
    }
}

@Composable
fun TransactionTransferView(
    modifier: Modifier,
    sourceAccountName: String,
    targetAccountName: String,
    sourceAmount: String,
    targetAmount: String,
    transferIconColorScheme: UiColorScheme? = null,
    transferIcon: (@Composable (tint: Color) -> Unit)? = null,
) {
    TransactionView(
        modifier = modifier,
        primaryText = targetAccountName,
        primaryAmount = "+$targetAmount",
        secondaryText = sourceAccountName,
        secondaryAmount = "-$sourceAmount",
        iconColorScheme = transferIconColorScheme,
        mainIcon = transferIcon,
    )
}
