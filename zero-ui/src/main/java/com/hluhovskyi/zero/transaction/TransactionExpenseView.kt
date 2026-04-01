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
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.colors.Color as DomainColor
import com.hluhovskyi.zero.ui.CategoryIconView

@Composable
fun TransactionExpenseView(
    modifier: Modifier,
    categoryName: String,
    amount: String,
    accountName: String,
    iconColorScheme: ColorScheme? = null,
    accountIcon: (@Composable () -> Unit)? = null,
    convertedAmount: String? = null,
    icon: (@Composable (tint: DomainColor) -> Unit)? = null,
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
    iconColorScheme: ColorScheme? = null,
    accountIcon: (@Composable () -> Unit)? = null,
    convertedAmount: String? = null,
    icon: (@Composable (tint: DomainColor) -> Unit)? = null,
) {
    TransactionView(
        modifier = modifier,
        primaryText = categoryName,
        primaryAmount = "+$amount",
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
    secondaryText: String,
    secondaryAmount: String? = null,
    iconColorScheme: ColorScheme? = null,
    secondaryIcon: (@Composable () -> Unit)? = null,
    mainIcon: (@Composable (tint: DomainColor) -> Unit)? = null,
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
            modifier = Modifier.padding(start = 16.dp)
        ) {
            Row {
                Text(
                    text = primaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1B1B1F),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B1B1F),
                    text = primaryAmount,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                secondaryIcon?.invoke()
                Text(
                    text = secondaryText,
                    fontSize = 13.sp,
                    color = Color(0xFF44464F),
                    modifier = Modifier.weight(1f),
                )
                secondaryAmount?.let {
                    Text(
                        fontSize = 12.sp,
                        color = Color(0xFF44464F),
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
    transferIconColorScheme: ColorScheme? = null,
    transferIcon: (@Composable (tint: DomainColor) -> Unit)? = null,
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
