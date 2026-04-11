package com.hluhovskyi.zero.transactions.edit

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.ExtendedFloatingActionButton
import androidx.compose.material.FloatingActionButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hluhovskyi.zero.common.AttachWithView
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.ModalHeader
import com.hluhovskyi.zero.ui.SegmentedToggle

internal class TransactionEditViewProvider(
    private val viewModel: TransactionEditViewModel,
    private val expenseIncomeComponent: Buildable<out AttachableViewComponent>,
    private val transferComponent: Buildable<out AttachableViewComponent>,
) : ViewProvider {

    @Composable
    override fun View() {
        TransactionEditView(
            viewModel = viewModel,
            expenseIncomeComponent = expenseIncomeComponent,
            transferComponent = transferComponent,
        )
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun TransactionEditView(
    viewModel: TransactionEditViewModel,
    expenseIncomeComponent: Buildable<out AttachableViewComponent>,
    transferComponent: Buildable<out AttachableViewComponent>,
) {
    val state by viewModel.state.collectAsState(initial = TransactionEditViewModel.State())

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            item {
                ModalHeader(
                    title = "New Transaction",
                    onClose = { viewModel.perform(TransactionEditViewModel.Action.Discard) },
                )
            }
            item {
                SegmentedToggle(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 24.dp),
                    items = state.transactionTypes,
                    selectedItem = state.selectedTransactionType,
                    onItemSelected = { type ->
                        viewModel.perform(TransactionEditViewModel.Action.ChangeTransactionType(type))
                    },
                    labelMapping = { type ->
                        when (type) {
                            TransactionEditType.EXPENSE -> "Expense"
                            TransactionEditType.INCOME -> "Income"
                            TransactionEditType.TRANSFER -> "Transfer"
                        }
                    },
                )
            }
            item {
                when (state.selectedTransactionType) {
                    TransactionEditType.EXPENSE,
                    TransactionEditType.INCOME,
                    -> expenseIncomeComponent.AttachWithView()
                    TransactionEditType.TRANSFER -> transferComponent.AttachWithView()
                }
            }
        }

        ExtendedFloatingActionButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 32.dp),
            icon = {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                )
            },
            text = {
                Text(text = "Save Transaction")
            },
            onClick = { viewModel.perform(TransactionEditViewModel.Action.Save) },
            elevation = FloatingActionButtonDefaults.elevation(8.dp),
        )
    }
}
