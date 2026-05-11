package com.hluhovskyi.zero.categories.picker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.CategoryIconView
import com.hluhovskyi.zero.ui.SearchBar
import com.hluhovskyi.zero.ui.common.toUi
import com.hluhovskyi.zero.ui.theme.OnSurface

private const val GRID_COLUMNS = 4

internal class CategoryPickerViewProvider(
    private val viewModel: CategoryPickerViewModel,
    private val imageLoader: ImageLoader,
) : ViewProvider {

    @Composable
    override fun View() {
        CategoryPickerView(
            viewModel = viewModel,
            imageLoader = imageLoader,
        )
    }
}

@Composable
private fun CategoryPickerView(
    viewModel: CategoryPickerViewModel,
    imageLoader: ImageLoader,
) {
    val state by viewModel.state.collectAsState(initial = CategoryPickerViewModel.State())

    Column(modifier = Modifier.fillMaxSize()) {
        SearchBar(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            query = state.searchQuery,
            onQueryChange = { query ->
                viewModel.perform(CategoryPickerViewModel.Action.UpdateSearchQuery(query))
            },
            placeholder = stringResource(R.string.category_picker_search_placeholder),
        )
        LazyVerticalGrid(
            modifier = Modifier.weight(1f),
            columns = GridCells.Fixed(GRID_COLUMNS),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            items(state.categories) { category ->
                CategoryPickerGridItem(
                    imageLoader = imageLoader,
                    category = category,
                    isSelected = category.id == state.selectedCategoryId,
                    onClick = { viewModel.perform(CategoryPickerViewModel.Action.SelectCategory(category)) },
                )
            }
        }
    }
}

@Composable
private fun CategoryPickerGridItem(
    modifier: Modifier = Modifier,
    imageLoader: ImageLoader,
    category: CategoryPickerViewModel.CategoryPickerItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CategoryIconView(
            colorScheme = category.colorScheme.toUi(),
            size = 48.dp,
            contentPadding = 12.dp,
            isSelected = isSelected,
        ) { iconTint ->
            imageLoader.View(
                modifier = Modifier.sizeIn(maxHeight = 24.dp, maxWidth = 24.dp),
                image = category.icon,
                tint = iconTint,
            )
        }
        Text(
            modifier = Modifier.padding(top = 8.dp),
            text = category.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
