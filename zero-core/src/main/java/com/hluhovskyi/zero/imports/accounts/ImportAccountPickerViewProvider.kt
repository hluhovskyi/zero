package com.hluhovskyi.zero.imports.accounts

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

internal class ImportAccountPickerViewProvider(
    private val viewModel: ImportAccountPickerViewModel,
) : ViewProvider {

    @Composable
    override fun View() {
        ImportAccountPickerView(
            viewModel = viewModel,
        )
    }
}

@Composable
private fun ImportAccountPickerView(
    viewModel: ImportAccountPickerViewModel,
) {
    val state by viewModel.state.collectAsState(initial = ImportAccountPickerViewModel.State())
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(items = state.items, key = { it.id.value }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.perform(ImportAccountPickerViewModel.Action.ChangeSelection(item)) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = item.selected,
                        onCheckedChange = {
                            viewModel.perform(ImportAccountPickerViewModel.Action.ChangeSelection(item))
                        },
                    )
                    Text(text = item.name)
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.End,
        ) {
            Button(onClick = { viewModel.perform(ImportAccountPickerViewModel.Action.Submit) }) {
                Text(text = "Next")
            }
        }
    }
}
