package com.hluhovskyi.zero.accounts.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.hluhovskyi.zero.common.ViewProvider

internal class AccountEditViewProvider(
    private val viewModel: AccountEditViewModel
): ViewProvider {

    @Composable
    override fun View() {
        AccountEditView(
            viewModel = viewModel
        )
    }
}

@Composable
private fun AccountEditView(
    viewModel: AccountEditViewModel
) {
    val state = viewModel.state.collectAsState(initial = AccountEditViewModel.State())
}