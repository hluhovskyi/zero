package com.hluhovskyi.zero.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.ui.theme.PrimaryContainer

@Composable
fun ModalHeader(
    modifier: Modifier = Modifier,
    title: String,
    onClose: () -> Unit,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.action_close_description),
                tint = PrimaryContainer,
            )
        }
        Text(
            modifier = Modifier.weight(1f),
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryContainer,
            textAlign = TextAlign.Center,
        )
        Box(modifier = Modifier.widthIn(min = 48.dp)) {
            trailingContent?.invoke()
        }
    }
}
