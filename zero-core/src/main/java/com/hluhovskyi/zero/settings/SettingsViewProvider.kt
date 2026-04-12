package com.hluhovskyi.zero.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.theme.OnSurface
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant
import com.hluhovskyi.zero.ui.theme.SurfaceContainer
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLowest

internal class SettingsViewProvider(
    private val viewModel: SettingsViewModel,
) : ViewProvider {

    @Composable
    override fun View() {
        MoreView(viewModel = viewModel)
    }
}

@Composable
private fun MoreView(viewModel: SettingsViewModel) {
    val state by viewModel.state.collectAsState(initial = SettingsViewModel.State())

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            MoreHeader()
        }

        item {
            MoreSection(title = "PREFERENCES") {
                MoreRow(
                    icon = Icons.Outlined.Payments,
                    primaryText = "Primary Currency",
                    secondaryText = state.selectedCurrencyName.ifEmpty { "Loading…" },
                    onClick = { viewModel.perform(SettingsViewModel.Action.OpenCurrencyPicker) },
                )
            }
        }

        item {
            MoreSection(title = "DATA") {
                MoreRow(
                    icon = Icons.Outlined.MoveToInbox,
                    primaryText = "Import Data",
                    secondaryText = "Migrate history from other apps",
                    onClick = { viewModel.perform(SettingsViewModel.Action.Import) },
                )
                MoreRow(
                    icon = Icons.Outlined.Download,
                    primaryText = "Export Data",
                    secondaryText = "Export your transactions as CSV",
                    onClick = { /* placeholder */ },
                )
            }
        }

        item {
            MoreSection(title = "SECURITY") {
                MoreRow(
                    icon = Icons.Outlined.Fingerprint,
                    primaryText = "Biometric Lock",
                    secondaryText = "Face ID or Fingerprint required on open",
                    onClick = { /* placeholder */ },
                    showChevron = false,
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MoreHeader() {
    Text(
        text = "More",
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
        style = TextStyle(
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = OnSurface,
        ),
    )
}

@Composable
private fun MoreSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp),
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnSurfaceVariant,
                letterSpacing = 0.8.sp,
            ),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceContainerLowest, RoundedCornerShape(16.dp)),
        ) {
            content()
        }
    }
}

@Composable
private fun MoreRow(
    icon: ImageVector,
    primaryText: String,
    secondaryText: String,
    onClick: () -> Unit,
    showChevron: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(SurfaceContainer, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = OnSurfaceVariant,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = primaryText,
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OnSurface,
                ),
            )
            Text(
                text = secondaryText,
                style = TextStyle(
                    fontSize = 12.sp,
                    color = OnSurfaceVariant,
                ),
            )
        }
        if (showChevron) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = OnSurfaceVariant,
            )
        }
    }
}
