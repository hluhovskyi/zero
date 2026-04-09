package com.hluhovskyi.zero.imports

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.hluhovskyi.zero.common.AttachWithView
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.ViewProvider

internal class ImportViewProvider(
    private val viewModel: ImportViewModel,
    private val filePicker: Buildable<out AttachableViewComponent>,
    private val accountPicker: Buildable<out AttachableViewComponent>,
    private val categoriesPicker: Buildable<out AttachableViewComponent>,
    private val transactionsPreview: Buildable<out AttachableViewComponent>,
) : ViewProvider {

    @Composable
    override fun View() {
        ImportView(
            viewModel = viewModel,
            filePicker = filePicker,
            accountPicker = accountPicker,
            categoriesPicker = categoriesPicker,
            transactionsPreview = transactionsPreview,
        )
    }
}

@Composable
private fun ImportView(
    viewModel: ImportViewModel,
    filePicker: Buildable<out AttachableViewComponent>,
    accountPicker: Buildable<out AttachableViewComponent>,
    categoriesPicker: Buildable<out AttachableViewComponent>,
    transactionsPreview: Buildable<out AttachableViewComponent>,
) {
    val state by viewModel.state.collectAsState(initial = ImportViewModel.State())
    when (state.step) {
        ImportViewModel.Step.FilePicker -> {
            filePicker.AttachWithView()
        }
        ImportViewModel.Step.AccountsPicker -> {
            accountPicker.AttachWithView()
        }
        ImportViewModel.Step.CategoriesPicker -> {
            categoriesPicker.AttachWithView()
        }
        ImportViewModel.Step.TransactionsPreview -> {
            transactionsPreview.AttachWithView()
        }
    }
}
