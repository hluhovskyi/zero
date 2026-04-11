package com.hluhovskyi.zero.currencies.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.common.Currency
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.theme.OnSurface
import com.hluhovskyi.zero.ui.theme.SurfaceContainer

private const val GRID_COLUMNS = 4

internal class CurrencyPickerViewProvider(
    private val viewModel: CurrencyPickerViewModel,
) : ViewProvider {

    @Composable
    override fun View() {
        CurrencyPickerView(viewModel = viewModel)
    }
}

@Composable
private fun CurrencyPickerView(viewModel: CurrencyPickerViewModel) {
    val state by viewModel.state.collectAsState(initial = CurrencyPickerViewModel.State())

    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(state.currencies, key = { it.id.value }) { currency ->
            CurrencyPickerGridItem(
                currency = currency,
                onClick = { viewModel.perform(CurrencyPickerViewModel.Action.SelectCurrency(currency)) },
            )
        }
    }
}

@Composable
private fun CurrencyPickerGridItem(
    currency: Currency,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(SurfaceContainer, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = currency.symbol,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurface,
            )
        }
        Text(
            modifier = Modifier.padding(top = 8.dp),
            text = currency.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
