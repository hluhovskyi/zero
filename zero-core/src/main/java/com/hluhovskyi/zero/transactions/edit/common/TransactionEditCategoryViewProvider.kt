package com.hluhovskyi.zero.transactions.edit.common

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
import com.hluhovskyi.zero.ui.DatePickerCard
import com.hluhovskyi.zero.ui.SelectorCard

internal class TransactionEditCategoryViewProvider(
    private val viewModel: TransactionEditCategoryViewModel,
    private val imageLoader: ImageLoader,
) : ViewProvider {

    @Composable
    override fun View() {
        TransactionEditCategoryView(
            viewModel = viewModel,
            imageLoader = imageLoader,
        )
    }
}

@Composable
private fun TransactionEditCategoryView(
    viewModel: TransactionEditCategoryViewModel,
    imageLoader: ImageLoader,
) {
    val state by viewModel.state.collectAsState(initial = TransactionEditCategoryViewModel.State())
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
                viewModel.perform(TransactionEditCategoryViewModel.Action.ChangeAmount(it))
            },
            currencies = state.currencies,
            onCurrencySelected = {
                viewModel.perform(TransactionEditCategoryViewModel.Action.SelectCurrency(it))
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
                viewModel.perform(TransactionEditCategoryViewModel.Action.SelectCategory(it))
            },
            onShowAll = {
                viewModel.perform(TransactionEditCategoryViewModel.Action.ShowAllCategories)
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
                        viewModel.perform(TransactionEditCategoryViewModel.Action.ChangeDate(it))
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
                    viewModel.perform(TransactionEditCategoryViewModel.Action.SelectAccount(it))
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
                    viewModel.perform(TransactionEditCategoryViewModel.Action.ChangeRate(rate))
                },
            )
        }
    }
}
