package com.hluhovskyi.zero.imports

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.hluhovskyi.zero.common.AttachWithView
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.Uri
import com.hluhovskyi.zero.common.ViewProvider

internal class ImportViewProvider(
    private val viewModel: ImportViewModel,
    private val sourceSelection: Buildable<out AttachableViewComponent>,
    private val categoriesReview: Buildable<out AttachableViewComponent>,
    private val accountsReview: Buildable<out AttachableViewComponent>,
    private val transactionsPreview: Buildable<out AttachableViewComponent>,
) : ViewProvider {

    @Composable
    override fun View() {
        ImportView(
            viewModel = viewModel,
            sourceSelection = sourceSelection,
            categoriesReview = categoriesReview,
            accountsReview = accountsReview,
            transactionsPreview = transactionsPreview,
        )
    }
}

@Composable
private fun ImportView(
    viewModel: ImportViewModel,
    sourceSelection: Buildable<out AttachableViewComponent>,
    categoriesReview: Buildable<out AttachableViewComponent>,
    accountsReview: Buildable<out AttachableViewComponent>,
    transactionsPreview: Buildable<out AttachableViewComponent>,
) {
    val state by viewModel.state.collectAsState(
        initial = ImportViewModel.State.SourceSelection,
    )

    BackHandler(
        enabled = state !is ImportViewModel.State.SourceSelection &&
            state !is ImportViewModel.State.FilePicker,
    ) {
        viewModel.perform(ImportViewModel.Action.Back)
    }

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { androidUri ->
        if (androidUri != null) {
            val uri = Uri(androidUri.toString())
            if (uri is Uri.NonEmpty) {
                viewModel.perform(ImportViewModel.Action.SelectFile(uri))
            }
        } else {
            viewModel.perform(ImportViewModel.Action.Back)
        }
    }

    when (state) {
        ImportViewModel.State.SourceSelection -> sourceSelection.AttachWithView()
        ImportViewModel.State.FilePicker -> {
            LaunchedEffect(state) {
                fileLauncher.launch(arrayOf("*/*"))
            }
        }
        ImportViewModel.State.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        ImportViewModel.State.CategoriesReview -> categoriesReview.AttachWithView()
        ImportViewModel.State.AccountsReview -> accountsReview.AttachWithView()
        ImportViewModel.State.TransactionsPreview -> transactionsPreview.AttachWithView()
    }
}
