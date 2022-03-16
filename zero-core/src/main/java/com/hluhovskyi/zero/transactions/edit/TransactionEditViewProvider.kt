package com.hluhovskyi.zero.transactions.edit

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hluhovskyi.zero.common.AttachWithView
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.TextFieldDropdownMenu

internal class TransactionEditViewProvider(
    private val viewModel: TransactionEditViewModel,
    private val expenseComponent: Buildable<out AttachableViewComponent>,
    private val incomeComponent: Buildable<out AttachableViewComponent>,
    private val transferComponent: Buildable<out AttachableViewComponent>,
) : ViewProvider {

    @Composable
    override fun View() {
        TransactionEditView(
            viewModel = viewModel,
            expenseComponent = expenseComponent,
            incomeComponent = incomeComponent,
            transferComponent = transferComponent
        )
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun TransactionEditView(
    viewModel: TransactionEditViewModel,
    expenseComponent: Buildable<out AttachableViewComponent>,
    incomeComponent: Buildable<out AttachableViewComponent>,
    transferComponent: Buildable<out AttachableViewComponent>
) {
    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        val state by viewModel.state.collectAsState(initial = TransactionEditViewModel.State())
        TransactionTypeSelect(
            types = state.transactionTypes,
            selectedType = state.selectedTransactionType,
            onTypeSelected = { type ->
                viewModel.perform(TransactionEditViewModel.Action.ChangeTransactionType(type))
            }
        )
        when (state.selectedTransactionType) {
            TransactionEditType.EXPENSE -> {
                expenseComponent.AttachWithView()
            }
            TransactionEditType.INCOME -> {
                incomeComponent.AttachWithView()
            }
            TransactionEditType.TRANSFER -> {
                transferComponent.AttachWithView()
            }
        }
        Button(
            modifier = Modifier
                .padding(top = 16.dp)
                .sizeIn(minHeight = 48.dp)
                .fillMaxWidth(),
            onClick = { viewModel.perform(TransactionEditViewModel.Action.Save) }
        ) {
            Text(text = "Save transaction")
        }
    }
}

@Composable
private fun TransactionTypeSelect(
    modifier: Modifier = Modifier,
    types: List<TransactionEditType>,
    selectedType: TransactionEditType?,
    onTypeSelected: (TransactionEditType) -> Unit
) {
    TextFieldDropdownMenu(
        modifier = modifier,
        items = types,
        label = {
            Text(text = "Type")
        },
        nameMapping = {
            when (it) {
                TransactionEditType.EXPENSE -> "Expense"
                TransactionEditType.INCOME -> "Income"
                TransactionEditType.TRANSFER -> "Transfer"
            }
        },
        selectedItem = selectedType,
        onItemSelected = onTypeSelected
    )
}
