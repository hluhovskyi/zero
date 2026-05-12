package com.hluhovskyi.zero.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.common.Uri
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.theme.OnSurface
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant
import com.hluhovskyi.zero.ui.theme.SurfaceContainer
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLowest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val snackbarHostState = remember { SnackbarHostState() }
    val backupSaved = stringResource(R.string.settings_backup_saved)
    val exportFailedTemplate = stringResource(R.string.settings_export_failed)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { androidUri ->
        if (androidUri != null) {
            val uri = Uri(androidUri.toString())
            if (uri is Uri.NonEmpty) {
                viewModel.perform(SettingsViewModel.Action.Export(uri))
            }
        }
    }

    LaunchedEffect(state.exportFeedback) {
        when (val feedback = state.exportFeedback) {
            SettingsViewModel.ExportFeedback.Success ->
                snackbarHostState.showSnackbar(backupSaved)
            is SettingsViewModel.ExportFeedback.Error ->
                snackbarHostState.showSnackbar(String.format(exportFailedTemplate, feedback.message))
            null -> Unit
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
        ) {
            item { MoreHeader() }
            item {
                MoreSection(title = stringResource(R.string.settings_section_preferences).uppercase()) {
                    MoreRow(
                        icon = Icons.Outlined.Payments,
                        primaryText = stringResource(R.string.settings_primary_currency),
                        secondaryText = state.selectedCurrencyName.ifEmpty { stringResource(R.string.settings_currency_loading) },
                        onClick = { viewModel.perform(SettingsViewModel.Action.OpenCurrencyPicker) },
                    )
                }
            }
            item {
                MoreSection(title = stringResource(R.string.settings_section_data).uppercase()) {
                    MoreRow(
                        icon = Icons.Outlined.MoveToInbox,
                        primaryText = stringResource(R.string.settings_import_data),
                        secondaryText = stringResource(R.string.settings_import_data_description),
                        onClick = { viewModel.perform(SettingsViewModel.Action.Import) },
                    )
                    MoreRow(
                        icon = Icons.Outlined.Download,
                        primaryText = stringResource(R.string.settings_export_data),
                        secondaryText = stringResource(R.string.settings_export_data_description),
                        onClick = {
                            val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                            exportLauncher.launch("zero-backup-$date.json")
                        },
                    )
                }
            }
            item {
                MoreSection(title = stringResource(R.string.settings_section_security).uppercase()) {
                    MoreRow(
                        icon = Icons.Outlined.Fingerprint,
                        primaryText = stringResource(R.string.settings_biometric_lock),
                        secondaryText = stringResource(R.string.settings_biometric_lock_description),
                        onClick = { /* placeholder */ },
                        showChevron = false,
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun MoreHeader() {
    Text(
        text = stringResource(R.string.settings_title),
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
