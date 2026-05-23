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
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.ui.common.toCompose
import com.hluhovskyi.zero.ui.theme.ZeroTheme

private val OverBg = Color(0xFFFFF8F6)
private val OrangeWarn = Color(0xFFE65100)
private val YellowWarn = Color(0xFFF9A825)

@Composable
internal fun BudgetCard(
    item: BudgetViewModel.Item.Set,
    onTap: () -> Unit,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
    modifier: Modifier = Modifier,
) {
    val isOver = item.status == BudgetViewModel.Item.Status.Over
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
        IconWithRing(item = item, imageLoader = imageLoader)
        TextBlock(
            item = item,
            amountFormatter = amountFormatter,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun IconWithRing(
    item: BudgetViewModel.Item.Set,
    imageLoader: ImageLoader,
) {
    // In dark mode the entity's primary/background swap roles so the icon
    // container reads as theme-coherent on the dark surface — mirrors
    // CategoryIconView. The progress ring keeps the original scheme colors.
    val schemeBg = item.colorScheme.background.value.toCompose()
    val schemePrimary = item.colorScheme.primary.value.toCompose()
    val bg = if (ZeroTheme.colors.isLight) schemeBg else schemePrimary
    val primary = if (ZeroTheme.colors.isLight) schemePrimary else schemeBg
    val ringColor = ringColor(item.status, bg = schemeBg, primary = schemePrimary)
    val trackColor = ZeroTheme.colors.surfaceContainer
    val animated by animateFloatAsState(
        targetValue = item.progress,
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
                image = item.icon,
                tint = primary,
            )
        }
    }
}

@Composable
private fun TextBlock(
    item: BudgetViewModel.Item.Set,
    amountFormatter: AmountFormatter,
    modifier: Modifier = Modifier,
) {
    val isOver = item.status == BudgetViewModel.Item.Status.Over
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = item.name,
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
                text = amountFormatter.format(item.spent),
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
                text = stringResource(
                    if (isOver) R.string.budget_card_over else R.string.budget_card_left,
                    amountFormatter.format(item.remaining),
                ),
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor(item.status),
                ),
            )
            Text(
                text = stringResource(
                    R.string.budget_card_of,
                    amountFormatter.format(item.budgeted),
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
private fun ringColor(
    status: BudgetViewModel.Item.Status,
    bg: Color,
    primary: Color,
): Color = when (status) {
    BudgetViewModel.Item.Status.Over -> ZeroTheme.colors.error
    BudgetViewModel.Item.Status.AlmostThere -> OrangeWarn
    BudgetViewModel.Item.Status.Watch -> YellowWarn
    BudgetViewModel.Item.Status.Healthy -> lerp(bg, primary, 0.3f)
}

@Composable
private fun statusColor(status: BudgetViewModel.Item.Status): Color = when (status) {
    BudgetViewModel.Item.Status.Over -> ZeroTheme.colors.error
    BudgetViewModel.Item.Status.AlmostThere -> OrangeWarn
    BudgetViewModel.Item.Status.Watch,
    BudgetViewModel.Item.Status.Healthy,
    -> ZeroTheme.colors.onSurfaceVariant
}
