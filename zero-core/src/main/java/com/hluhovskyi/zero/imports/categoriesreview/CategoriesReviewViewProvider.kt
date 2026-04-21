package com.hluhovskyi.zero.imports.categoriesreview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.imports.ImportCategory
import com.hluhovskyi.zero.ui.CategoryIconView
import com.hluhovskyi.zero.ui.common.toUi

internal class CategoriesReviewViewProvider(
    private val viewModel: CategoriesReviewViewModel,
    private val imageLoader: ImageLoader,
) : ViewProvider {

    @Composable
    override fun View() {
        CategoriesReviewView(viewModel = viewModel, imageLoader = imageLoader)
    }
}

@Composable
private fun CategoriesReviewView(viewModel: CategoriesReviewViewModel, imageLoader: ImageLoader) {
    val state by viewModel.state.collectAsState(initial = CategoriesReviewViewModel.State())

    Column(modifier = Modifier.fillMaxSize()) {
        IconButton(onClick = { viewModel.perform(CategoriesReviewViewModel.Action.Back) }) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text(
            text = "Categories",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Text(
            text = "MANAGEMENT",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(state.categories, key = { it.id.value }) { category ->
                CategoryGridItem(
                    category = category,
                    isSelected = category.id !in state.excludedIds,
                    imageLoader = imageLoader,
                    onClick = { viewModel.perform(CategoriesReviewViewModel.Action.ToggleCategory(category.id)) },
                )
            }
        }
        Button(
            onClick = { viewModel.perform(CategoriesReviewViewModel.Action.Next) },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Text(text = "Next →")
        }
    }
}

@Composable
private fun CategoryGridItem(
    category: ImportCategory,
    isSelected: Boolean,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CategoryIconView(
            colorScheme = category.colorScheme.toUi(),
            isSelected = isSelected,
        ) { iconTint ->
            imageLoader.View(
                image = category.icon,
                modifier = Modifier.fillMaxSize(),
                tint = if (iconTint == Color.Unspecified) null else iconTint,
            )
        }
        Text(
            text = category.name,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${category.transactionCount}",
            fontSize = 10.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
        )
    }
}
