package com.hluhovskyi.zero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.ui.theme.ZeroTheme

/**
 * Shared header for import flow step screens (categories, accounts, transactions).
 * Shows a back button, centered title, step counter "N / total", and a segmented progress bar.
 *
 * @param title Screen title shown centred in the toolbar
 * @param step 1-based current step index (e.g. 1 for categories, 2 for accounts)
 * @param totalSteps Total number of steps in the flow (4 by default)
 * @param onBack Called when the back button is tapped
 */
@Composable
fun ImportStepHeader(
    title: String,
    step: Int,
    totalSteps: Int = 4,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back_description),
                    tint = ZeroTheme.colors.primaryContainer,
                )
            }
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = ZeroTheme.colors.primaryContainer,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = buildAnnotatedString {
                    append("${step + 1}")
                    withStyle(SpanStyle(color = ZeroTheme.colors.onSurfaceVariant.copy(alpha = 0.5f))) {
                        append(" / $totalSteps")
                    }
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = ZeroTheme.colors.onSurfaceVariant,
                modifier = Modifier
                    .width(48.dp)
                    .padding(end = 4.dp),
                textAlign = TextAlign.End,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            repeat(totalSteps - 1) { i ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (i < step) ZeroTheme.colors.primaryContainer else ZeroTheme.colors.outlineVariant),
                )
            }
        }
    }
}
