package com.hluhovskyi.zero.accounts.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExtendedFloatingActionButton
import androidx.compose.material.FloatingActionButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.accounts.AccountCategory
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.AmountDisplay
import com.hluhovskyi.zero.ui.ModalHeader
import com.hluhovskyi.zero.ui.SelectorCard
import com.hluhovskyi.zero.ui.theme.OnSurface
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLow

internal class AccountEditViewProvider(
    private val viewModel: AccountEditViewModel,
    private val onClose: OnCloseHandler,
) : ViewProvider {

    @Composable
    override fun View() {
        AccountEditView(
            viewModel = viewModel,
            onClose = onClose,
        )
    }
}

@Composable
private fun AccountEditView(
    viewModel: AccountEditViewModel,
    onClose: OnCloseHandler,
) {
    val state by viewModel.state.collectAsState(initial = AccountEditViewModel.State())
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column {
            ModalHeader(
                title = "New Account",
                onClose = { onClose.onClose() },
            )
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AmountDisplay(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 8.dp),
                    amount = state.balance,
                    currencySymbol = state.selectedCurrency?.symbol ?: "",
                    focusRequester = focusRequester,
                    onAmountChange = { balance ->
                        viewModel.perform(AccountEditViewModel.Action.ChangeBalance(balance))
                    },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SelectorCard(
                        modifier = Modifier.weight(1f),
                        label = "Currency",
                        value = state.selectedCurrency?.name ?: "",
                        items = emptyList<String>(),
                        nameMapping = { it },
                        onItemSelected = {},
                        onClick = { viewModel.perform(AccountEditViewModel.Action.OpenCurrencyPicker) },
                    )
                    SelectorCard(
                        modifier = Modifier.weight(1f),
                        label = "Account Type",
                        value = state.category.displayName,
                        items = AccountCategory.entries,
                        nameMapping = { it.displayName },
                        onItemSelected = { category ->
                            viewModel.perform(AccountEditViewModel.Action.ChangeCategory(category))
                        },
                    )
                }

                InputCard(
                    label = "Account Name",
                    value = state.name,
                    placeholder = "e.g. Everyday Checking",
                    onValueChange = { name ->
                        viewModel.perform(AccountEditViewModel.Action.ChangeName(name))
                    },
                )

                InputCard(
                    modifier = Modifier.padding(bottom = 96.dp),
                    label = "Details",
                    value = state.details,
                    placeholder = "e.g. Chase Bank",
                    onValueChange = { details ->
                        viewModel.perform(AccountEditViewModel.Action.ChangeDetails(details))
                    },
                )
            }
        }

        ExtendedFloatingActionButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 32.dp),
            icon = { Icon(Icons.Filled.Check, contentDescription = "Save account") },
            text = { Text("Save") },
            onClick = { viewModel.perform(AccountEditViewModel.Action.Save) },
            elevation = FloatingActionButtonDefaults.elevation(8.dp),
        )
    }
}

@Composable
private fun InputCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = OnSurface,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceContainerLow, RoundedCornerShape(16.dp))
                .padding(horizontal = 20.dp, vertical = 18.dp),
            textStyle = TextStyle(
                fontSize = 15.sp,
                color = OnSurface,
            ),
            singleLine = true,
            decorationBox = { innerTextField ->
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontSize = 15.sp,
                        color = OnSurfaceVariant,
                    )
                }
                innerTextField()
            },
        )
    }
}

private val AccountCategory.displayName: String
    get() = when (this) {
        AccountCategory.CASH -> "Cash"
        AccountCategory.BANK -> "Bank"
        AccountCategory.CREDIT_CARDS -> "Credit Cards"
        AccountCategory.DIGITAL_WALLETS -> "Digital Wallets"
        AccountCategory.CRYPTO -> "Crypto"
        AccountCategory.OTHER -> "Other"
    }
