package com.hluhovskyi.zero.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.CategoryIconView
import com.hluhovskyi.zero.ui.common.toUi

internal class CategoryViewProvider(
    private val viewModel: CategoryViewModel,
    private val imageLoader: ImageLoader
) : ViewProvider {

    @Composable
    override fun View() {
        CategoryView(
            viewModel = viewModel,
            imageLoader = imageLoader
        )
    }
}

@Composable
private fun CategoryView(
    viewModel: CategoryViewModel,
    imageLoader: ImageLoader,
) {
    val state by viewModel.state.collectAsState(initial = CategoryViewModel.State())
    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(state.categories) { category ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.perform(CategoryViewModel.Action.SelectCategory(category)) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryIconView(colorScheme = category.colorScheme.toUi()) { tint ->
                    imageLoader.View(
                        image = category.icon,
                        modifier = Modifier
                            .sizeIn(maxHeight = 24.dp, maxWidth = 24.dp)
                            .aspectRatio(1f),
                        scale = ImageLoader.Scale.Crop,
                        tint = tint,
                    )
                }
                Text(
                    text = category.name,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
    }
}