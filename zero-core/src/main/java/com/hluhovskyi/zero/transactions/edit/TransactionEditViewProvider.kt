package com.hluhovskyi.zero.transactions.edit

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ExtendedFloatingActionButton
import androidx.compose.material.FloatingActionButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.hluhovskyi.zero.common.AttachWithView
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.ModalHeader
import com.hluhovskyi.zero.ui.SegmentedToggle

internal class TransactionEditViewProvider(
    private val viewModel: TransactionEditViewModel,
    private val categoryPickerComponent: Buildable<out AttachableViewComponent>,
    private val expenseComponent: Buildable<out AttachableViewComponent>,
    private val incomeComponent: Buildable<out AttachableViewComponent>,
    private val transferComponent: Buildable<out AttachableViewComponent>,
) : ViewProvider {

    @Composable
    override fun View() {
        TransactionEditView(
            viewModel = viewModel,
            categoryPickerComponent = categoryPickerComponent,
            expenseComponent = expenseComponent,
            incomeComponent = incomeComponent,
            transferComponent = transferComponent
        )
    }
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterialApi::class)
@Composable
private fun TransactionEditView(
    viewModel: TransactionEditViewModel,
    categoryPickerComponent: Buildable<out AttachableViewComponent>,
    expenseComponent: Buildable<out AttachableViewComponent>,
    incomeComponent: Buildable<out AttachableViewComponent>,
    transferComponent: Buildable<out AttachableViewComponent>
) {
    val state by viewModel.state.collectAsState(initial = TransactionEditViewModel.State())
    val sheetState = rememberModalBottomSheetState(initialValue = ModalBottomSheetValue.Hidden)
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(state.showCategoryPicker) {
        if (state.showCategoryPicker) {
            focusManager.clearFocus()
            sheetState.show()
        } else {
            sheetState.hide()
        }
    }

    LaunchedEffect(sheetState.currentValue) {
        if (sheetState.currentValue == ModalBottomSheetValue.Hidden && state.showCategoryPicker) {
            viewModel.perform(TransactionEditViewModel.Action.DismissCategoryPicker)
        }
    }

    ModalBottomSheetLayout(
        sheetState = sheetState,
        sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        sheetBackgroundColor = MaterialTheme.colors.background,
        sheetContent = {
            categoryPickerComponent.AttachWithView()
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background)
                .imePadding()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                item {
                    ModalHeader(
                        title = "New Transaction",
                        onClose = { viewModel.perform(TransactionEditViewModel.Action.Discard) }
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
                        }
                    )
                }
                item {
                    when (state.selectedTransactionType) {
                        TransactionEditType.EXPENSE -> expenseComponent.AttachWithView()
                        TransactionEditType.INCOME -> incomeComponent.AttachWithView()
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
                        contentDescription = null
                    )
                },
                text = {
                    Text(text = "Save Transaction")
                },
                onClick = { viewModel.perform(TransactionEditViewModel.Action.Save) },
                elevation = FloatingActionButtonDefaults.elevation(8.dp)
            )
        }
    }
}
