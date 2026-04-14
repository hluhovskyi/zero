// zero-core/src/main/java/com/hluhovskyi/zero/imports/accountsreview/AccountsReviewViewProvider.kt
package com.hluhovskyi.zero.imports.accountsreview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.imports.ImportAccount

internal class AccountsReviewViewProvider(
    private val viewModel: AccountsReviewViewModel,
) : ViewProvider {

    @Composable
    override fun View() {
        AccountsReviewView(viewModel = viewModel)
    }
}

@Composable
private fun AccountsReviewView(viewModel: AccountsReviewViewModel) {
    val state by viewModel.state.collectAsState(initial = AccountsReviewViewModel.State())

    Column(modifier = Modifier.fillMaxSize()) {
        IconButton(onClick = { viewModel.perform(AccountsReviewViewModel.Action.Back) }) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text(
            text = "Review Accounts",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Text(
            text = "STEP 3 OF 4",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.accounts, key = { it.id.value }) { account ->
                AccountRow(account = account)
            }
        }
        Button(
            onClick = { viewModel.perform(AccountsReviewViewModel.Action.Next) },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Text(text = "Next: Review Transactions →")
        }
    }
}

@Composable
private fun AccountRow(account: ImportAccount) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = account.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = "${account.currencyId.value} • ${account.transactionCount} transactions",
                fontSize = 13.sp,
            )
        }
    }
}
