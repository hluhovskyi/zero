// zero-core/src/main/java/com/hluhovskyi/zero/imports/sourceselection/SourceSelectionViewProvider.kt
package com.hluhovskyi.zero.imports.sourceselection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.imports.Source

internal class SourceSelectionViewProvider(
    private val viewModel: SourceSelectionViewModel,
) : ViewProvider {

    @Composable
    override fun View() {
        SourceSelectionView(viewModel = viewModel)
    }
}

@Composable
private fun SourceSelectionView(viewModel: SourceSelectionViewModel) {
    val state by viewModel.state.collectAsState(initial = SourceSelectionViewModel.State())

    Column(modifier = Modifier.fillMaxSize()) {
        IconButton(onClick = { viewModel.perform(SourceSelectionViewModel.Action.Close) }) {
            Icon(imageVector = Icons.Filled.Close, contentDescription = "Close")
        }
        Text(
            text = "Import Data",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Text(
            text = "Choose how you'd like to import your financial data.",
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.sources) { source ->
                SourceRow(
                    source = source,
                    onClick = { viewModel.perform(SourceSelectionViewModel.Action.SelectSource(source)) },
                )
            }
        }
    }
}

@Composable
private fun SourceRow(source: Source, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = source.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(text = source.description, fontSize = 13.sp)
    }
}
