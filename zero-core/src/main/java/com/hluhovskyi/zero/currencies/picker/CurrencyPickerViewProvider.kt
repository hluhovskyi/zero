package com.hluhovskyi.zero.currencies.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
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
import com.hluhovskyi.zero.ui.SearchBar
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

    Column(modifier = Modifier.fillMaxSize()) {
        SearchBar(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            query = state.searchQuery,
            onQueryChange = { query ->
                viewModel.perform(CurrencyPickerViewModel.Action.UpdateSearchQuery(query))
            },
            placeholder = "Search currencies…",
        )
        LazyVerticalGrid(
            modifier = Modifier.weight(1f),
            columns = GridCells.Fixed(GRID_COLUMNS),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            items(state.currencies, key = { it.id.value }) { currency ->
                CurrencyPickerGridItem(
                    currency = currency,
                    isSelected = currency.id == state.selectedCurrencyId,
                    onClick = { viewModel.perform(CurrencyPickerViewModel.Action.SelectCurrency(currency)) },
                )
            }
        }
    }
}

@Composable
private fun CurrencyPickerGridItem(
    currency: Currency,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colors.primary
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Outer box always 56dp; border drawn on outer ring, padding reserves space for it.
        // Background fills the inner 48dp so the icon never shrinks.
        Box(
            modifier = Modifier
                .size(56.dp)
                .then(
                    if (isSelected) {
                        Modifier.border(2.dp, primary, shape).padding(2.dp)
                    } else {
                        Modifier.padding(4.dp)
                    },
                )
                .background(SurfaceContainer, shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = currency.symbol,
                fontSize = 14.sp,
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
