package com.hluhovskyi.zero.transactions.edit.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.transactions.edit.TransactionEditFocusTarget
import com.hluhovskyi.zero.transactions.edit.TransactionEditViewModel
import com.hluhovskyi.zero.ui.DatePickerCard
import com.hluhovskyi.zero.ui.SelectorCard

/** Expense / income form: Exchange-rate tile (when FX), category row, date + account selectors. */
@Composable
internal fun ExpenseIncomeForm(
    form: TransactionEditViewModel.Form.ExpenseIncome,
    imageLoader: ImageLoader,
    perform: (TransactionEditViewModel.Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 24.dp)) {
        AnimatedVisibility(visible = form.hasFx) {
            TransactionEditRateField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                sourceCurrencySymbol = form.sourceCurrencySymbol,
                targetCurrencySymbol = form.targetCurrencySymbol,
                rate = form.rate,
                rateAuto = form.rateAuto,
                focused = form.keypadTarget == TransactionEditFocusTarget.Rate,
                onFocus = { perform(TransactionEditViewModel.Action.FocusRate) },
                onReset = { perform(TransactionEditViewModel.Action.ResetRate) },
                convertedAmountText = form.convertedAmountText,
                convertedCurrencyName = form.targetCurrencyName,
            )
        }

        CategoryField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 16.dp),
            imageLoader = imageLoader,
            categories = form.categories,
            selectedCategory = form.selectedCategory,
            showShortcuts = form.showCategoryShortcuts,
            onCategorySelected = { perform(TransactionEditViewModel.Action.SelectCategory(it)) },
            onOpenPicker = { perform(TransactionEditViewModel.Action.ShowAllCategories) },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            form.date?.let { date ->
                DatePickerCard(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.transaction_edit_date_label),
                    date = date,
                    onDateSelected = { perform(TransactionEditViewModel.Action.ChangeDate(it)) },
                )
            }
            SelectorCard(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.transaction_edit_account_label),
                value = form.selectedAccount?.name ?: "",
                items = form.accounts,
                nameMapping = { it.name },
                onItemSelected = { perform(TransactionEditViewModel.Action.SelectAccount(it)) },
            )
        }
    }
}
