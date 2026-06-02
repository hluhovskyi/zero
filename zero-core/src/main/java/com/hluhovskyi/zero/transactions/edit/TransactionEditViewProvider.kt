package com.hluhovskyi.zero.transactions.edit

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.transactions.edit.common.ExpenseIncomeForm
import com.hluhovskyi.zero.transactions.edit.transfer.TransferForm
import com.hluhovskyi.zero.ui.AmountField
import com.hluhovskyi.zero.ui.AmountKeypad
import com.hluhovskyi.zero.ui.ModalHeader
import com.hluhovskyi.zero.ui.SegmentedToggle
import com.hluhovskyi.zero.ui.ZeroFab
import com.hluhovskyi.zero.ui.theme.ZeroTheme

internal class TransactionEditViewProvider(
    private val viewModel: TransactionEditViewModel,
    private val imageLoader: ImageLoader,
    private val isNewTransaction: Boolean,
) : ViewProvider {

    @Composable
    override fun View() {
        TransactionEditView(viewModel, imageLoader, isNewTransaction)
    }
}

@Composable
private fun TransactionEditView(
    viewModel: TransactionEditViewModel,
    imageLoader: ImageLoader,
    isNewTransaction: Boolean,
) {
    val state by viewModel.state.collectAsState(initial = TransactionEditViewModel.State())
    var menuExpanded by remember { mutableStateOf(false) }
    val labelExpense = stringResource(R.string.transaction_type_expense)
    val labelIncome = stringResource(R.string.transaction_type_income)
    val labelTransfer = stringResource(R.string.transaction_type_transfer)

    val isTransfer = state.selectedTransactionType == TransactionEditType.TRANSFER

    // Keypad opens on tapping the amount; auto-opens for a new transaction and stays open on
    // transfer (which has no pinned hero amount to tap).
    var keypadVisible by rememberSaveable { mutableStateOf(isNewTransaction) }
    BackHandler(enabled = keypadVisible) { keypadVisible = false }
    LaunchedEffect(isTransfer) { if (isTransfer) keypadVisible = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
    ) {
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
                { EditMenu(menuExpanded, { menuExpanded = it }, viewModel) }
            } else {
                null
            },
        )

        SegmentedToggle(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp),
            items = state.transactionTypes,
            selectedItem = state.selectedTransactionType,
            onItemSelected = { viewModel.perform(TransactionEditViewModel.Action.ChangeTransactionType(it)) },
            labelMapping = { type ->
                when (type) {
                    TransactionEditType.EXPENSE -> labelExpense
                    TransactionEditType.INCOME -> labelIncome
                    TransactionEditType.TRANSFER -> labelTransfer
                }
            },
        )

        // Pinned hero amount — hidden on transfer (its From/To amounts live in the form).
        if (!isTransfer) {
            AmountField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 16.dp, bottom = 8.dp)
                    .testTag("TransactionEdit.amountField"),
                caption = stringResource(R.string.transaction_edit_amount_display_label),
                currencySymbol = state.currencySymbol,
                value = state.amount,
                focused = state.keypadTarget == TransactionEditFocusTarget.Amount,
                hero = true,
                onFocus = {
                    keypadVisible = true
                    viewModel.perform(TransactionEditViewModel.Action.FocusAmount)
                },
                onCurrencyClick = if (state.canPickCurrency) {
                    { viewModel.perform(TransactionEditViewModel.Action.PickCurrency) }
                } else {
                    null
                },
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
        ) {
            item {
                // Its own distinct flow so the form recomposes only on form changes, not on every
                // header/amount/keypad emission.
                val form by viewModel.form.collectAsState(
                    initial = TransactionEditViewModel.Form.ExpenseIncome(),
                )
                when (val current = form) {
                    is TransactionEditViewModel.Form.ExpenseIncome ->
                        ExpenseIncomeForm(current, imageLoader, viewModel::perform)
                    is TransactionEditViewModel.Form.Transfer ->
                        TransferForm(current, viewModel::perform)
                }
            }
            item { NotesField(state.notes, viewModel) }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.End,
        ) {
            ZeroFab(
                modifier = Modifier.padding(end = 16.dp, bottom = 12.dp),
                onClick = { viewModel.perform(TransactionEditViewModel.Action.Save) },
                icon = Icons.Filled.Check,
                contentDescription = stringResource(R.string.transaction_edit_save),
                expanded = true,
                text = stringResource(R.string.transaction_edit_save),
            )
            AnimatedVisibility(visible = keypadVisible) {
                val target = state.keypadTarget
                AmountKeypad(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ZeroTheme.colors.surfaceContainerLow)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    value = when (target) {
                        TransactionEditFocusTarget.Rate -> state.rate
                        TransactionEditFocusTarget.Received -> state.targetAmount
                        TransactionEditFocusTarget.Amount -> state.amount
                    },
                    onChange = {
                        when (target) {
                            TransactionEditFocusTarget.Rate ->
                                viewModel.perform(TransactionEditViewModel.Action.ChangeRate(it))
                            TransactionEditFocusTarget.Received ->
                                viewModel.perform(TransactionEditViewModel.Action.ChangeTargetAmount(it))
                            TransactionEditFocusTarget.Amount ->
                                viewModel.perform(TransactionEditViewModel.Action.ChangeAmount(it))
                        }
                    },
                    maxDecimals = if (target == TransactionEditFocusTarget.Rate) 6 else 2,
                    keyHeight = 58.dp,
                )
            }
        }
    }
}

@Composable
private fun EditMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    viewModel: TransactionEditViewModel,
) {
    Box {
        IconButton(onClick = { onExpandedChange(true) }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.transaction_edit_more_options_description),
                tint = ZeroTheme.colors.primaryContainer,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            DropdownMenuItem(
                onClick = {
                    onExpandedChange(false)
                    viewModel.perform(TransactionEditViewModel.Action.Duplicate)
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        tint = ZeroTheme.colors.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = stringResource(R.string.transaction_duplicate), color = ZeroTheme.colors.onSurface)
                }
            }
            DropdownMenuItem(
                onClick = {
                    onExpandedChange(false)
                    viewModel.perform(TransactionEditViewModel.Action.Delete)
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = ZeroTheme.colors.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = stringResource(R.string.transaction_edit_delete), color = ZeroTheme.colors.error)
                }
            }
        }
    }
}

@Composable
private fun NotesField(notes: String, viewModel: TransactionEditViewModel) {
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
            value = notes,
            onValueChange = { viewModel.perform(TransactionEditViewModel.Action.ChangeNotes(it)) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = ZeroTheme.colors.onSurface,
            ),
            decorationBox = { innerTextField ->
                if (notes.isEmpty()) {
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
