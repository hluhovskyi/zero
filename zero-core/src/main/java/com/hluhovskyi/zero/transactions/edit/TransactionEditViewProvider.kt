package com.hluhovskyi.zero.transactions.edit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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

internal class TransactionEditViewProvider(
    private val viewModel: TransactionEditViewModel,
    private val expenseComponent: Buildable<out AttachableViewComponent>,
) : ViewProvider {

    @Composable
    override fun View() {
        TransactionEditView(
            viewModel = viewModel,
            expenseComponent = expenseComponent,
        )
    }
}

@Composable
private fun TransactionEditView(
    viewModel: TransactionEditViewModel,
    expenseComponent: Buildable<out AttachableViewComponent>,
) {
    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        val state by viewModel.state.collectAsState(initial = TransactionEditViewModel.State())
        when (state.transactionType) {
            TransactionEditType.EXPENSE -> {
                expenseComponent.AttachWithView()
            }
            TransactionEditType.INCOME -> {

            }
            TransactionEditType.TRANSFER -> {

            }
        }
        Button(
            modifier = Modifier.padding(top = 16.dp),
            onClick = { viewModel.perform(TransactionEditViewModel.Action.Save) }
        ) {
            Text(text = "Save")
        }
    }
}
