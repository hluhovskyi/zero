package com.hluhovskyi.zero.backup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restore
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.theme.ZeroTheme
import kotlinx.datetime.LocalDateTime

internal class BackupDetailViewProvider(
    private val viewModel: BackupDetailViewModel,
) : ViewProvider {

    @Composable
    override fun View() {
        BackupDetailScreen(viewModel = viewModel)
    }
}

@Composable
private fun BackupDetailScreen(viewModel: BackupDetailViewModel) {
    val state by viewModel.state.collectAsState(initial = BackupDetailViewModel.State())
    val snackbarHostState = remember { SnackbarHostState() }

    val cancelledMessage = stringResource(R.string.backup_sign_in_cancelled)
    val failedMessage = stringResource(R.string.backup_sign_in_failed)

    LaunchedEffect(state.signInFeedback) {
        when (state.signInFeedback) {
            BackupDetailViewModel.SignInFeedback.Cancelled -> {
                snackbarHostState.showSnackbar(cancelledMessage)
                viewModel.perform(BackupDetailViewModel.Action.SignInFeedbackShown)
            }
            is BackupDetailViewModel.SignInFeedback.Failed -> {
                snackbarHostState.showSnackbar(failedMessage)
                viewModel.perform(BackupDetailViewModel.Action.SignInFeedbackShown)
            }
            null -> Unit
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = ZeroTheme.colors.surface,
    ) { paddingValues ->
        BackupDetailBody(
            state = state,
            paddingValues = paddingValues,
            onAction = viewModel::perform,
        )
    }
}

@Composable
private fun BackupDetailBody(
    state: BackupDetailViewModel.State,
    paddingValues: PaddingValues,
    onAction: (BackupDetailViewModel.Action) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
    ) {
        item { BackupTopBar(onBack = { onAction(BackupDetailViewModel.Action.Back) }) }
        if (state.isSignedIn) {
            item { ConnectedHeader(label = state.accountLabel ?: stringResource(R.string.backup_account_placeholder)) }
            item { BackupStatusBlock(phase = state.phase, lastSuccessAt = state.lastSuccessAt, lastError = state.lastError) }
            if (state.phase is BackupUseCase.Phase.Idle || state.phase is BackupUseCase.Phase.Failed) {
                item {
                    BackupPrimaryActions(
                        showRetry = state.phase is BackupUseCase.Phase.Failed,
                        onBackupNow = { onAction(BackupDetailViewModel.Action.BackupNow) },
                        onRestore = { onAction(BackupDetailViewModel.Action.Restore) },
                    )
                }
            }
            item {
                WifiOnlyToggleRow(
                    wifiOnly = state.wifiOnly,
                    onWifiOnlyChange = { wifiOnly -> onAction(BackupDetailViewModel.Action.SetWifiOnly(wifiOnly)) },
                )
            }
            item { DisconnectButton(onClick = { onAction(BackupDetailViewModel.Action.Disconnect) }) }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        } else {
            item {
                DisconnectedContent(onConnect = { onAction(BackupDetailViewModel.Action.Connect) })
            }
        }
    }
}

@Composable
private fun BackupTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.backup_back_description),
                modifier = Modifier.size(24.dp),
                tint = ZeroTheme.colors.onSurface,
            )
        }
        Text(
            text = stringResource(R.string.backup_screen_title),
            modifier = Modifier
                .weight(1f)
                .padding(end = 48.dp),
            textAlign = TextAlign.Center,
            style = TextStyle(
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = ZeroTheme.colors.onSurface,
            ),
        )
    }
}

@Composable
private fun DisconnectedContent(onConnect: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        BackupHeroIllustration()
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.backup_disconnected_title),
                textAlign = TextAlign.Center,
                style = TextStyle(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = ZeroTheme.colors.onSurface,
                ),
            )
            Text(
                text = stringResource(R.string.backup_disconnected_body),
                textAlign = TextAlign.Center,
                style = TextStyle(
                    fontSize = 14.sp,
                    color = ZeroTheme.colors.onSurfaceVariant,
                    lineHeight = 22.sp,
                ),
            )
        }
        PrimaryCtaButton(
            icon = Icons.Outlined.CloudUpload,
            label = stringResource(R.string.backup_connect),
            onClick = onConnect,
        )
    }
}

@Composable
private fun BackupHeroIllustration() {
    Box(
        modifier = Modifier
            .size(140.dp)
            .background(ZeroTheme.colors.primaryContainerLight, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.CloudUpload,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = ZeroTheme.colors.primaryContainer,
        )
    }
}

@Composable
private fun ConnectedHeader(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(ZeroTheme.colors.surfaceContainerLowest, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(ZeroTheme.colors.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.CloudDone,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = ZeroTheme.colors.surfaceContainerLowest,
            )
        }
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = ZeroTheme.colors.onSurface,
            ),
        )
    }
}

