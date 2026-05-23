package com.hluhovskyi.zero.budget.over

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.theme.ZeroTheme

internal class BudgetOverViewProvider(
    @Suppress("unused") private val viewModel: BudgetOverViewModel,
    @Suppress("unused") private val imageLoader: ImageLoader,
    @Suppress("unused") private val amountFormatter: AmountFormatter,
) : ViewProvider {

    @Composable
    override fun View() {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .background(ZeroTheme.colors.surface),
        )
    }
}
