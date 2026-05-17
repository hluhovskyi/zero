package com.hluhovskyi.zero.budget

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.ui.CategoryIconView
import com.hluhovskyi.zero.ui.common.toUi
import com.hluhovskyi.zero.ui.theme.Error
import com.hluhovskyi.zero.ui.theme.OnSurface
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant
import com.hluhovskyi.zero.ui.theme.OutlineVariant
import com.hluhovskyi.zero.ui.theme.Primary
import com.hluhovskyi.zero.ui.theme.SurfaceContainer
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLowest

private val OverBg = Color(0xFFFFF8F6)
private val OrangeWarn = Color(0xFFE65100)
private val YellowWarn = Color(0xFFF9A825)

@Composable
internal fun BudgetCard(
    row: BudgetQueryUseCase.Budgeted,
    onTap: () -> Unit,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
    modifier: Modifier = Modifier,
) {
    val isOver = row.spent > row.budgeted
    val pct = if (row.budgeted > Amount.zero()) {
        (row.spent.value.toDouble() / row.budgeted.value.toDouble()).toFloat()
    } else {
        0f
    }

    val cardBg = if (isOver) OverBg else SurfaceContainerLowest
    val borderModifier = if (isOver) {
        Modifier.border(1.5.dp, Error.copy(alpha = 0.2f), RoundedCornerShape(18.dp))
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .background(cardBg, RoundedCornerShape(18.dp))
            .then(borderModifier)
            .clickable(onClick = onTap)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CategoryIconView(
                colorScheme = row.colorScheme.toUi(),
                size = 34.dp,
                contentPadding = 7.dp,
            ) { tint ->
                imageLoader.View(
                    modifier = Modifier.size(20.dp),
                    image = row.icon,
                    tint = tint,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = row.categoryName,
                        modifier = Modifier.weight(1f),
                        style = TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurface,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = amountFormatter.format(row.spent, currencySymbol = "$"),
                        style = TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isOver) Error else Primary,
                        ),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = statusText(row, isOver, pct, amountFormatter),
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = statusColor(isOver, pct),
                        ),
                    )
                    Text(
                        text = stringResource(
                            R.string.budget_card_of,
                            amountFormatter.format(row.budgeted, currencySymbol = "$"),
                        ),
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = OnSurfaceVariant,
                        ),
                    )
                }
            }
        }

        ProgressBar(pct = pct, isOver = isOver, cardBg = cardBg)
    }
}

@Composable
private fun ProgressBar(pct: Float, isOver: Boolean, cardBg: Color) {
    val animated by animateFloatAsState(
        targetValue = pct.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600),
        label = "barGrow",
    )
    val barColor = when {
        isOver -> Error
        pct > 0.85f -> OrangeWarn
        pct > 0.65f -> YellowWarn
        else -> Primary
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(SurfaceContainer, RoundedCornerShape(4.dp)),
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .background(barColor, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (isOver) {
                Box(
                    modifier = Modifier
                        .offset(x = 2.dp)
                        .size(12.dp)
                        .background(cardBg, CircleShape)
                        .padding(2.dp)
                        .background(Error, CircleShape),
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(2.dp)
                .height(14.dp)
                .background(OutlineVariant, RoundedCornerShape(1.dp)),
        )
    }
}

@Composable
private fun statusText(
    row: BudgetQueryUseCase.Budgeted,
    isOver: Boolean,
    pct: Float,
    amountFormatter: AmountFormatter,
): String {
    val diff = if (isOver) row.spent - row.budgeted else row.budgeted - row.spent
    val formatted = amountFormatter.format(diff, currencySymbol = "$")
    return when {
        isOver -> stringResource(R.string.budget_card_over_limit, formatted)
        pct > 0.85f -> stringResource(R.string.budget_card_almost_there, formatted)
        else -> stringResource(R.string.budget_card_remaining, formatted)
    }
}

private fun statusColor(isOver: Boolean, pct: Float): Color = when {
    isOver -> Error
    pct > 0.85f -> OrangeWarn
    else -> OnSurfaceVariant
}
