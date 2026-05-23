package com.hluhovskyi.zero.budget

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.ui.common.toCompose
import com.hluhovskyi.zero.ui.theme.ZeroTheme

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

    val cardBg = if (isOver) OverBg else ZeroTheme.colors.surfaceContainerLowest
    val borderModifier = if (isOver) {
        Modifier.border(1.5.dp, ZeroTheme.colors.error.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(cardBg, RoundedCornerShape(16.dp))
            .then(borderModifier)
            .clickable(onClick = onTap)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconWithRing(
            row = row,
            pct = pct,
            isOver = isOver,
            imageLoader = imageLoader,
        )
        TextBlock(
            row = row,
            isOver = isOver,
            pct = pct,
            amountFormatter = amountFormatter,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun IconWithRing(
    row: BudgetQueryUseCase.Budgeted,
    pct: Float,
    isOver: Boolean,
    imageLoader: ImageLoader,
) {
    val bg = row.colorScheme.background.value.toCompose()
    val primary = row.colorScheme.primary.value.toCompose()
    val ringColor = ringColor(isOver = isOver, pct = pct, scheme = row.colorScheme)
    val trackColor = ZeroTheme.colors.surfaceContainer
    val animated by animateFloatAsState(
        targetValue = pct.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600),
        label = "ringGrow",
    )
    Box(
        modifier = Modifier.size(52.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = 3.dp.toPx()
            val diameter = 48.dp.toPx()
            val topLeft = Offset(
                x = (size.width - diameter) / 2f,
                y = (size.height - diameter) / 2f,
            )
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx),
            )
            if (animated > 0f) {
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
            }
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(bg, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            imageLoader.View(
                modifier = Modifier.size(22.dp),
                image = row.icon,
                tint = primary,
            )
        }
    }
}

@Composable
private fun TextBlock(
    row: BudgetQueryUseCase.Budgeted,
    isOver: Boolean,
    pct: Float,
    amountFormatter: AmountFormatter,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
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
                    color = ZeroTheme.colors.onSurface,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = amountFormatter.format(row.spent),
                style = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isOver) ZeroTheme.colors.error else ZeroTheme.colors.primary,
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
                text = statusText(row, isOver, amountFormatter),
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor(isOver, pct),
                ),
            )
            Text(
                text = stringResource(
                    R.string.budget_card_of,
                    amountFormatter.format(row.budgeted),
                ),
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = ZeroTheme.colors.onSurfaceVariant,
                ),
            )
        }
    }
}

@Composable
private fun ringColor(isOver: Boolean, pct: Float, scheme: ColorScheme): Color = when {
    isOver -> ZeroTheme.colors.error
    pct > 0.85f -> OrangeWarn
    pct > 0.65f -> YellowWarn
    else -> lerp(
        scheme.background.value.toCompose(),
        scheme.primary.value.toCompose(),
        0.3f,
    )
}

@Composable
private fun statusText(
    row: BudgetQueryUseCase.Budgeted,
    isOver: Boolean,
    amountFormatter: AmountFormatter,
): String {
    val diff = if (isOver) row.spent - row.budgeted else row.budgeted - row.spent
    val formatted = amountFormatter.format(diff)
    return if (isOver) {
        stringResource(R.string.budget_card_over, formatted)
    } else {
        stringResource(R.string.budget_card_left, formatted)
    }
}

@Composable
private fun statusColor(isOver: Boolean, pct: Float): Color = when {
    isOver -> ZeroTheme.colors.error
    pct > 0.85f -> OrangeWarn
    else -> ZeroTheme.colors.onSurfaceVariant
}
