package com.hluhovskyi.zero.transactions.edit.income

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.transactions.edit.common.AmountDisplay
import com.hluhovskyi.zero.transactions.edit.common.CategoryScrollRow
import com.hluhovskyi.zero.transactions.edit.common.TransactionEditRateTextField
import com.hluhovskyi.zero.ui.DatePickerCard
import com.hluhovskyi.zero.ui.SelectorCard

internal class TransactionEditIncomeViewProvider(
    private val viewModel: TransactionEditIncomeViewModel,
    private val imageLoader: ImageLoader,
) : ViewProvider {

    @Composable
    override fun View() {
        TransactionEditIncomeView(
            viewModel = viewModel,
            imageLoader = imageLoader
        )
    }
}

@Composable
private fun TransactionEditIncomeView(
    viewModel: TransactionEditIncomeViewModel,
    imageLoader: ImageLoader
) {
    val state by viewModel.state.collectAsState(initial = TransactionEditIncomeViewModel.State())
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier.padding(horizontal = 24.dp)
    ) {
        AmountDisplay(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp, bottom = 40.dp),
            amount = state.amount,
            currencySymbol = state.selectedCurrency?.currencySymbol ?: "",
            focusRequester = focusRequester,
            onAmountChange = {
                viewModel.perform(TransactionEditIncomeViewModel.Action.ChangeAmount(it))
            },
            currencies = state.currencies,
            onCurrencySelected = {
                viewModel.perform(TransactionEditIncomeViewModel.Action.SelectCurrency(it))
            }
        )

        CategoryScrollRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            imageLoader = imageLoader,
            categories = state.categories,
            selectedCategory = state.selectedCategory,
            onCategorySelected = {
                viewModel.perform(TransactionEditIncomeViewModel.Action.SelectCategory(it))
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DatePickerCard(
                modifier = Modifier.weight(1f),
                label = "Date",
                date = state.date,
                onDateSelected = {
                    viewModel.perform(TransactionEditIncomeViewModel.Action.ChangeDate(it))
                }
            )
            SelectorCard(
                modifier = Modifier.weight(1f),
                label = "Account",
                value = state.selectedAccount?.name ?: "",
                items = state.accounts,
                nameMapping = { it.name },
                onItemSelected = {
                    viewModel.perform(TransactionEditIncomeViewModel.Action.SelectAccount(it))
                }
            )
        }

        AnimatedVisibility(visible = state.showRate) {
            TransactionEditRateTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                rate = state.rate,
                onValueChange = { rate ->
                    viewModel.perform(TransactionEditIncomeViewModel.Action.ChangeRate(rate))
                }
            )
        }
    }
}
