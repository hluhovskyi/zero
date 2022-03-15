package com.hluhovskyi.zero.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hluhovskyi.zero.common.ViewProvider

internal class AccountViewProvider(
    private val viewModel: AccountViewModel
) : ViewProvider {

    @Composable
    override fun View() {
        AccountView(
            viewModel = viewModel
        )
    }
}

@Composable
private fun AccountView(
    viewModel: AccountViewModel
) {
    val state by viewModel.state.collectAsState(initial = AccountViewModel.State())
    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(state.accounts) { account ->
            Row {
                Text(
                    text = account.name,
                    modifier = Modifier
                        .weight(1f)
                        // TODO: Handle click
                        .clickable { }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Text(
                    text = account.balance.value.toPlainString()
                )
            }
        }
    }
}