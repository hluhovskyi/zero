package com.hluhovskyi.zero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.ui.theme.ZeroTheme

@Composable
fun ImportErrorBanner(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ZeroTheme.colors.errorContainer, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(ZeroTheme.colors.importErrorContainer, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Cancel,
                contentDescription = null,
                tint = ZeroTheme.colors.error,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.import_error_banner_title),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = ZeroTheme.colors.error,
            )
            Text(
                text = message,
                fontSize = 13.sp,
                color = ZeroTheme.colors.importErrorContent,
                lineHeight = 19.sp,
            )
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .background(ZeroTheme.colors.error, RoundedCornerShape(8.dp))
                        .clickable(onClick = onRetry)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.import_error_banner_retry),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = ZeroTheme.colors.onError,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.import_error_banner_dismiss),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ZeroTheme.colors.error,
                    )
                }
            }
        }
    }
}
