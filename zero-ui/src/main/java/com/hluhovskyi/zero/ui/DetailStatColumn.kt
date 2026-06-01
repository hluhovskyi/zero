package com.hluhovskyi.zero.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DetailStatColumn(
    label: String,
    value: String,
    labelColor: Color,
    valueColor: Color,
    valueFontSize: TextUnit = 17.sp,
) {
    Column {
        Text(
            text = label,
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = labelColor,
                letterSpacing = 1.2.sp,
            ),
        )
        Spacer(Modifier.size(2.dp))
        Text(
            text = value,
            style = TextStyle(
                fontSize = valueFontSize,
                fontWeight = FontWeight.Bold,
                color = valueColor,
            ),
        )
    }
}
