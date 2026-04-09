package com.hluhovskyi.zero.transactions.edit.expense

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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

internal class TransactionEditExpenseViewProvider(
    private val viewModel: TransactionEditExpenseViewModel,
    private val imageLoader: ImageLoader,
) : ViewProvider {

    @Composable
    override fun View() {
        TransactionEditExpenseView(
            viewModel = viewModel,
            imageLoader = imageLoader,
        )
    }
}

@Composable
private fun TransactionEditExpenseView(
    viewModel: TransactionEditExpenseViewModel,
    imageLoader: ImageLoader,
) {
    val state by viewModel.state.collectAsState(initial = TransactionEditExpenseViewModel.State())
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier.padding(horizontal = 24.dp),
    ) {
        AmountDisplay(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp, bottom = 40.dp),
            amount = state.amount,
            currencySymbol = state.selectedCurrency?.currencySymbol ?: "",
            focusRequester = focusRequester,
            onAmountChange = {
                viewModel.perform(TransactionEditExpenseViewModel.Action.ChangeAmount(it))
            },
            currencies = state.currencies,
            onCurrencySelected = {
                viewModel.perform(TransactionEditExpenseViewModel.Action.SelectCurrency(it))
            },
        )

        CategoryScrollRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            imageLoader = imageLoader,
            categories = state.categories,
            selectedCategory = state.selectedCategory,
            onCategorySelected = {
                viewModel.perform(TransactionEditExpenseViewModel.Action.SelectCategory(it))
            },
            onShowAll = {
                viewModel.perform(TransactionEditExpenseViewModel.Action.ShowAllCategories)
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.date?.let { date ->
                DatePickerCard(
                    modifier = Modifier.weight(1f),
                    label = "Date",
                    date = date,
                    onDateSelected = {
                        viewModel.perform(TransactionEditExpenseViewModel.Action.ChangeDate(it))
                    },
                )
            }
            SelectorCard(
                modifier = Modifier.weight(1f),
                label = "Account",
                value = state.selectedAccount?.name ?: "",
                items = state.accounts,
                nameMapping = { it.name },
                onItemSelected = {
                    viewModel.perform(TransactionEditExpenseViewModel.Action.SelectAccount(it))
                },
            )
        }

        AnimatedVisibility(visible = state.showRate) {
            TransactionEditRateTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                rate = state.rate,
                onValueChange = { rate ->
                    viewModel.perform(TransactionEditExpenseViewModel.Action.ChangeRate(rate))
                },
            )
        }
    }
}
