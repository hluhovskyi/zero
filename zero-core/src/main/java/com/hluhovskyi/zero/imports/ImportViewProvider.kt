package com.hluhovskyi.zero.imports

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
    private val useCase: ImportUseCase,
    private val sourceSelection: Buildable<out AttachableViewComponent>,
    private val categoriesReview: Buildable<out AttachableViewComponent>,
    private val accountsReview: Buildable<out AttachableViewComponent>,
    private val transactionsPreview: Buildable<out AttachableViewComponent>,
) : ViewProvider {

    @Composable
    override fun View() {
        ImportView(
            useCase = useCase,
            sourceSelection = sourceSelection,
            categoriesReview = categoriesReview,
            accountsReview = accountsReview,
            transactionsPreview = transactionsPreview,
        )
    }
}

@Composable
private fun ImportView(
    useCase: ImportUseCase,
    sourceSelection: Buildable<out AttachableViewComponent>,
    categoriesReview: Buildable<out AttachableViewComponent>,
    accountsReview: Buildable<out AttachableViewComponent>,
    transactionsPreview: Buildable<out AttachableViewComponent>,
) {
    val state by useCase.state.collectAsState(
        initial = ImportUseCase.State.SourceSelection(emptyList()),
    )

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { androidUri ->
        if (androidUri != null) {
            val uri = Uri(androidUri.toString())
            if (uri is Uri.NonEmpty) {
                useCase.perform(ImportUseCase.Action.SelectFile(uri))
            }
        } else {
            useCase.perform(ImportUseCase.Action.Back)
        }
    }

    when (state) {
        is ImportUseCase.State.SourceSelection -> sourceSelection.AttachWithView()
        is ImportUseCase.State.FilePicker -> {
            LaunchedEffect(state) {
                fileLauncher.launch(arrayOf("*/*"))
            }
        }
        is ImportUseCase.State.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is ImportUseCase.State.CategoriesReview -> categoriesReview.AttachWithView()
        is ImportUseCase.State.AccountsReview -> accountsReview.AttachWithView()
        is ImportUseCase.State.TransactionsPreview -> transactionsPreview.AttachWithView()
    }
}
