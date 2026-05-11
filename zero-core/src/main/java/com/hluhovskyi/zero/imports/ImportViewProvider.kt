package com.hluhovskyi.zero.imports

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.common.AttachWithView
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.Uri
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.theme.OnSurface
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant
import com.hluhovskyi.zero.ui.theme.PrimaryContainer

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
            state !is ImportViewModel.State.FilePicker &&
            state !is ImportViewModel.State.UpToDate,
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
        ImportViewModel.State.UpToDate -> UpToDateView(
            onDone = { viewModel.perform(ImportViewModel.Action.Back) },
        )
    }
}

@Composable
private fun UpToDateView(onDone: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDone) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.import_back_description),
                    tint = PrimaryContainer,
                )
            }
            Text(
                text = stringResource(R.string.import_nothing_to_import_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryContainer,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Box(modifier = Modifier.padding(end = 48.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.import_up_to_date_message),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnSurface,
            )
            Text(
                text = stringResource(R.string.import_all_synced_message),
                fontSize = 14.sp,
                color = OnSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp).padding(bottom = 16.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(PrimaryContainer)
                    .clickable(onClick = onDone)
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.import_done),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
    }
}
