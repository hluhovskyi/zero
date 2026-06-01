package com.hluhovskyi.zero.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.ui.theme.ZeroTheme

private val KEYS = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "0", "⌫")

internal fun handleAmountKeypadKey(value: String, key: String): String = when {
    key == "⌫" -> if (value.length <= 1) "0" else value.dropLast(1)
    key == "." -> if (value.contains(".")) value else "$value."
    else -> {
        if (value == "0") {
            key
        } else {
            val dotIndex = value.indexOf('.')
            if (dotIndex >= 0 && value.length - dotIndex - 1 >= 2) {
                value
            } else {
                "$value$key"
            }
        }
    }
}

@Composable
fun AmountKeypad(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyHeight: Dp = 50.dp,
) {
    val view = LocalView.current
    Column(modifier = modifier.fillMaxWidth()) {
        KEYS.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(keyHeight)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onChange(handleAmountKeypadKey(value, key))
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (key == "⌫") {
                            Icon(
                                imageVector = Icons.Filled.Backspace,
                                contentDescription = stringResource(R.string.numpad_backspace_cd),
                                tint = ZeroTheme.colors.outlineVariant,
                            )
                        } else {
                            Text(
                                text = key,
                                style = TextStyle(
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = ZeroTheme.colors.primaryContainer,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
