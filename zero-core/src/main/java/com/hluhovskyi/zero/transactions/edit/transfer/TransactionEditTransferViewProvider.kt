package com.hluhovskyi.zero.transactions.edit.transfer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.transactions.edit.TransactionEditAccount
import com.hluhovskyi.zero.transactions.edit.TransactionEditFocusTarget
import com.hluhovskyi.zero.transactions.edit.common.TransactionEditAmountField
import com.hluhovskyi.zero.transactions.edit.common.TransactionEditRateField
import com.hluhovskyi.zero.ui.DatePickerCard
import com.hluhovskyi.zero.ui.SelectorCard
import com.hluhovskyi.zero.ui.theme.ZeroTheme

internal class TransactionEditTransferViewProvider(
    private val viewModel: TransactionEditTransferViewModel,
    private val isNewTransaction: Boolean,
) : ViewProvider {

    @Composable
    override fun View() {
        TransactionEditTransferView(
            viewModel = viewModel,
            shouldFocus = isNewTransaction,
        )
    }
}

@Composable
private fun TransactionEditTransferView(
    viewModel: TransactionEditTransferViewModel,
    shouldFocus: Boolean,
) {
    val state by viewModel.state.collectAsState(initial = TransactionEditTransferViewModel.State())

    Column(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .then(if (!shouldFocus) Modifier.focusTarget() else Modifier),
    ) {
        if (state.needsFx) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TransactionEditAmountField(
                    modifier = Modifier.weight(1f),
                    caption = stringResource(R.string.transfer_edit_from_amount),
                    currencySymbol = state.sourceCurrencySymbol,
                    value = state.amount,
                    focused = state.editTarget == TransactionEditFocusTarget.Amount,
                    onFocus = { viewModel.perform(TransactionEditTransferViewModel.Action.FocusAmount) },
                )
                TransactionEditAmountField(
                    modifier = Modifier.weight(1f),
                    caption = stringResource(R.string.transfer_edit_to_amount),
                    currencySymbol = state.targetCurrencySymbol,
                    value = state.targetAmount,
                    focused = state.editTarget == TransactionEditFocusTarget.Received,
                    onFocus = { viewModel.perform(TransactionEditTransferViewModel.Action.FocusReceived) },
                )
            }
            TransactionEditRateField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                sourceCurrencySymbol = state.sourceCurrencySymbol,
                targetCurrencySymbol = state.targetCurrencySymbol,
                rate = state.rate,
                rateAuto = state.rateAuto,
                focused = state.editTarget == TransactionEditFocusTarget.Rate,
                onFocus = { viewModel.perform(TransactionEditTransferViewModel.Action.FocusRate) },
                onReset = { viewModel.perform(TransactionEditTransferViewModel.Action.ResetRate) },
            )
        } else {
            TransactionEditAmountField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                caption = stringResource(R.string.transaction_edit_amount_display_label),
                currencySymbol = state.sourceCurrencySymbol,
                value = state.amount,
                focused = true,
                onFocus = { viewModel.perform(TransactionEditTransferViewModel.Action.FocusAmount) },
            )
        }

        AccountSelectorsWithSwap(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 16.dp),
            state = state,
            onSourceSelected = {
                viewModel.perform(TransactionEditTransferViewModel.Action.SelectAccount(it))
            },
            onTargetSelected = {
                viewModel.perform(TransactionEditTransferViewModel.Action.SelectTargetAccount(it))
            },
            onSwap = {
                viewModel.perform(TransactionEditTransferViewModel.Action.SwapAccounts)
            },
        )

        state.date?.let { date ->
            DatePickerCard(
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.transaction_edit_date_label),
                date = date,
                onDateSelected = {
                    viewModel.perform(TransactionEditTransferViewModel.Action.ChangeDate(it))
                },
            )
        }
    }
}

@Composable
private fun AccountSelectorsWithSwap(
    modifier: Modifier = Modifier,
    state: TransactionEditTransferViewModel.State,
    onSourceSelected: (TransactionEditAccount) -> Unit,
    onTargetSelected: (TransactionEditAccount) -> Unit,
    onSwap: () -> Unit,
) {
    Row(
        modifier = modifier.height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectorCard(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.transfer_edit_from_label),
            value = state.selectedAccount?.name ?: "",
            items = state.accounts,
            nameMapping = { it.name },
            onItemSelected = onSourceSelected,
        )
        Box(
            modifier = Modifier
                .width(36.dp)
                .fillMaxHeight()
                .background(ZeroTheme.colors.surfaceContainerLow, RoundedCornerShape(14.dp))
                .clickable(onClick = onSwap),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.SwapHoriz,
                contentDescription = stringResource(R.string.transfer_edit_swap_description),
                tint = ZeroTheme.colors.primaryContainer,
            )
        }
        SelectorCard(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.transfer_edit_to_label),
            value = state.selectedTargetAccount?.name ?: "",
            items = state.targetAccounts,
            nameMapping = { it.name },
            onItemSelected = onTargetSelected,
        )
    }
}
