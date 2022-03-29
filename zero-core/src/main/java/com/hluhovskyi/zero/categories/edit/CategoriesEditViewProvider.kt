package com.hluhovskyi.zero.categories.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Button
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.common.toCompose

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
    Column(
        modifier = Modifier.padding(16.dp),
    ) {
        val state by viewModel.state.collectAsState(initial = CategoryEditViewModel.State())

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            imageLoader.View(
                image = state.icon,
                modifier = Modifier
                    .size(64.dp)
                    .background(color = state.color.toCompose(), shape = CircleShape)
                    .padding(12.dp)
            )
        }

        Row(
            modifier = Modifier.padding(top = 24.dp)
        ) {
            val modifier = Modifier
                .weight(1f)
                .sizeIn(minHeight = 48.dp)
            OutlinedButton(
                modifier = modifier,
                onClick = { viewModel.perform(CategoryEditViewModel.Action.SelectIcon) }
            ) {
                Text(text = "Select icon")
            }
            Spacer(modifier = Modifier.sizeIn(minWidth = 16.dp))
            OutlinedButton(
                modifier = modifier,
                onClick = { viewModel.perform(CategoryEditViewModel.Action.SelectColor) }
            ) {
                Text(text = "Select color")
            }
        }

        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            value = state.name,
            onValueChange = { name ->
                viewModel.perform(CategoryEditViewModel.Action.ChangeName(name))
            }
        )

        Button(
            modifier = Modifier
                .padding(top = 16.dp)
                .sizeIn(minHeight = 48.dp)
                .fillMaxWidth(),
            onClick = { viewModel.perform(CategoryEditViewModel.Action.Save) }
        ) {
            Text(text = "Save category")
        }
    }
}