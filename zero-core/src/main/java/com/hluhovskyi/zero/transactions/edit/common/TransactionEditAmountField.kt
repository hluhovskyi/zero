package com.hluhovskyi.zero.transactions.edit.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ui.theme.ZeroTheme

/**
 * Boxed amount tile. [hero] = big figure, no border (caret only). Split (default) = medium figure,
 * focus background + border. Read-only display — the inline keypad supplies edits via the parent.
 * When [onCurrencyClick] is set the currency renders as a tappable chip (expense/income), otherwise
 * a static symbol (transfer).
 */
@Composable
internal fun TransactionEditAmountField(
    modifier: Modifier = Modifier,
    caption: String,
    currencySymbol: String,
    value: String,
    focused: Boolean,
    onFocus: () -> Unit,
    hero: Boolean = false,
    onCurrencyClick: (() -> Unit)? = null,
) {
    val focusBg = !hero && focused
    Column(
        modifier = modifier
            .background(
                if (focusBg) ZeroTheme.colors.surface else ZeroTheme.colors.surfaceContainerLow,
                RoundedCornerShape(18.dp),
            )
            .then(
                if (focusBg) {
                    Modifier.border(1.5.dp, ZeroTheme.colors.primaryContainer, RoundedCornerShape(18.dp))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onFocus)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text = caption.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = ZeroTheme.colors.onSurfaceVariant,
            letterSpacing = 1.2.sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onCurrencyClick != null) {
                Row(
                    modifier = Modifier
                        .background(ZeroTheme.colors.surfaceContainer, RoundedCornerShape(10.dp))
                        .clickable(onClick = onCurrencyClick)
                        .padding(start = 9.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = currencySymbol,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = ZeroTheme.colors.primaryContainer,
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = ZeroTheme.colors.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    text = currencySymbol,
                    fontSize = if (hero) 21.sp else 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = ZeroTheme.colors.onSurfaceVariant,
                )
            }
            Box(modifier = Modifier.weight(1f))
            Text(
                text = value.ifEmpty { "0" },
                fontSize = if (hero) 36.sp else 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = ZeroTheme.colors.primaryContainer,
                maxLines = 1,
            )
            Box(
                modifier = Modifier
                    .padding(start = 3.dp)
                    .width(3.dp)
                    .height(if (hero) 29.dp else 22.dp)
                    .background(if (focused) ZeroTheme.colors.primaryContainer else Color.Transparent),
            )
        }
    }
}
