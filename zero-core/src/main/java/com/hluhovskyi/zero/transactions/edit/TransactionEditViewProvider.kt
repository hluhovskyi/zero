package com.hluhovskyi.zero.transactions.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.common.AttachWithView
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.ModalHeader
import com.hluhovskyi.zero.ui.SegmentedToggle
import com.hluhovskyi.zero.ui.ZeroFab
import com.hluhovskyi.zero.ui.theme.ZeroTheme

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

@Composable
private fun TransactionEditView(
    viewModel: TransactionEditViewModel,
    expenseIncomeComponent: Buildable<out AttachableViewComponent>,
    transferComponent: Buildable<out AttachableViewComponent>,
) {
    val state by viewModel.state.collectAsState(initial = TransactionEditViewModel.State())
    var menuExpanded by remember { mutableStateOf(false) }
    val labelExpense = stringResource(R.string.transaction_type_expense)
    val labelIncome = stringResource(R.string.transaction_type_income)
    val labelTransfer = stringResource(R.string.transaction_type_transfer)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ZeroTheme.colors.surface),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            item {
                val title = when (state.headerMode) {
                    is TransactionEditViewModel.HeaderMode.New -> stringResource(R.string.transaction_new_title)
                    is TransactionEditViewModel.HeaderMode.Edit -> stringResource(R.string.transaction_edit_title)
                    is TransactionEditViewModel.HeaderMode.DuplicateFrom -> stringResource(R.string.transaction_duplicate_from_title)
                }
                val subtitle = (state.headerMode as? TransactionEditViewModel.HeaderMode.DuplicateFrom)?.subtitle
                ModalHeader(
                    title = title,
                    subtitle = subtitle,
                    onClose = { viewModel.perform(TransactionEditViewModel.Action.Discard) },
                    trailingContent = if (state.headerMode is TransactionEditViewModel.HeaderMode.Edit) {
                        {
                            Box {
                                IconButton(onClick = { menuExpanded = true }) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreVert,
                                        contentDescription = stringResource(R.string.transaction_edit_more_options_description),
                                        tint = ZeroTheme.colors.primaryContainer,
                                    )
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false },
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Outlined.ContentCopy,
                                                    contentDescription = null,
                                                    tint = ZeroTheme.colors.onSurface,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    text = stringResource(R.string.transaction_duplicate),
                                                    color = ZeroTheme.colors.onSurface,
                                                )
                                            }
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            viewModel.perform(TransactionEditViewModel.Action.Duplicate)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Delete,
                                                    contentDescription = null,
                                                    tint = ZeroTheme.colors.error,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    text = stringResource(R.string.transaction_edit_delete),
                                                    color = ZeroTheme.colors.error,
                                                )
                                            }
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            viewModel.perform(TransactionEditViewModel.Action.Delete)
                                        },
                                    )
                                }
                            }
                        }
                    } else {
                        null
                    },
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
                            TransactionEditType.EXPENSE -> labelExpense
                            TransactionEditType.INCOME -> labelIncome
                            TransactionEditType.TRANSFER -> labelTransfer
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
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 16.dp)
                        .background(ZeroTheme.colors.surfaceContainerLow, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.transaction_notes_label).uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = ZeroTheme.colors.onSurfaceVariant,
                        letterSpacing = 1.5.sp,
                    )
                    BasicTextField(
                        value = state.notes,
                        onValueChange = { notes ->
                            viewModel.perform(TransactionEditViewModel.Action.ChangeNotes(notes))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ZeroTheme.colors.onSurface,
                        ),
                        decorationBox = { innerTextField ->
                            if (state.notes.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.transaction_notes_hint),
                                    fontSize = 15.sp,
                                    color = ZeroTheme.colors.onSurfaceVariant,
                                )
                            }
                            innerTextField()
                        },
                    )
                }
            }
        }

        ZeroFab(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 32.dp),
            onClick = { viewModel.perform(TransactionEditViewModel.Action.Save) },
            icon = Icons.Filled.Check,
            contentDescription = stringResource(R.string.transaction_edit_save),
            expanded = true,
            text = stringResource(R.string.transaction_edit_save),
        )
    }
}
