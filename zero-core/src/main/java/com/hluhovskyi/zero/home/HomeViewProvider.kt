package com.hluhovskyi.zero.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.common.AttachWithView
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.transactions.TransactionComponent
import com.hluhovskyi.zero.ui.theme.Primary
import com.hluhovskyi.zero.welcome.WelcomeComponent

internal class HomeViewProvider(
    private val viewModel: HomeViewModel,
    private val welcomeComponent: WelcomeComponent,
    private val transactionComponent: TransactionComponent,
) : ViewProvider {

    @Composable
    override fun View() {
        val state by viewModel.state.collectAsState(initial = HomeViewModel.State())

        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
                text = stringResource(R.string.home_title),
                style = TextStyle(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Primary,
                ),
            )
            Box(modifier = Modifier.fillMaxSize()) {
                if (state.isNewUser) {
                    welcomeComponent.AttachWithView()
                } else {
                    transactionComponent.AttachWithView()
                }
            }
        }
    }
}
