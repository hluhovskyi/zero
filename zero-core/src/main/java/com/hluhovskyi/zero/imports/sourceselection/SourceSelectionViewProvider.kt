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
import com.hluhovskyi.zero.imports.KnownSource
import com.hluhovskyi.zero.imports.Source
import com.hluhovskyi.zero.ui.theme.OnSurface
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant

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
            Icon(imageVector = Icons.Filled.Close, contentDescription = "Close", tint = OnSurface)
        }
        Text(
            text = "Import Data",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = OnSurface,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Text(
            text = "Choose how you'd like to import your financial data.",
            fontSize = 14.sp,
            color = OnSurfaceVariant,
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
    val (label, description) = when (source.key) {
        KnownSource.ZeroBackup.key -> "Zero Backup" to "Restore from a Zero backup file"
        KnownSource.ZenMoney.key -> "ZenMoney" to "Import from a ZenMoney CSV export"
        else -> source.key to ""
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = OnSurface,
        )
        if (description.isNotEmpty()) {
            Text(
                text = description,
                fontSize = 13.sp,
                color = OnSurfaceVariant,
            )
        }
    }
}
