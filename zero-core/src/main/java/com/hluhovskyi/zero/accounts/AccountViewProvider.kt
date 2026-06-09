package com.hluhovskyi.zero.accounts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.hluhovskyi.zero.ui.ZeroFab
import com.hluhovskyi.zero.ui.chart.LineChart
import com.hluhovskyi.zero.ui.chart.LineChartData
import com.hluhovskyi.zero.ui.chart.SignedLineChart
import com.hluhovskyi.zero.ui.common.toUi
import com.hluhovskyi.zero.ui.theme.ZeroTheme

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
    val state by viewModel.state.collectAsState()
    val grouped = state.activeAccountsByCategory
    val archivedAccounts = state.archivedAccounts
    var expandedItemId: Id.Known? by remember { mutableStateOf(null) }
    var showArchived by remember { mutableStateOf(false) }
    val fabExpanded = !state.hasAddedAccount
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item {
                val netWorth = state.netWorth
                val symbol = netWorth.currency?.symbol.orEmpty()
                val growthChip: String? = when (val change = netWorth.change) {
                    is NetWorthChange.Growth ->
                        stringResource(R.string.account_net_worth_growth, change.percent.toString())
                    is NetWorthChange.Improvement ->
                        stringResource(
                            R.string.account_net_worth_improvement,
                            amountFormatter.format(amount = change.delta, currencySymbol = symbol),
                        )
                    null -> null
                }
                NetWorthHeader(
                    balance = amountFormatter.format(amount = netWorth.balance, currencySymbol = symbol),
                    assets = amountFormatter.format(amount = netWorth.assets, currencySymbol = symbol),
                    liabilities = amountFormatter.format(amount = netWorth.liabilities, currencySymbol = symbol),
                    trendPoints = netWorth.trendPoints,
                    isNegative = netWorth.isNegative,
                    growthChip = growthChip,
                )
            }
            item {
                MyAccountsSectionHeader()
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
            if (archivedAccounts.isNotEmpty()) {
                item(key = "archived_footer") {
                    ArchivedFooter(
                        archivedAccounts = archivedAccounts,
                        showArchived = showArchived,
                        onToggle = { showArchived = !showArchived },
                        onAccountClick = { account ->
                            viewModel.perform(AccountViewModel.Action.Select(account.id))
                        },
                        imageLoader = imageLoader,
                        amountFormatter = amountFormatter,
                    )
                }
            }
        }
        ZeroFab(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 32.dp),
            onClick = { onAddAccount.onAddAccount() },
            icon = Icons.Filled.Add,
            contentDescription = stringResource(R.string.account_add),
            expanded = fabExpanded,
            text = stringResource(R.string.account_add),
        )
    }
}

@Composable
private fun NetWorthHeader(
    balance: String,
    assets: String,
    liabilities: String,
    trendPoints: List<Float>,
    isNegative: Boolean,
    growthChip: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ZeroTheme.colors.surfaceContainerLow)
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.account_total_net_worth).uppercase(),
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ZeroTheme.colors.onSurfaceVariant,
                        letterSpacing = 1.sp,
                    ),
                )
                Text(
                    text = balance,
                    style = TextStyle(
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isNegative) ZeroTheme.colors.error else ZeroTheme.colors.primary,
                        letterSpacing = (-0.5).sp,
                    ),
                )
            }
            if (growthChip != null) {
                GrowthChip(text = growthChip)
            }
        }
        if (isNegative) {
            SignedLineChart(
                data = LineChartData(trendPoints),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(74.dp),
            )
        } else {
            LineChart(
                data = LineChartData(trendPoints),
                lineColor = ZeroTheme.colors.secondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(74.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                MiniStat(
                    label = stringResource(R.string.account_assets),
                    value = assets,
                    valueColor = ZeroTheme.colors.secondary,
                )
                MiniStat(
                    label = stringResource(R.string.account_liabilities),
                    value = liabilities,
                    valueColor = ZeroTheme.colors.error,
                )
            }
            ViewTrend()
        }
    }
}

@Composable
private fun GrowthChip(text: String) {
    Row(
        modifier = Modifier
            .background(ZeroTheme.colors.secondary.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.ArrowDropUp,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = ZeroTheme.colors.secondary,
        )
        Text(
            text = text,
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = ZeroTheme.colors.secondary,
            ),
        )
    }
}

