package com.hluhovskyi.zero.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
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

internal class AccountViewProvider(
    private val viewModel: AccountViewModel,
    private val imageLoader: ImageLoader,
    private val amountFormatter: AmountFormatter,
) : ViewProvider {

    @Composable
    override fun View() {
        AccountView(
            viewModel = viewModel,
            imageLoader = imageLoader,
            amountFormatter = amountFormatter,
        )
    }
}

@Composable
private fun AccountView(
    viewModel: AccountViewModel,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
) {
    val state by viewModel.state.collectAsState(initial = AccountViewModel.State())
    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(state.accounts) { account ->
            Row(
                modifier = Modifier
                    .clickable { }
                    .padding(vertical = 8.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                imageLoader.View(
                    modifier = Modifier.size(32.dp),
                    image = account.icon,
                )
                Text(
                    text = account.name,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                )
                Text(
                    text = amountFormatter.format(
                        amount = account.balance,
                        currencySymbol = account.currencySymbol,
                    ),
                )
            }
        }
    }
}
