package com.hluhovskyi.zero.security

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.theme.ZeroTheme

internal class BiometricLockGateViewProvider(
    private val viewModel: BiometricLockGateViewModel,
) : ViewProvider {

    @Composable
    override fun View() {
        val state by viewModel.state.collectAsState(initial = BiometricLockGateViewModel.State())
        if (!state.isLocked) return

        LaunchedEffect(state.promptToken) {
            if (state.promptToken > 0) {
                viewModel.perform(BiometricLockGateViewModel.Action.Unlock)
            }
        }

        BiometricLockScreen(
            onUnlockClick = { viewModel.perform(BiometricLockGateViewModel.Action.Unlock) },
        )
    }
}

@Composable
private fun BiometricLockScreen(onUnlockClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ZeroTheme.colors.surface)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {},
            )
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(ZeroTheme.colors.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = ZeroTheme.colors.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.biometric_lock_screen_title),
                style = TextStyle(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = ZeroTheme.colors.onSurface,
                ),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.biometric_lock_screen_subtitle),
                style = TextStyle(
                    fontSize = 14.sp,
                    color = ZeroTheme.colors.onSurfaceVariant,
                ),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(ZeroTheme.colors.primaryContainer)
                    .clickable(onClick = onUnlockClick)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.biometric_lock_screen_action),
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ZeroTheme.colors.onPrimaryContainer,
                    ),
                )
            }
        }
    }
}
