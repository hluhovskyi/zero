package com.hluhovskyi.zero.transactions.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.common.toCompose

internal class TransactionPreviewViewProvider(
    private val viewModel: TransactionPreviewViewModel,
    private val imageLoader: ImageLoader,
    private val amountFormatter: AmountFormatter,
) : ViewProvider {

    @Composable
    override fun View() {
        TransactionPreviewView(
            viewModel = viewModel,
            imageLoader = imageLoader,
            amountFormatter = amountFormatter,
        )
    }
}

@Composable
private fun TransactionPreviewView(
    viewModel: TransactionPreviewViewModel,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
) {
    val state by viewModel.state.collectAsState(initial = TransactionPreviewViewModel.State())
    Column {
        Box(
            modifier = Modifier
                .size(72.dp)
                .align(Alignment.CenterHorizontally)
                .background(
                    color = state.categoryColor.toCompose(),
                    shape = CircleShape
                )
        ) {
            imageLoader.View(
                image = state.categoryIcon
            )
        }
    }
}