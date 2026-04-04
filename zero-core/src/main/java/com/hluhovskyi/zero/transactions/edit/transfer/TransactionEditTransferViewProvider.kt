package com.hluhovskyi.zero.transactions.edit.transfer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.hluhovskyi.zero.common.Rate
import com.hluhovskyi.zero.transactions.edit.TransferRateMode
import com.hluhovskyi.zero.transactions.edit.common.AmountDisplay
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.DatePickerCard
import com.hluhovskyi.zero.ui.SelectorCard
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLow
import com.hluhovskyi.zero.ui.theme.SurfaceContainerHigh
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat

internal class TransactionEditTransferViewProvider(
    private val viewModel: TransactionEditTransferViewModel
) : ViewProvider {

    @Composable
    override fun View() {
        TransactionEditTransferView(viewModel = viewModel)
    }
}

@Composable
private fun TransactionEditTransferView(
    viewModel: TransactionEditTransferViewModel
) {
    val state by viewModel.state.collectAsState(initial = TransactionEditTransferViewModel.State())
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier.padding(horizontal = 24.dp)
    ) {
        // Amount display
        AmountDisplay(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp, bottom = 24.dp),
            amount = state.amount,
            currencySymbol = state.sourceCurrencySymbol,
            focusRequester = focusRequester,
            onAmountChange = {
                viewModel.perform(TransactionEditTransferViewModel.Action.ChangeAmount(it))
            },
            showCurrencySelector = false,
        )

        // Rate mode pill
        RateModePill(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            state = state,
            onCycleMode = {
                viewModel.perform(TransactionEditTransferViewModel.Action.CycleRateMode)
            },
            onRateChange = {
                viewModel.perform(TransactionEditTransferViewModel.Action.ChangeTransferRate(it))
            },
            onTargetAmountChange = {
                viewModel.perform(TransactionEditTransferViewModel.Action.ChangeTargetAmount(it))
            },
        )

        // From / Swap / To account selectors
        AccountSelectorsWithSwap(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            state = state,
            onSourceSelected = {
                viewModel.perform(TransactionEditTransferViewModel.Action.SelectAccount(it))
            },
            onTargetSelected = {
                viewModel.perform(TransactionEditTransferViewModel.Action.SelectTargetAccount(it))
            },
            onSwap = {
                viewModel.perform(TransactionEditTransferViewModel.Action.SwapAccounts)
            }
        )

        // Date picker (full width)
        DatePickerCard(
            modifier = Modifier.fillMaxWidth(),
            label = "Date",
            date = state.date,
            onDateSelected = {
                viewModel.perform(TransactionEditTransferViewModel.Action.ChangeDate(it))
            }
        )
    }
}

