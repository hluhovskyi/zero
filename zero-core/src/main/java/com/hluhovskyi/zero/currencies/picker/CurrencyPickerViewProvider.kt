package com.hluhovskyi.zero.currencies.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.common.Currency
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.theme.OnSurface
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant
import com.hluhovskyi.zero.ui.theme.SurfaceContainer
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLow

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
        CurrencySearchBar(
            query = state.searchQuery,
            onQueryChange = { query ->
                viewModel.perform(CurrencyPickerViewModel.Action.UpdateSearchQuery(query))
            },
        )
        LazyVerticalGrid(
            modifier = Modifier.weight(1f),
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
}

@Composable
private fun CurrencySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .background(SurfaceContainerLow, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        textStyle = TextStyle(
            fontSize = 15.sp,
            color = OnSurface,
        ),
        singleLine = true,
        decorationBox = { innerTextField ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = OnSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Box {
                    if (query.isEmpty()) {
                        Text(
                            text = "Search currencies…",
                            fontSize = 15.sp,
                            color = OnSurfaceVariant,
                        )
                    }
                    innerTextField()
                }
            }
        },
    )
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
