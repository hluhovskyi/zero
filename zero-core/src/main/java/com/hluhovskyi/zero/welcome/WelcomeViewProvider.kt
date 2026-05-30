package com.hluhovskyi.zero.welcome

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.transactions.OnAddTransactionHandler
import com.hluhovskyi.zero.ui.ZeroFab
import com.hluhovskyi.zero.ui.theme.ZeroTheme

internal class WelcomeViewProvider(
    private val viewModel: WelcomeViewModel,
    private val onAddTransaction: OnAddTransactionHandler,
) : ViewProvider {

    @Composable
    override fun View() {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
                    text = stringResource(R.string.home_title),
                    style = TextStyle(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = ZeroTheme.colors.primary,
                    ),
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    WelcomeIllustration()
                    Spacer(Modifier.height(32.dp))
                    Text(
                        text = stringResource(R.string.welcome_heading),
                        style = TextStyle(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ZeroTheme.colors.primary,
                            letterSpacing = (-0.48).sp,
                            lineHeight = 28.8.sp,
                        ),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        modifier = Modifier.widthIn(max = 260.dp),
                        text = stringResource(R.string.welcome_subtitle),
                        style = TextStyle(
                            fontSize = 15.sp,
                            color = ZeroTheme.colors.onSurfaceVariant,
                            lineHeight = 23.sp,
                        ),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(28.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                viewModel.perform(WelcomeViewModel.Action.ImportSelected)
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FileDownload,
                            contentDescription = null,
                            tint = ZeroTheme.colors.primaryContainer,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.welcome_import_action),
                            style = TextStyle(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ZeroTheme.colors.primaryContainer,
                            ),
                        )
                    }
                }
            }
            ZeroFab(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 32.dp),
                onClick = { onAddTransaction.onAddTransaction() },
                icon = Icons.Filled.Add,
                contentDescription = stringResource(R.string.transaction_add),
                expanded = true,
                text = stringResource(R.string.transaction_add),
            )
        }
    }
}

@Composable
private fun WelcomeIllustration() {
    Box(modifier = Modifier.size(width = 200.dp, height = 160.dp)) {
        // Back card
        Box(
            modifier = Modifier
                .padding(start = 20.dp, top = 24.dp)
                .rotate(-6f)
                .size(width = 152.dp, height = 96.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(ZeroTheme.colors.selectedPill),
        )
        // Mid card
        Box(
            modifier = Modifier
                .padding(start = 16.dp, top = 16.dp)
                .rotate(-2f)
                .size(width = 152.dp, height = 96.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(ZeroTheme.colors.primaryContainerLight),
        ) {
            CardLine(offsetY = 14.dp, offsetX = 16.dp, width = 120.dp, color = ZeroTheme.colors.primary.copy(alpha = 0.10f))
            CardLine(offsetY = 30.dp, offsetX = 16.dp, width = 60.dp, color = ZeroTheme.colors.primary.copy(alpha = 0.08f))
            CardLine(offsetY = 48.dp, offsetX = 16.dp, width = 40.dp, color = ZeroTheme.colors.primary.copy(alpha = 0.08f))
        }
        // Front card
        Box(
            modifier = Modifier
                .padding(start = 24.dp, top = 8.dp)
                .size(width = 152.dp, height = 96.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(ZeroTheme.colors.primaryContainer),
        ) {
            CardLine(offsetY = 14.dp, offsetX = 16.dp, width = 120.dp, color = ZeroTheme.colors.welcomeCardLine.copy(alpha = 0.20f))
            CardLine(offsetY = 30.dp, offsetX = 16.dp, width = 80.dp, color = ZeroTheme.colors.welcomeCardLine.copy(alpha = 0.15f))
            CardLine(offsetY = 48.dp, offsetX = 16.dp, width = 48.dp, color = ZeroTheme.colors.welcomeCardLine.copy(alpha = 0.15f))
            Text(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 14.dp),
                text = stringResource(R.string.welcome_illustration_amount_placeholder),
                style = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = ZeroTheme.colors.welcomeCardLine.copy(alpha = 0.9f),
                    letterSpacing = (-0.36).sp,
                ),
            )
        }
        // Plus badge
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 10.dp, bottom = 12.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(ZeroTheme.colors.surface)
                .padding(3.dp)
                .clip(CircleShape)
                .background(ZeroTheme.colors.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = ZeroTheme.colors.secondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun CardLine(
    offsetY: Dp,
    offsetX: Dp,
    width: Dp,
    color: Color,
) {
    Box(
        modifier = Modifier
            .padding(top = offsetY, start = offsetX)
            .size(width = width, height = 6.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color),
    )
}