@Composable
private fun RateModePill(
    modifier: Modifier = Modifier,
    state: TransactionEditTransferViewModel.State,
    onCycleMode: () -> Unit,
    onRateChange: (String) -> Unit,
    onTargetAmountChange: (String) -> Unit,
) {
    Column(
        modifier = modifier
            .background(SurfaceContainerLow, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        when (val mode = state.transferRateMode) {
            is TransferRateMode.Default -> {
                Column(
                    modifier = Modifier.clickable { onCycleMode() }
                ) {
                    val pillText = formatDefaultPillText(
                        amount = state.amount,
                        rate = mode.rate,
                        sourceCurrencySymbol = state.sourceCurrencySymbol,
                        targetCurrencySymbol = state.targetCurrencySymbol,
                    )
                    Text(
                        text = pillText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = OnSurfaceVariant,
                    )
                }
            }

            is TransferRateMode.CustomRate -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RATE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceVariant,
                        letterSpacing = 1.sp,
                    )
                    Text(
                        text = "CHANGE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.primary,
                        modifier = Modifier.clickable { onCycleMode() }
                    )
                }
                BasicTextField(
                    value = mode.rate,
                    onValueChange = onRateChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.primary,
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    cursorBrush = SolidColor(MaterialTheme.colors.primary),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        if (mode.rate.isEmpty()) {
                            Text(
                                text = "Enter rate",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = OnSurfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                        innerTextField()
                    }
                )
                // Show computed destination amount read-only
                val computedTarget = computeTargetFromRate(state.amount, mode.rate, state.targetCurrencySymbol)
                if (computedTarget.isNotEmpty()) {
                    Text(
                        modifier = Modifier.padding(top = 4.dp),
                        text = computedTarget,
                        fontSize = 12.sp,
                        color = OnSurfaceVariant,
                    )
                }
            }

            is TransferRateMode.CustomAmount -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DESTINATION AMOUNT",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceVariant,
                        letterSpacing = 1.sp,
                    )
                    Text(
                        text = "CHANGE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.primary,
                        modifier = Modifier.clickable { onCycleMode() }
                    )
                }
                BasicTextField(
                    value = state.targetAmount,
                    onValueChange = onTargetAmountChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.primary,
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    cursorBrush = SolidColor(MaterialTheme.colors.primary),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (state.targetCurrencySymbol.isNotEmpty()) {
                                Text(
                                    text = state.targetCurrencySymbol,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurfaceVariant,
                                    modifier = Modifier.padding(end = 4.dp),
                                )
                            }
                            Box {
                                if (state.targetAmount.isEmpty()) {
                                    Text(
                                        text = "0",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = OnSurfaceVariant.copy(alpha = 0.5f),
                                    )
                                }
                                innerTextField()
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AccountSelectorsWithSwap(
    modifier: Modifier = Modifier,
    state: TransactionEditTransferViewModel.State,
    onSourceSelected: (com.hluhovskyi.zero.transactions.edit.TransactionEditAccount) -> Unit,
    onTargetSelected: (com.hluhovskyi.zero.transactions.edit.TransactionEditAccount) -> Unit,
    onSwap: () -> Unit,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SelectorCard(
                modifier = Modifier.fillMaxWidth(),
                label = "From",
                value = state.selectedAccount?.name ?: "",
                items = state.accounts,
                nameMapping = { it.name },
                onItemSelected = onSourceSelected,
            )
            SelectorCard(
                modifier = Modifier.fillMaxWidth(),
                label = "To",
                value = state.selectedTargetAccount?.name ?: "",
                items = state.targetAccounts,
                nameMapping = { it.name },
                onItemSelected = onTargetSelected,
            )
        }

        // Swap button — centered, overlapping between cards
        Box(
            modifier = Modifier
                .zIndex(1f)
                .size(40.dp)
                .clip(CircleShape)
                .background(SurfaceContainerHigh)
                .clickable { onSwap() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.SwapVert,
                contentDescription = "Swap accounts",
                modifier = Modifier.size(20.dp),
                tint = OnSurfaceVariant,
            )
        }
    }
}

private val amountFormat = DecimalFormat("#,##0.00")

private fun formatDefaultPillText(
    amount: String,
    rate: Rate,
    sourceCurrencySymbol: String,
    targetCurrencySymbol: String,
): String {
    val sourceAmount = amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val targetAmount = sourceAmount.multiply(rate.value)
    val formattedTarget = amountFormat.format(targetAmount)

    return if (sourceCurrencySymbol == targetCurrencySymbol) {
        "Receives $formattedTarget"
    } else {
        val formattedRate = rate.value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        "Receives $targetCurrencySymbol$formattedTarget \u00B7 1 $sourceCurrencySymbol = $formattedRate $targetCurrencySymbol"
    }
}

private fun computeTargetFromRate(
    sourceAmount: String,
    rateStr: String,
    targetCurrencySymbol: String,
): String {
    val amount = sourceAmount.toBigDecimalOrNull() ?: return ""
    val rate = rateStr.toBigDecimalOrNull() ?: return ""
    if (rate.compareTo(BigDecimal.ZERO) == 0) return ""
    val target = amount.multiply(rate)
    val formatted = amountFormat.format(target)
    return if (targetCurrencySymbol.isNotEmpty()) {
        "Receives $targetCurrencySymbol$formatted"
    } else {
        "Receives $formatted"
    }
}
