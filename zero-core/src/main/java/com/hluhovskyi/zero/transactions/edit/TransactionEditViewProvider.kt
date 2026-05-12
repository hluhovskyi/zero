package com.hluhovskyi.zero.transactions.edit

import androidx.compose.animation.ExperimentalAnimationApi
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
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExtendedFloatingActionButton
import androidx.compose.material.FloatingActionButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.hluhovskyi.zero.ui.theme.OnSurface
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant
import com.hluhovskyi.zero.ui.theme.PrimaryContainer
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLow

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
    var menuExpanded by remember { mutableStateOf(false) }

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
                    title = if (state.isEditMode) "Edit Transaction" else "New Transaction",
                    onClose = { viewModel.perform(TransactionEditViewModel.Action.Discard) },
                    trailingContent = if (state.isEditMode) {
                        {
                            Box {
                                IconButton(onClick = { menuExpanded = true }) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreVert,
                                        contentDescription = "More options",
                                        tint = PrimaryContainer,
                                    )
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false },
                                ) {
                                    DropdownMenuItem(
                                        onClick = {
                                            menuExpanded = false
                                            viewModel.perform(TransactionEditViewModel.Action.Delete)
                                        },
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Outlined.Delete,
                                                contentDescription = null,
                                                tint = Color(0xFFBA1A1A),
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = "Delete",
                                                color = Color(0xFFBA1A1A),
                                            )
                                        }
                                    }
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
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 16.dp)
                        .background(SurfaceContainerLow, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.transaction_notes_label),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceVariant,
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
                            color = OnSurface,
                        ),
                        decorationBox = { innerTextField ->
                            if (state.notes.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.transaction_notes_hint),
                                    fontSize = 15.sp,
                                    color = OnSurfaceVariant,
                                )
                            }
                            innerTextField()
                        },
                    )
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
