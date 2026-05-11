package com.hluhovskyi.zero.imports.sourceselection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.imports.KnownSource
import com.hluhovskyi.zero.imports.Source
import com.hluhovskyi.zero.ui.ImportErrorBanner
import com.hluhovskyi.zero.ui.ModalHeader
import com.hluhovskyi.zero.ui.theme.OnSurface
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant
import com.hluhovskyi.zero.ui.theme.Outline
import com.hluhovskyi.zero.ui.theme.OutlineVariant
import com.hluhovskyi.zero.ui.theme.PrimaryContainer
import com.hluhovskyi.zero.ui.theme.Surface
import com.hluhovskyi.zero.ui.theme.SurfaceContainer
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLow
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLowest

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface),
    ) {
        ModalHeader(
            title = stringResource(R.string.import_source_selection_title),
            onClose = { viewModel.perform(SourceSelectionViewModel.Action.Close) },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val error = state.error
            if (error != null) {
                item(key = "error_banner") {
                    ImportErrorBanner(
                        message = error,
                        onRetry = { viewModel.perform(SourceSelectionViewModel.Action.Retry) },
                        onDismiss = { viewModel.perform(SourceSelectionViewModel.Action.DismissError) },
                    )
                }
            }
            item(key = "subtitle") {
                Text(
                    text = stringResource(R.string.import_source_selection_subtitle),
                    fontSize = 14.sp,
                    color = OnSurfaceVariant,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            items(state.sources, key = { it.key }) { source ->
                SourceCard(
                    source = source,
                    onClick = { viewModel.perform(SourceSelectionViewModel.Action.SelectSource(source)) },
                )
            }
            item(key = "hint") {
                MoreSourcesHint()
            }
        }
    }
}

private data class SourceCardConfig(
    val icon: ImageVector,
    val iconBg: Color,
    val iconTint: Color,
    val title: String,
    val description: String,
)

@Composable
private fun sourceCardConfig(source: Source): SourceCardConfig? = when (source.key) {
    KnownSource.ZeroBackup.key -> SourceCardConfig(
        icon = Icons.Filled.Backup,
        iconBg = Color(0xFFE8EEFF),
        iconTint = PrimaryContainer,
        title = stringResource(R.string.import_source_zero_backup_title),
        description = stringResource(R.string.import_source_zero_backup_description),
    )
    KnownSource.ZenMoney.key -> SourceCardConfig(
        icon = Icons.Filled.Description,
        iconBg = Color(0xFFE8F5E9),
        iconTint = Color(0xFF1B5E20),
        title = stringResource(R.string.import_source_zenmoney_title),
        description = stringResource(R.string.import_source_zenmoney_description),
    )
    else -> null
}

@Composable
private fun SourceCard(source: Source, onClick: () -> Unit) {
    val config = sourceCardConfig(source) ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceContainerLowest)
            .clickable(onClick = onClick, onClickLabel = config.title)
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(config.iconBg, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = config.icon,
                contentDescription = null,
                tint = config.iconTint,
                modifier = Modifier.size(28.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = config.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnSurface,
            )
            Text(
                text = config.description,
                fontSize = 13.sp,
                color = OnSurfaceVariant,
                lineHeight = 18.sp,
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = OutlineVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun MoreSourcesHint() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceContainerLow, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(SurfaceContainer, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = Outline,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = stringResource(R.string.import_source_more_coming_soon),
            fontSize = 13.sp,
            color = OnSurfaceVariant,
        )
    }
}
