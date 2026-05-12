package com.hluhovskyi.zero.accounts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Unarchive
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.CategoryIconView
import com.hluhovskyi.zero.ui.common.toUi
import com.hluhovskyi.zero.ui.theme.Error
import com.hluhovskyi.zero.ui.theme.OnSecondary
import com.hluhovskyi.zero.ui.theme.OnSurface
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant
import com.hluhovskyi.zero.ui.theme.OutlineVariant
import com.hluhovskyi.zero.ui.theme.Primary
import com.hluhovskyi.zero.ui.theme.Secondary
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLow
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLowest

internal class AccountViewProvider(
    private val viewModel: AccountViewModel,
    private val imageLoader: ImageLoader,
    private val amountFormatter: AmountFormatter,
    private val onAddAccount: OnAddAccountHandler,
) : ViewProvider {

    @Composable
    override fun View() {
        AccountView(
            viewModel = viewModel,
            imageLoader = imageLoader,
            amountFormatter = amountFormatter,
            onAddAccount = onAddAccount,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AccountView(
    viewModel: AccountViewModel,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
    onAddAccount: OnAddAccountHandler,
) {
    val state by viewModel.state.collectAsState(initial = AccountViewModel.State())
    val grouped = remember(state.accounts) {
        state.accounts
            .groupBy { it.category }
            .entries
            .sortedBy { it.key.ordinal }
    }
    var expandedItemId: Id.Known? by remember { mutableStateOf(null) }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            NetWorthHeader(
                balance = amountFormatter.format(
                    amount = state.balance,
                    currencySymbol = state.currency?.symbol.orEmpty(),
                ),
                assets = amountFormatter.format(
                    amount = state.assets,
                    currencySymbol = state.currency?.symbol.orEmpty(),
                ),
                liabilities = amountFormatter.format(
                    amount = state.liabilities,
                    currencySymbol = state.currency?.symbol.orEmpty(),
                ),
            )
        }
        item {
            MyAccountsSectionHeader(onAddAccount = { onAddAccount.onAddAccount() })
        }
        grouped.forEach { (category, accounts) ->
            item(key = category.name) {
                CategoryHeader(category = category)
            }
            items(accounts, key = { it.id.value }) { account ->
                AccountRow(
                    account = account,
                    imageLoader = imageLoader,
                    amountFormatter = amountFormatter,
                    onClick = { viewModel.perform(AccountViewModel.Action.Select(account.id)) },
                    onLongClick = { expandedItemId = account.id },
                    menuExpanded = expandedItemId == account.id,
                    onMenuDismiss = { expandedItemId = null },
                    onEditClick = {
                        expandedItemId = null
                        viewModel.perform(AccountViewModel.Action.Edit(account.id))
                    },
                    onArchiveClick = {
                        expandedItemId = null
                        viewModel.perform(AccountViewModel.Action.Archive(account.id))
                    },
                    onUnarchiveClick = {
                        expandedItemId = null
                        viewModel.perform(AccountViewModel.Action.Unarchive(account.id))
                    },
                )
            }
        }
    }
}

@Composable
private fun NetWorthHeader(
    balance: String,
    assets: String,
    liabilities: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceContainerLow)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.account_total_net_worth),
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = OnSurfaceVariant,
                letterSpacing = 1.sp,
            ),
        )
        Text(
            text = balance,
            style = TextStyle(
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Primary,
                letterSpacing = (-0.5).sp,
            ),
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.account_assets),
                    style = TextStyle(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceVariant,
                        letterSpacing = 1.sp,
                    ),
                )
                Text(
                    text = assets,
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Secondary,
                    ),
                )
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(32.dp)
                    .background(OutlineVariant)
                    .align(Alignment.CenterVertically),
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.account_liabilities),
                    style = TextStyle(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceVariant,
                        letterSpacing = 1.sp,
                    ),
                )
                Text(
                    text = liabilities,
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Error,
                    ),
                )
            }
        }
    }
}

@Composable
private fun MyAccountsSectionHeader(onAddAccount: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.account_my_accounts),
            style = TextStyle(
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnSurface,
            ),
        )
        Box(
            modifier = Modifier
                .background(
                    color = Secondary,
                    shape = RoundedCornerShape(20.dp),
                )
                .clickable(onClick = onAddAccount)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.account_add),
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = OnSecondary,
                ),
            )
        }
    }
}

@Composable
private fun CategoryHeader(category: AccountCategory) {
    Text(
        text = category.displayName(),
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
        style = TextStyle(
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = OnSurfaceVariant,
            letterSpacing = 0.8.sp,
        ),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AccountRow(
    account: Account,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    menuExpanded: Boolean,
    onMenuDismiss: () -> Unit,
    onEditClick: () -> Unit,
    onArchiveClick: () -> Unit,
    onUnarchiveClick: () -> Unit,
) {
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .background(SurfaceContainerLowest, shape = RoundedCornerShape(12.dp))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CategoryIconView(
                colorScheme = account.colorScheme.toUi(),
                size = 44.dp,
                contentPadding = 10.dp,
            ) { tint ->
                imageLoader.View(
                    modifier = Modifier.size(24.dp),
                    image = account.icon,
                    tint = tint,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.name,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurface,
                    ),
                )
                if (account.details != null) {
                    Text(
                        text = account.details,
                        style = TextStyle(
                            fontSize = 12.sp,
                            color = OnSurfaceVariant,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = amountFormatter.format(
                    amount = account.balance,
                    currencySymbol = account.currencySymbol,
                ),
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (account.balance < 0L) Error else OnSurface,
                ),
            )
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = onMenuDismiss,
        ) {
            DropdownMenuItem(onClick = onEditClick) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.account_detail_edit))
            }
            if (account.archivedAt == null) {
                DropdownMenuItem(onClick = onArchiveClick) {
                    Icon(
                        imageVector = Icons.Filled.Archive,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.account_detail_archive))
                }
            } else {
                DropdownMenuItem(onClick = onUnarchiveClick) {
                    Icon(
                        imageVector = Icons.Filled.Unarchive,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.account_detail_unarchive))
                }
            }
        }
    }
}

@Composable
private fun AccountCategory.displayName(): String = when (this) {
    AccountCategory.CASH -> stringResource(R.string.account_header_cash)
    AccountCategory.BANK -> stringResource(R.string.account_header_bank)
    AccountCategory.CREDIT_CARDS -> stringResource(R.string.account_header_credit_cards)
    AccountCategory.DIGITAL_WALLETS -> stringResource(R.string.account_header_digital_wallets)
    AccountCategory.CRYPTO -> stringResource(R.string.account_header_crypto)
    AccountCategory.OTHER -> stringResource(R.string.account_header_other)
}
