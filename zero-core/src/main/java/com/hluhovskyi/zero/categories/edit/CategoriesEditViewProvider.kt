package com.hluhovskyi.zero.categories.edit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.common.ViewProvider

internal class CategoriesEditViewProvider(
    private val viewModel: CategoryEditViewModel,
    private val imageLoader: ImageLoader,
) : ViewProvider {

    @Composable
    override fun View() {
        CategoryEditView(
            viewModel = viewModel,
            imageLoader = imageLoader
        )
    }
}

@Composable
private fun CategoryEditView(
    viewModel: CategoryEditViewModel,
    imageLoader: ImageLoader
) {
    Column {
        val state by viewModel.state.collectAsState(initial = CategoryEditViewModel.State())

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.name,
            onValueChange = { name ->
                viewModel.perform(CategoryEditViewModel.Action.ChangeName(name))
            }
        )
        imageLoader.View(image = state.icon)
        Button(
            onClick = { viewModel.perform(CategoryEditViewModel.Action.SelectIcon) }
        ) {
            Text(text = "Select icon")
        }
    }
}