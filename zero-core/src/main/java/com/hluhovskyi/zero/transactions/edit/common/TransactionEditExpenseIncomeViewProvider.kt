package com.hluhovskyi.zero.transactions.edit.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.DatePickerCard
import com.hluhovskyi.zero.ui.SelectorCard

internal class TransactionEditExpenseIncomeViewProvider(
    private val viewModel: TransactionEditExpenseIncomeViewModel,
    private val imageLoader: ImageLoader,
    private val isNewTransaction: Boolean,
) : ViewProvider {

    @Composable
    override fun View() {
        TransactionEditExpenseIncomeView(
            viewModel = viewModel,
            imageLoader = imageLoader,
            shouldFocus = isNewTransaction,
        )
    }
}

@Composable
private fun TransactionEditExpenseIncomeView(
    viewModel: TransactionEditExpenseIncomeViewModel,
    imageLoader: ImageLoader,
    shouldFocus: Boolean,
) {
    val state by viewModel.state.collectAsState(initial = TransactionEditExpenseIncomeViewModel.State())

    Column(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .then(if (!shouldFocus) Modifier.focusTarget() else Modifier),
    ) {
        CategoryScrollRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 32.dp),
            imageLoader = imageLoader,
            categories = state.categories,
            selectedCategory = state.selectedCategory,
            onCategorySelected = {
                viewModel.perform(TransactionEditExpenseIncomeViewModel.Action.SelectCategory(it))
            },
            onShowAll = {
                viewModel.perform(TransactionEditExpenseIncomeViewModel.Action.ShowAllCategories)
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.date?.let { date ->
                DatePickerCard(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.transaction_edit_date_label),
                    date = date,
                    onDateSelected = {
                        viewModel.perform(TransactionEditExpenseIncomeViewModel.Action.ChangeDate(it))
                    },
                )
            }
            SelectorCard(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.transaction_edit_account_label),
                value = state.selectedAccount?.name ?: "",
                items = state.accounts,
                nameMapping = { it.name },
                onItemSelected = {
                    viewModel.perform(TransactionEditExpenseIncomeViewModel.Action.SelectAccount(it))
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
                    viewModel.perform(TransactionEditExpenseIncomeViewModel.Action.ChangeRate(rate))
                },
            )
        }
    }
}
