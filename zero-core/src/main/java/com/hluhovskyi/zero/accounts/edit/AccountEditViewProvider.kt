package com.hluhovskyi.zero.accounts.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExtendedFloatingActionButton
import androidx.compose.material.FloatingActionButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
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
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.accounts.AccountCategory
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.AmountDisplay
import com.hluhovskyi.zero.ui.ModalHeader
import com.hluhovskyi.zero.ui.SelectorCard
import com.hluhovskyi.zero.ui.UiColorScheme
import com.hluhovskyi.zero.ui.theme.OnSurface
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLow

internal class AccountEditViewProvider(
    private val viewModel: AccountEditViewModel,
    private val onClose: OnCloseHandler,
    private val imageLoader: ImageLoader,
) : ViewProvider {

    @Composable
    override fun View() {
        AccountEditView(
            viewModel = viewModel,
            onClose = onClose,
            imageLoader = imageLoader,
        )
    }
}

@Composable
private fun AccountEditView(
    viewModel: AccountEditViewModel,
    onClose: OnCloseHandler,
    imageLoader: ImageLoader,
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
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AmountDisplay(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 16.dp),
                    label = "OPENING BALANCE",
                    amount = state.balance,
                    currencySymbol = state.selectedCurrency?.symbol ?: "",
                    focusRequester = focusRequester,
                    onAmountChange = { balance ->
                        viewModel.perform(AccountEditViewModel.Action.ChangeBalance(balance))
                    },
                    onCurrencyClick = {
                        viewModel.perform(AccountEditViewModel.Action.OpenCurrencyPicker)
                    },
                )

                FormCard(
                    label = "Account Name",
                    value = state.name,
                    placeholder = state.category.namePlaceholder,
                    onValueChange = { name ->
                        viewModel.perform(AccountEditViewModel.Action.ChangeName(name))
                    },
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    IconTile(
                        modifier = Modifier.fillMaxHeight(),
                        image = state.selectedIcon,
                        imageLoader = imageLoader,
                        onClick = { viewModel.perform(AccountEditViewModel.Action.SelectIcon) },
                    )
                    SelectorCard(
                        modifier = Modifier.weight(1f),
                        label = "Type",
                        value = state.category.displayName,
                        items = AccountCategory.entries,
                        nameMapping = { it.displayName },
                        onItemSelected = { category ->
                            viewModel.perform(AccountEditViewModel.Action.ChangeCategory(category))
                        },
                    )
                }

                if (state.category != AccountCategory.CASH) {
                    FormCard(
                        label = state.category.detailLabel,
                        value = state.details,
                        placeholder = state.category.detailPlaceholder,
                        onValueChange = { details ->
                            viewModel.perform(AccountEditViewModel.Action.ChangeDetails(details))
                        },
                    )
                }

                Spacer(modifier = Modifier.height(96.dp))
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
private fun FormCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceContainerLow, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = OnSurfaceVariant,
            letterSpacing = 1.5.sp,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
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

@Composable
private fun IconTile(
    modifier: Modifier = Modifier,
    image: Image,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
) {
    val scheme = UiColorScheme.default()
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(scheme.background, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        imageLoader.View(
            image = image,
            modifier = Modifier
                .align(Alignment.Center)
                .size(26.dp),
            tint = scheme.primary,
        )
        Icon(
            imageVector = Icons.Filled.ArrowDropDown,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp)
                .size(14.dp),
            tint = scheme.primary,
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

private val AccountCategory.namePlaceholder: String
    get() = when (this) {
        AccountCategory.CASH -> "e.g. Wallet"
        AccountCategory.CREDIT_CARDS -> "e.g. Amex Gold"
        AccountCategory.BANK -> "e.g. Chase Sapphire"
        AccountCategory.DIGITAL_WALLETS -> "e.g. PayPal"
        AccountCategory.CRYPTO -> "e.g. Bitcoin"
        AccountCategory.OTHER -> "e.g. Savings"
    }

private val AccountCategory.detailLabel: String
    get() = when (this) {
        AccountCategory.CASH -> ""
        AccountCategory.CREDIT_CARDS -> "Last 4 / Nickname"
        AccountCategory.BANK -> "Account Number / Type"
        AccountCategory.DIGITAL_WALLETS -> "Account Details"
        AccountCategory.CRYPTO -> "Wallet Address"
        AccountCategory.OTHER -> "Details"
    }

private val AccountCategory.detailPlaceholder: String
    get() = when (this) {
        AccountCategory.CASH -> ""
        AccountCategory.CREDIT_CARDS -> "••• 1209"
        AccountCategory.BANK -> "Checking"
        AccountCategory.DIGITAL_WALLETS -> "user@example.com"
        AccountCategory.CRYPTO -> "bc1q..."
        AccountCategory.OTHER -> ""
    }
