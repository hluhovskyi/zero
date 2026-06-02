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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.transactions.edit.TransactionEditFocusTarget
import com.hluhovskyi.zero.transactions.edit.TransactionEditViewModel
import com.hluhovskyi.zero.transactions.edit.common.TransactionEditRateField
import com.hluhovskyi.zero.ui.AmountField
import com.hluhovskyi.zero.ui.DatePickerCard
import com.hluhovskyi.zero.ui.SelectorCard
import com.hluhovskyi.zero.ui.theme.ZeroTheme

/** Transfer form: From/To amount fields + rate tile (when FX), From/swap/To selectors, date. */
@Composable
internal fun TransferForm(
    form: TransactionEditViewModel.Form.Transfer,
    perform: (TransactionEditViewModel.Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 24.dp)) {
        if (form.hasFx) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AmountField(
                    modifier = Modifier.weight(1f),
                    caption = stringResource(R.string.transfer_edit_from_amount),
                    currencySymbol = form.sourceCurrencySymbol,
                    value = form.fromAmount,
                    focused = form.keypadTarget == TransactionEditFocusTarget.Amount,
                    onFocus = { perform(TransactionEditViewModel.Action.FocusAmount) },
                )
                AmountField(
                    modifier = Modifier.weight(1f),
                    caption = stringResource(R.string.transfer_edit_to_amount),
                    currencySymbol = form.targetCurrencySymbol,
                    value = form.toAmount,
                    focused = form.keypadTarget == TransactionEditFocusTarget.Received,
                    onFocus = { perform(TransactionEditViewModel.Action.FocusReceived) },
                )
            }
            TransactionEditRateField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                sourceCurrencySymbol = form.sourceCurrencySymbol,
                targetCurrencySymbol = form.targetCurrencySymbol,
                rate = form.rate,
                rateAuto = form.rateAuto,
                focused = form.keypadTarget == TransactionEditFocusTarget.Rate,
                onFocus = { perform(TransactionEditViewModel.Action.FocusRate) },
                onReset = { perform(TransactionEditViewModel.Action.ResetRate) },
            )
        } else {
            AmountField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                caption = stringResource(R.string.transaction_edit_amount_display_label),
                currencySymbol = form.sourceCurrencySymbol,
                value = form.fromAmount,
                focused = true,
                hero = true,
                onFocus = { perform(TransactionEditViewModel.Action.FocusAmount) },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(top = 14.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SelectorCard(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.transfer_edit_from_label),
                value = form.selectedAccount?.name ?: "",
                items = form.accounts,
                nameMapping = { it.name },
                onItemSelected = { perform(TransactionEditViewModel.Action.SelectAccount(it)) },
            )
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .fillMaxHeight()
                    .background(ZeroTheme.colors.surfaceContainerLow, RoundedCornerShape(14.dp))
                    .clickable { perform(TransactionEditViewModel.Action.SwapAccounts) },
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
                value = form.selectedTargetAccount?.name ?: "",
                items = form.targetAccounts,
                nameMapping = { it.name },
                onItemSelected = { perform(TransactionEditViewModel.Action.SelectTargetAccount(it)) },
            )
        }

        form.date?.let { date ->
            DatePickerCard(
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.transaction_edit_date_label),
                date = date,
                onDateSelected = { perform(TransactionEditViewModel.Action.ChangeDate(it)) },
            )
        }
    }
}
