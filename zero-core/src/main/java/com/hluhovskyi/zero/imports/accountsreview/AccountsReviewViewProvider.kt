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
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.imports.ImportAccount
import com.hluhovskyi.zero.ui.ImportStepHeader
import com.hluhovskyi.zero.ui.theme.OnSurface
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant
import com.hluhovskyi.zero.ui.theme.PrimaryContainer
import com.hluhovskyi.zero.ui.theme.SurfaceContainer
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLow
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLowest

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

    val totalTransactions = state.accounts.sumOf { it.transactionCount }
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
                    text = stringResource(R.string.import_accounts_review_info, state.accounts.size, totalTransactions),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceVariant,
                    letterSpacing = 0.08.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp, start = 4.dp),
                )
            }
            items(state.accounts, key = { it.id.value }) { account ->
                AccountRow(account = account, imageLoader = imageLoader)
            }
            item { Box(modifier = Modifier.padding(bottom = 8.dp)) }
        }
        Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp).padding(bottom = 16.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(PrimaryContainer)
                    .clickable { viewModel.perform(AccountsReviewViewModel.Action.Next) }
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = stringResource(R.string.import_accounts_review_continue), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun AccountRow(account: ImportAccount, imageLoader: ImageLoader) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceContainerLowest)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(SurfaceContainer, shape = RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val icon = account.icon
            if (icon != null) {
                imageLoader.View(
                    image = icon,
                    modifier = Modifier.size(24.dp),
                    tint = OnSurfaceVariant,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.AccountBalance,
                    contentDescription = null,
                    tint = OnSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = account.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = OnSurface)
            Text(text = account.currencyId.value, fontSize = 12.sp, color = OnSurfaceVariant)
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(SurfaceContainerLow)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.import_accounts_review_tx_count, account.transactionCount),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnSurfaceVariant,
            )
        }
    }
}
