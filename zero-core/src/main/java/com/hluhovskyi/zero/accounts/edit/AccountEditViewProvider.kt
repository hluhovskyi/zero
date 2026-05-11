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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.accounts.AccountCategory
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.AmountDisplay
import com.hluhovskyi.zero.ui.ModalHeader
import com.hluhovskyi.zero.ui.SelectorCard
import com.hluhovskyi.zero.ui.UiColorScheme
import com.hluhovskyi.zero.ui.common.toUi
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
    val categoryDisplayNames = AccountCategory.entries.associateWith { it.displayName() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column {
            ModalHeader(
                title = if (state.isEditMode) stringResource(R.string.account_edit_title_edit) else stringResource(R.string.account_edit_title_new),
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
                    label = stringResource(R.string.account_edit_opening_balance_label),
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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    IconTile(
                        modifier = Modifier.fillMaxHeight(),
                        image = state.selectedIcon,
                        colorScheme = state.colorScheme.toUi(),
                        imageLoader = imageLoader,
                        onClick = { viewModel.perform(AccountEditViewModel.Action.SelectIcon) },
                    )
                    FormCard(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.account_edit_name_label),
                        value = state.name,
                        placeholder = state.category.namePlaceholder(),
                        onValueChange = { name ->
                            viewModel.perform(AccountEditViewModel.Action.ChangeName(name))
                        },
                    )
                }

                SelectorCard(
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.account_edit_type_label),
                    value = categoryDisplayNames.getValue(state.category),
                    items = AccountCategory.entries,
                    nameMapping = { categoryDisplayNames.getValue(it) },
                    onItemSelected = { category ->
                        viewModel.perform(AccountEditViewModel.Action.ChangeCategory(category))
                    },
                )

                if (state.category != AccountCategory.CASH) {
                    FormCard(
                        label = state.category.detailLabel(),
                        value = state.details,
                        placeholder = state.category.detailPlaceholder(),
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
            icon = { Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.account_edit_save_description)) },
            text = { Text(stringResource(R.string.account_edit_save)) },
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
    colorScheme: UiColorScheme,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
) {
    val scheme = colorScheme
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

@Composable
private fun AccountCategory.displayName(): String = when (this) {
    AccountCategory.CASH -> stringResource(R.string.account_type_cash)
    AccountCategory.BANK -> stringResource(R.string.account_type_bank)
    AccountCategory.CREDIT_CARDS -> stringResource(R.string.account_type_credit_cards)
    AccountCategory.DIGITAL_WALLETS -> stringResource(R.string.account_type_digital_wallets)
    AccountCategory.CRYPTO -> stringResource(R.string.account_type_crypto)
    AccountCategory.OTHER -> stringResource(R.string.account_type_other)
}

@Composable
private fun AccountCategory.namePlaceholder(): String = when (this) {
    AccountCategory.CASH -> stringResource(R.string.account_name_placeholder_cash)
    AccountCategory.CREDIT_CARDS -> stringResource(R.string.account_name_placeholder_credit_cards)
    AccountCategory.BANK -> stringResource(R.string.account_name_placeholder_bank)
    AccountCategory.DIGITAL_WALLETS -> stringResource(R.string.account_name_placeholder_digital_wallets)
    AccountCategory.CRYPTO -> stringResource(R.string.account_name_placeholder_crypto)
    AccountCategory.OTHER -> stringResource(R.string.account_name_placeholder_other)
}

@Composable
private fun AccountCategory.detailLabel(): String = when (this) {
    AccountCategory.CASH -> ""
    AccountCategory.CREDIT_CARDS -> stringResource(R.string.account_detail_label_credit_cards)
    AccountCategory.BANK -> stringResource(R.string.account_detail_label_bank)
    AccountCategory.DIGITAL_WALLETS -> stringResource(R.string.account_detail_label_digital_wallets)
    AccountCategory.CRYPTO -> stringResource(R.string.account_detail_label_crypto)
    AccountCategory.OTHER -> stringResource(R.string.account_detail_label_other)
}

@Composable
private fun AccountCategory.detailPlaceholder(): String = when (this) {
    AccountCategory.CASH -> ""
    AccountCategory.CREDIT_CARDS -> stringResource(R.string.account_detail_placeholder_credit_cards)
    AccountCategory.BANK -> stringResource(R.string.account_detail_placeholder_bank)
    AccountCategory.DIGITAL_WALLETS -> stringResource(R.string.account_detail_placeholder_digital_wallets)
    AccountCategory.CRYPTO -> stringResource(R.string.account_detail_placeholder_crypto)
    AccountCategory.OTHER -> ""
}
