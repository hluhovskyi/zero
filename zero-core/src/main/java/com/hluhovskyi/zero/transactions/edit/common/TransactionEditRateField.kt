package com.hluhovskyi.zero.transactions.edit.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.ui.theme.ZeroTheme

/**
 * Boxed "Exchange rate" tile shared by expense/income and transfer. Tapping it focuses the rate
 * for the inline keypad; [onReset] restores the auto-derived rate. Passing [convertedAmountText]
 * adds the converted-total line (expense/income); transfer omits it.
 */
@Composable
internal fun TransactionEditRateField(
    modifier: Modifier = Modifier,
    sourceCurrencySymbol: String,
    targetCurrencySymbol: String,
    rate: String,
    rateAuto: Boolean,
    focused: Boolean,
    onFocus: () -> Unit,
    onReset: () -> Unit,
    convertedAmountText: String? = null,
    convertedCurrencyName: String = "",
) {
    Column(
        modifier = modifier
            .background(
                if (focused) ZeroTheme.colors.surface else ZeroTheme.colors.surfaceContainerLow,
                RoundedCornerShape(16.dp),
            )
            .then(
                if (focused) {
                    Modifier.border(1.5.dp, ZeroTheme.colors.primaryContainer, RoundedCornerShape(16.dp))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onFocus)
            .padding(horizontal = 16.dp, vertical = 11.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.transaction_edit_exchange_rate).uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = ZeroTheme.colors.onSurfaceVariant,
                letterSpacing = 1.2.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "1$sourceCurrencySymbol = ",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ZeroTheme.colors.onSurfaceVariant,
                )
                Text(
                    text = rate.ifEmpty { "0" },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = ZeroTheme.colors.primaryContainer,
                )
                if (focused) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .width(2.dp)
                            .height(15.dp)
                            .background(ZeroTheme.colors.primaryContainer),
                    )
                }
                Text(
                    text = " $targetCurrencySymbol",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ZeroTheme.colors.onSurfaceVariant,
                )
                if (!rateAuto) {
                    Row(
                        modifier = Modifier
                            .padding(start = 5.dp)
                            .clickable(onClick = onReset),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = ZeroTheme.colors.primaryContainer,
                        )
                        Text(
                            text = stringResource(R.string.transaction_edit_rate_reset),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ZeroTheme.colors.primaryContainer,
                            modifier = Modifier.padding(start = 2.dp),
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(14.dp),
                        tint = if (focused) ZeroTheme.colors.primaryContainer else ZeroTheme.colors.outline,
                    )
                }
            }
        }
        if (convertedAmountText != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 9.dp)
                    .height(1.dp)
                    .background(ZeroTheme.colors.surfaceContainer),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.transaction_edit_converts_to) + " · " + convertedCurrencyName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ZeroTheme.colors.onSurfaceVariant,
                )
                Text(
                    text = convertedAmountText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = ZeroTheme.colors.primaryContainer,
                )
            }
        }
    }
}
