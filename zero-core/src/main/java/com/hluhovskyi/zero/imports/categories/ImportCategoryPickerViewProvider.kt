package com.hluhovskyi.zero.imports.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.hluhovskyi.zero.common.ViewProvider

internal class ImportCategoryPickerViewProvider(
    private val viewModel: ImportCategoryPickerViewModel,
) : ViewProvider {

    @Composable
    override fun View() {
        ImportCategoryPickerView(
            viewModel = viewModel,
        )
    }
}

@Composable
private fun ImportCategoryPickerView(
    viewModel: ImportCategoryPickerViewModel,
) {
    val state by viewModel.state.collectAsState(initial = ImportCategoryPickerViewModel.State())
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(items = state.items, key = { it.id.value }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.perform(ImportCategoryPickerViewModel.Action.ChangeSelection(item)) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = item.selected,
                        onCheckedChange = {
                            viewModel.perform(ImportCategoryPickerViewModel.Action.ChangeSelection(item))
                        },
                    )
                    Text(text = item.name)
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.End,
        ) {
            Button(onClick = { viewModel.perform(ImportCategoryPickerViewModel.Action.Submit) }) {
                Text(text = "Next")
            }
        }
    }
}
