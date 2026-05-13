package com.hluhovskyi.zero.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.hluhovskyi.zero.common.AttachWithView
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.transactions.TransactionComponent
import com.hluhovskyi.zero.welcome.WelcomeComponent

internal class HomeViewProvider(
    private val viewModel: HomeViewModel,
    private val welcomeComponent: WelcomeComponent,
    private val transactionComponent: TransactionComponent,
) : ViewProvider {

    @Composable
    override fun View() {
        val state by viewModel.state.collectAsState(initial = HomeViewModel.State())

        Box(modifier = Modifier.fillMaxSize()) {
            if (state.isNewUser) {
                welcomeComponent.AttachWithView()
            } else {
                transactionComponent.AttachWithView()
            }
        }
    }
}
