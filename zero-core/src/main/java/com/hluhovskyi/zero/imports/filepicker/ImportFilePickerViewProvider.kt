package com.hluhovskyi.zero.imports.filepicker

import androidx.compose.foundation.layout.Column
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.hluhovskyi.zero.common.ViewProvider

internal class ImportFilePickerViewProvider(
    private val viewModel: ImportFilePickerViewModel,
) : ViewProvider {

    @Composable
    override fun View() {
        ImportFilePickerView(
            viewModel = viewModel
        )
    }
}

@Composable
private fun ImportFilePickerView(
    viewModel: ImportFilePickerViewModel,
) {
    val state by viewModel.state.collectAsState(initial = ImportFilePickerViewModel.State())
    Column {
        Text(text = "Select a file to import data from")
        if (state.fileName.isBlank()) {
            Button(onClick = {
                viewModel.perform(ImportFilePickerViewModel.Action.Pick)
            }) {
                Text(text = "Select file")
            }
        } else {
            Button(onClick = {
                viewModel.perform(ImportFilePickerViewModel.Action.Submit)
            }) {
                Text(text = "Submit ${state.fileName}")
            }
        }
    }
}