@Composable
private fun MiniStat(label: String, value: String, valueColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = ZeroTheme.colors.onSurfaceVariant,
                letterSpacing = 0.8.sp,
            ),
        )
        Text(
            text = value,
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor,
            ),
        )
    }
}

@Composable
private fun ViewTrend() {
    Row(
        // The trend destination screen is out of scope; the affordance is present but inert.
        modifier = Modifier.clickable {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = stringResource(R.string.account_view_trend),
            style = TextStyle(
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = ZeroTheme.colors.primary,
            ),
        )
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = ZeroTheme.colors.primary,
        )
    }
}

@Composable
private fun MyAccountsSectionHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.account_my_accounts),
            style = TextStyle(
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = ZeroTheme.colors.onSurface,
            ),
        )
    }
}

@Composable
private fun CategoryHeader(category: AccountCategory) {
    Text(
        text = category.displayName().uppercase(),
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
        style = TextStyle(
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = ZeroTheme.colors.onSurfaceVariant,
            letterSpacing = 0.8.sp,
        ),
    )
}

@Composable
private fun ArchivedFooter(
    archivedAccounts: List<Account>,
    showArchived: Boolean,
    onToggle: () -> Unit,
    onAccountClick: (Account) -> Unit,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (showArchived) 180f else 0f,
        animationSpec = tween(200),
        label = "chevron",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 20.dp, bottom = 24.dp),
    ) {
        HorizontalDivider(color = ZeroTheme.colors.surfaceContainer, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(top = 16.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Archive,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = ZeroTheme.colors.outline,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (showArchived) {
                    stringResource(R.string.account_archived_hide, archivedAccounts.size)
                } else {
                    stringResource(R.string.account_archived_show, archivedAccounts.size)
                },
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ZeroTheme.colors.outline,
                    letterSpacing = 0.4.sp,
                ),
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier
                    .size(14.dp)
                    .rotate(chevronRotation),
                tint = ZeroTheme.colors.outline,
            )
        }
        AnimatedVisibility(visible = showArchived) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    text = stringResource(R.string.account_archived_hidden_notice).uppercase(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    style = TextStyle(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = ZeroTheme.colors.outline,
                        letterSpacing = 1.2.sp,
                    ),
                    textAlign = TextAlign.Center,
                )
                archivedAccounts.forEach { account ->
                    ArchivedAccountRow(
                        account = account,
                        imageLoader = imageLoader,
                        amountFormatter = amountFormatter,
                        onClick = { onAccountClick(account) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ArchivedAccountRow(
    account: Account,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
    onClick: () -> Unit,
) {
    val outlineColor = ZeroTheme.colors.outlineVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .alpha(0.78f)
            .drawBehind {
                drawRoundRect(
                    color = outlineColor,
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                    ),
                )
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(ZeroTheme.colors.surfaceContainer, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            imageLoader.View(
                modifier = Modifier.size(20.dp),
                image = account.icon,
                tint = ZeroTheme.colors.outline,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.name,
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ZeroTheme.colors.onSurfaceVariant,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .background(ZeroTheme.colors.surfaceContainer, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = stringResource(R.string.account_archived_badge).uppercase(),
                        style = TextStyle(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = ZeroTheme.colors.outline,
                            letterSpacing = 1.sp,
                        ),
                    )
                }
                if (account.details != null) {
                    Text(
                        text = account.details,
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = ZeroTheme.colors.outline,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Text(
            text = amountFormatter.format(
                amount = account.balance,
                currencySymbol = account.currencySymbol,
            ),
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = ZeroTheme.colors.outline,
            ),
        )
    }
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
                .background(ZeroTheme.colors.surfaceContainerLowest, shape = RoundedCornerShape(12.dp))
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
                        color = ZeroTheme.colors.onSurface,
                    ),
                )
                if (account.details != null) {
                    Text(
                        text = account.details,
                        style = TextStyle(
                            fontSize = 12.sp,
                            color = ZeroTheme.colors.onSurfaceVariant,
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
                    color = if (account.balance < 0L) ZeroTheme.colors.error else ZeroTheme.colors.onSurface,
                ),
            )
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = onMenuDismiss,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.account_detail_edit)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                onClick = onEditClick,
            )
            if (account.archivedAt == null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.account_detail_archive)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Archive,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onClick = onArchiveClick,
                )
            } else {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.account_detail_unarchive)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Unarchive,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onClick = onUnarchiveClick,
                )
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