@Composable
private fun BackupStatusBlock(
    phase: BackupUseCase.Phase,
    lastSuccessAt: LocalDateTime?,
    lastError: BackupError?,
) {
    val relativeTime = rememberRelativeTime(lastSuccessAt)
    when (phase) {
        is BackupUseCase.Phase.Idle -> StatusCard(
            iconColor = ZeroTheme.colors.secondary,
            iconBackground = ZeroTheme.colors.secondaryContainer,
            icon = Icons.Outlined.CloudDone,
            tagText = stringResource(R.string.backup_status_up_to_date),
            tagColor = ZeroTheme.colors.secondary,
            body = if (relativeTime != null) {
                stringResource(R.string.backup_status_last_at, relativeTime)
            } else {
                stringResource(R.string.backup_status_never)
            },
            bodyColor = ZeroTheme.colors.onSurface,
        )
        is BackupUseCase.Phase.Uploading -> StatusCard(
            iconColor = ZeroTheme.colors.primaryContainer,
            iconBackground = ZeroTheme.colors.primaryContainerLight,
            icon = Icons.Outlined.CloudUpload,
            tagText = stringResource(R.string.backup_status_uploading_title),
            tagColor = ZeroTheme.colors.primaryContainer,
            body = stringResource(R.string.backup_status_uploading_body),
            bodyColor = ZeroTheme.colors.onSurface,
        )
        is BackupUseCase.Phase.Restoring -> StatusCard(
            iconColor = ZeroTheme.colors.primaryContainer,
            iconBackground = ZeroTheme.colors.primaryContainerLight,
            icon = Icons.Outlined.Restore,
            tagText = stringResource(R.string.backup_status_restoring_title),
            tagColor = ZeroTheme.colors.primaryContainer,
            body = stringResource(R.string.backup_status_restoring_body),
            bodyColor = ZeroTheme.colors.onSurface,
        )
        is BackupUseCase.Phase.Failed -> StatusCard(
            iconColor = ZeroTheme.colors.error,
            iconBackground = ZeroTheme.colors.errorContainer,
            icon = Icons.Outlined.ErrorOutline,
            tagText = stringResource(R.string.backup_status_failed_title),
            tagColor = ZeroTheme.colors.error,
            body = backupErrorMessage(lastError ?: phase.error),
            bodyColor = ZeroTheme.colors.onSurface,
        )
    }
}

@Composable
private fun StatusCard(
    iconColor: androidx.compose.ui.graphics.Color,
    iconBackground: androidx.compose.ui.graphics.Color,
    icon: ImageVector,
    tagText: String,
    tagColor: androidx.compose.ui.graphics.Color,
    body: String,
    bodyColor: androidx.compose.ui.graphics.Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(ZeroTheme.colors.surfaceContainerLowest, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(iconBackground, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = iconColor,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = tagText.uppercase(),
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = tagColor,
                    letterSpacing = 0.5.sp,
                ),
            )
            Text(
                text = body,
                style = TextStyle(
                    fontSize = 14.sp,
                    color = bodyColor,
                    lineHeight = 20.sp,
                ),
            )
        }
    }
}

@Composable
private fun BackupPrimaryActions(
    showRetry: Boolean,
    onBackupNow: () -> Unit,
    onRestore: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .background(ZeroTheme.colors.primaryContainer, RoundedCornerShape(14.dp))
                .clickable(onClick = onBackupNow)
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            Icon(
                imageVector = if (showRetry) Icons.Outlined.Refresh else Icons.Outlined.CloudUpload,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = ZeroTheme.colors.surfaceContainerLowest,
            )
            Text(
                text = stringResource(if (showRetry) R.string.backup_status_failed_retry else R.string.backup_now),
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = ZeroTheme.colors.surfaceContainerLowest,
                ),
            )
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .background(ZeroTheme.colors.surfaceContainerLowest, RoundedCornerShape(14.dp))
                .border(1.5.dp, ZeroTheme.colors.outlineVariant, RoundedCornerShape(14.dp))
                .clickable(onClick = onRestore)
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            Icon(
                imageVector = Icons.Outlined.Restore,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = ZeroTheme.colors.primaryContainer,
            )
            Text(
                text = stringResource(R.string.backup_restore),
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = ZeroTheme.colors.primaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun WifiOnlyToggleRow(
    wifiOnly: Boolean,
    onWifiOnlyChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(ZeroTheme.colors.surfaceContainerLowest, RoundedCornerShape(18.dp))
            .clickable { onWifiOnlyChange(!wifiOnly) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.backup_mobile_data_title),
                style = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = ZeroTheme.colors.onSurface,
                ),
            )
            Text(
                text = stringResource(R.string.backup_mobile_data_body),
                style = TextStyle(
                    fontSize = 13.sp,
                    color = ZeroTheme.colors.onSurfaceVariant,
                    lineHeight = 18.sp,
                ),
            )
        }
        Switch(
            checked = !wifiOnly,
            onCheckedChange = { mobileDataAllowed -> onWifiOnlyChange(!mobileDataAllowed) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = ZeroTheme.colors.primaryContainer,
                checkedTrackColor = ZeroTheme.colors.primaryContainerLight,
            ),
        )
    }
}

@Composable
private fun DisconnectButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .border(1.dp, ZeroTheme.colors.outlineVariant, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.backup_disconnect),
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = ZeroTheme.colors.error,
            ),
        )
    }
}

@Composable
private fun PrimaryCtaButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ZeroTheme.colors.primaryContainer, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = ZeroTheme.colors.surfaceContainerLowest,
        )
        Text(
            text = label,
            style = TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = ZeroTheme.colors.surfaceContainerLowest,
            ),
        )
    }
}

@Composable
private fun backupErrorMessage(error: BackupError): String = when (error) {
    is BackupError.NetworkUnavailable -> stringResource(R.string.backup_error_network)
    is BackupError.AuthExpired -> stringResource(R.string.backup_error_auth_expired)
    is BackupError.QuotaExceeded -> stringResource(R.string.backup_error_quota)
    is BackupError.ParseFailure -> stringResource(R.string.backup_error_parse)
    is BackupError.Unknown -> stringResource(R.string.backup_error_unknown, error.message)
}

@Composable
private fun rememberRelativeTime(at: LocalDateTime?): String? = at?.let { rememberBackupRelativeTime(it) }
