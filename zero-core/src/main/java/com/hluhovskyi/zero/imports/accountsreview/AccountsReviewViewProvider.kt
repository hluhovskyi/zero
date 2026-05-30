package com.hluhovskyi.zero.imports.accountsreview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.imports.ImportAccount
import com.hluhovskyi.zero.imports.ImportStrategyChip
import com.hluhovskyi.zero.imports.ResolveStrategy
import com.hluhovskyi.zero.ui.ImportStepHeader
import com.hluhovskyi.zero.ui.theme.ZeroTheme

internal class AccountsReviewViewProvider(
    private val viewModel: AccountsReviewViewModel,
    private val imageLoader: ImageLoader,
) : ViewProvider {

    @Composable
    override fun View() {
        AccountsReviewView(viewModel = viewModel, imageLoader = imageLoader)
    }
}

@Composable
private fun AccountsReviewView(viewModel: AccountsReviewViewModel, imageLoader: ImageLoader) {
    val state by viewModel.state.collectAsState(initial = AccountsReviewViewModel.State())

    Column(modifier = Modifier.fillMaxSize()) {
        ImportStepHeader(
            title = stringResource(R.string.import_accounts_review_title),
            step = 2,
            totalSteps = 4,
            onBack = { viewModel.perform(AccountsReviewViewModel.Action.Back) },
        )
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.import_accounts_review_info, state.accounts.size, state.totalTransactions),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = ZeroTheme.colors.onSurfaceVariant,
                    letterSpacing = 0.08.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp, start = 4.dp),
                )
            }
            items(state.accounts, key = { it.id.value }) { account ->
                val strategy = state.strategies[account.id]
                    ?: if (account.existingId != null) ResolveStrategy.Merge else ResolveStrategy.New
                AccountRow(
                    account = account,
                    strategy = strategy,
                    imageLoader = imageLoader,
                    onChange = { viewModel.perform(AccountsReviewViewModel.Action.SetStrategy(account.id, it)) },
                )
            }
            item { Box(modifier = Modifier.padding(bottom = 8.dp)) }
        }
        Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp).padding(bottom = 16.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ZeroTheme.colors.primaryContainer)
                    .clickable { viewModel.perform(AccountsReviewViewModel.Action.Next) }
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.import_accounts_review_continue),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = ZeroTheme.colors.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun AccountRow(
    account: ImportAccount,
    strategy: ResolveStrategy,
    imageLoader: ImageLoader,
    onChange: (ResolveStrategy) -> Unit,
) {
    val isSkipped = strategy == ResolveStrategy.Skip
    val options = if (account.existingId != null) {
        listOf(ResolveStrategy.Merge, ResolveStrategy.New, ResolveStrategy.Skip)
    } else {
        listOf(ResolveStrategy.New, ResolveStrategy.Skip)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ZeroTheme.colors.surfaceContainerLowest)
            .alpha(if (isSkipped) 0.4f else 1f)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(ZeroTheme.colors.surfaceContainer, shape = RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val icon = account.icon
            if (icon != null) {
                imageLoader.View(
                    image = icon,
                    modifier = Modifier.size(22.dp),
                    tint = ZeroTheme.colors.onSurfaceVariant,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.AccountBalance,
                    contentDescription = null,
                    tint = ZeroTheme.colors.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = account.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ZeroTheme.colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (account.existingId != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(ZeroTheme.colors.importMergeContainer)
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.import_resolve_exists_badge).uppercase(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = ZeroTheme.colors.primaryContainer,
                            letterSpacing = 0.06.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
            val displayedCount = when (strategy) {
                ResolveStrategy.Merge -> account.newTransactionCount
                ResolveStrategy.New -> account.transactionCount
                ResolveStrategy.Skip -> 0
            }
            Text(
                text = if (isSkipped) {
                    stringResource(R.string.import_resolve_wont_be_imported)
                } else {
                    stringResource(R.string.import_accounts_review_tx_count, displayedCount)
                },
                fontSize = 11.sp,
                color = if (isSkipped) ZeroTheme.colors.outline else ZeroTheme.colors.onSurfaceVariant,
            )
        }
        ImportStrategyChip(
            selected = strategy,
            options = options,
            onChange = onChange,
        )
    }
}
