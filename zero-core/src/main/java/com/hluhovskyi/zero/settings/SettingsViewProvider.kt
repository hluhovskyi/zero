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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import com.hluhovskyi.zero.backup.BackupUseCase
import com.hluhovskyi.zero.backup.rememberBackupRelativeTime
import com.hluhovskyi.zero.common.Uri
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.theme.ZeroTheme
import kotlinx.datetime.LocalDateTime
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

    val biometricUnavailable = stringResource(R.string.settings_biometric_unavailable)
    val biometricAuthFailed = stringResource(R.string.settings_biometric_auth_failed)

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

    LaunchedEffect(state.biometricFeedback) {
        when (state.biometricFeedback) {
            SettingsViewModel.BiometricFeedback.Unavailable -> {
                snackbarHostState.showSnackbar(biometricUnavailable)
                viewModel.perform(SettingsViewModel.Action.BiometricFeedbackShown)
            }
            SettingsViewModel.BiometricFeedback.AuthFailed -> {
                snackbarHostState.showSnackbar(biometricAuthFailed)
                viewModel.perform(SettingsViewModel.Action.BiometricFeedbackShown)
            }
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
                val backup = state.backup
                MoreSection(title = stringResource(R.string.settings_section_backup).uppercase()) {
                    MoreRow(
                        icon = Icons.Outlined.CloudUpload,
                        primaryText = stringResource(R.string.settings_backup_row_title),
                        secondaryText = backupRowSubtitle(backup),
                        secondaryColor = backupRowSubtitleColor(backup),
                        onClick = { viewModel.perform(SettingsViewModel.Action.OpenBackup) },
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
                    MoreToggleRow(
                        icon = Icons.Outlined.Fingerprint,
                        primaryText = stringResource(R.string.settings_biometric_lock),
                        secondaryText = if (state.biometricLockEnabled) {
                            stringResource(R.string.settings_biometric_lock_description_enabled)
                        } else {
                            stringResource(R.string.settings_biometric_lock_description_disabled)
                        },
                        checked = state.biometricLockEnabled,
                        onToggle = { viewModel.perform(SettingsViewModel.Action.ToggleBiometricLock) },
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
            color = ZeroTheme.colors.onSurface,
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
                color = ZeroTheme.colors.onSurfaceVariant,
                letterSpacing = 0.8.sp,
            ),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ZeroTheme.colors.surfaceContainerLowest, RoundedCornerShape(16.dp)),
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
    secondaryColor: androidx.compose.ui.graphics.Color? = null,
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
                .background(ZeroTheme.colors.surfaceContainer, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = ZeroTheme.colors.onSurfaceVariant,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = primaryText,
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ZeroTheme.colors.onSurface,
                ),
            )
            Text(
                text = secondaryText,
                style = TextStyle(
                    fontSize = 12.sp,
                    color = secondaryColor ?: ZeroTheme.colors.onSurfaceVariant,
                ),
            )
        }
        if (showChevron) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = ZeroTheme.colors.onSurfaceVariant,
            )
        }
    }
}

/**
 * Maps the projected backup summary to the row's secondary text. Per
 * `feedback_viewmodel_no_derivation` this lives in the composable, not the ViewModel —
 * the ViewModel passes the raw passthrough state and the row picks the right copy.
 */
@Composable
private fun backupRowSubtitle(backup: SettingsViewModel.BackupSummary): String = when {
    !backup.isSignedIn -> stringResource(R.string.settings_backup_row_off)
    backup.phase is BackupUseCase.Phase.Uploading -> stringResource(R.string.settings_backup_row_uploading)
    backup.phase is BackupUseCase.Phase.Restoring -> stringResource(R.string.settings_backup_row_restoring)
    backup.phase is BackupUseCase.Phase.Failed && backup.consecutiveFailures > 0 ->
        stringResource(R.string.settings_backup_row_failed)
    backup.lastSuccessAt != null ->
        stringResource(R.string.settings_backup_row_last_at, settingsBackupRelativeTime(backup.lastSuccessAt))
    else -> stringResource(R.string.settings_backup_row_on)
}

@Composable
private fun backupRowSubtitleColor(backup: SettingsViewModel.BackupSummary): androidx.compose.ui.graphics.Color = when {
    !backup.isSignedIn -> ZeroTheme.colors.outline
    backup.phase is BackupUseCase.Phase.Failed && backup.consecutiveFailures > 0 -> ZeroTheme.colors.error
    backup.phase is BackupUseCase.Phase.Uploading || backup.phase is BackupUseCase.Phase.Restoring ->
        ZeroTheme.colors.primaryContainer
    else -> ZeroTheme.colors.onSurfaceVariant
}

@Composable
private fun settingsBackupRelativeTime(at: LocalDateTime): String = rememberBackupRelativeTime(at)

@Composable
private fun MoreToggleRow(
    icon: ImageVector,
    primaryText: String,
    secondaryText: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(ZeroTheme.colors.surfaceContainer, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = ZeroTheme.colors.onSurfaceVariant,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = primaryText,
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ZeroTheme.colors.onSurface,
                ),
            )
            Text(
                text = secondaryText,
                style = TextStyle(
                    fontSize = 12.sp,
                    color = ZeroTheme.colors.onSurfaceVariant,
                ),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = ZeroTheme.colors.primaryContainer,
            ),
        )
    }
}
