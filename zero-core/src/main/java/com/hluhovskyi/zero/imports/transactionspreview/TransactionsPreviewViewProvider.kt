package com.hluhovskyi.zero.imports.transactionspreview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.transaction.TransactionView
import com.hluhovskyi.zero.ui.ImportStepHeader
import com.hluhovskyi.zero.ui.UiColorScheme
import com.hluhovskyi.zero.ui.common.toUi
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant
import com.hluhovskyi.zero.ui.theme.Secondary

private val TRANSFER_COLOR_SCHEME = UiColorScheme.default()

internal class TransactionsPreviewViewProvider(
    private val viewModel: TransactionsPreviewViewModel,
    private val imageLoader: ImageLoader,
) : ViewProvider {

    @Composable
    override fun View() {
        TransactionsPreviewView(viewModel = viewModel, imageLoader = imageLoader)
    }
}

@Composable
private fun TransactionsPreviewView(
    viewModel: TransactionsPreviewViewModel,
    imageLoader: ImageLoader,
) {
    val state by viewModel.state.collectAsState(initial = TransactionsPreviewViewModel.State())

    Column(modifier = Modifier.fillMaxSize()) {
        ImportStepHeader(
            title = stringResource(R.string.import_transactions_preview_title),
            step = 3,
            totalSteps = 4,
            onBack = { viewModel.perform(TransactionsPreviewViewModel.Action.Back) },
        )
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.import_transactions_preview_info, state.totalCount),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceVariant,
                    letterSpacing = 0.08.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp, start = 4.dp),
                )
            }
            state.groups.forEach { group ->
                item(key = "header_${group.dateLabel}") {
                    Text(
                        text = group.dateLabel.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurfaceVariant,
                        letterSpacing = 0.08.sp,
                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp, top = 4.dp),
                    )
                }
                items(group.transactions, key = { it.id }) { transaction ->
                    TransactionCard(transaction = transaction, imageLoader = imageLoader)
                }
            }
            item { Box(modifier = Modifier.padding(bottom = 8.dp)) }
        }
        Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp).padding(bottom = 16.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Secondary)
                    .clickable { viewModel.perform(TransactionsPreviewViewModel.Action.Confirm) }
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.import_transactions_preview_import, state.totalCount),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun TransactionCard(
    transaction: TransactionsPreviewViewModel.DisplayTransaction,
    imageLoader: ImageLoader,
) {
    val cardModifier = Modifier
        .fillMaxWidth()
        .padding(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 12.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(Color(0xFFFFFFFF))
    val contentModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 14.dp)

    val colorScheme = transaction.colorScheme?.toUi()
    val icon = transaction.icon

    Box(modifier = cardModifier) {
        val isIncome = transaction.type == TransactionsPreviewViewModel.DisplayTransaction.Type.INCOME
        val isTransfer = transaction.type == TransactionsPreviewViewModel.DisplayTransaction.Type.TRANSFER

        TransactionView(
            modifier = contentModifier,
            primaryText = transaction.primaryText,
            primaryAmount = transaction.amount,
            amountColor = if (isIncome) Secondary else Color(0xFF1B1B1F),
            secondaryText = transaction.accountName,
            iconColorScheme = if (isTransfer) TRANSFER_COLOR_SCHEME else colorScheme,
            mainIcon = when {
                isTransfer -> { tint ->
                    Icon(
                        imageVector = Icons.Filled.SwapHoriz,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(24.dp),
                    )
                }
                icon != null -> { tint ->
                    imageLoader.View(
                        image = icon,
                        modifier = Modifier.size(24.dp),
                        tint = if (tint == Color.Unspecified) null else tint,
                    )
                }
                else -> null
            },
        )
    }
}
