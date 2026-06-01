package com.hluhovskyi.zero.budget

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.AmountKeypad
import com.hluhovskyi.zero.ui.CategoryIconView
import com.hluhovskyi.zero.ui.common.toCompose
import com.hluhovskyi.zero.ui.common.toUi
import com.hluhovskyi.zero.ui.theme.ZeroTheme
import kotlinx.coroutines.delay
import java.math.BigDecimal

private const val TOAST_DURATION_MS = 2800L

internal class BudgetViewProvider(
    private val viewModel: BudgetViewModel,
    private val imageLoader: ImageLoader,
    private val amountFormatter: AmountFormatter,
) : ViewProvider {

    @Composable
    override fun View() {
        BudgetView(
            viewModel = viewModel,
            imageLoader = imageLoader,
            amountFormatter = amountFormatter,
        )
    }
}

@Composable
private fun BudgetView(
    viewModel: BudgetViewModel,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
) {
    val state by viewModel.state.collectAsState()
    var toastMessage by remember { mutableStateOf<String?>(null) }

    // Inline overlays aren't navigation destinations, so back must step back through the open
    // ones instead of falling through to leave the Budget screen. The remove confirm is layered
    // over the numpad, so its handler is registered last to win while both are open: back goes
    // confirm → numpad → list. (The copy dialog is a `Dialog`, which handles back itself.)
    BackHandler(enabled = state.editingCategoryId != null) {
        viewModel.perform(BudgetViewModel.Action.DismissInlineEdit)
    }
    BackHandler(enabled = state.removeConfirm != null) {
        viewModel.perform(BudgetViewModel.Action.CancelRemove)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item { BudgetTitle() }
            item {
                BudgetMonthSelector(
                    label = state.displayedPeriodLabel,
                    hasOlder = state.hasPrevious,
                    hasNewer = state.hasNext,
                    onOlder = { viewModel.perform(BudgetViewModel.Action.SelectOlderMonth) },
                    onNewer = { viewModel.perform(BudgetViewModel.Action.SelectNewerMonth) },
                )
            }
            if (!state.hasAnyBudget) {
                item {
                    EmptyBudgetCallout(
                        periodLabel = state.displayedPeriodLabel,
                        totalCategories = state.budgeted.size,
                        previousPeriodHadBudgets = state.hasAnyPreviousBudget,
                    )
                }
            } else {
                item {
                    SummaryBar(
                        summary = state.summary,
                        amountFormatter = amountFormatter,
                    )
                }
            }
            if (state.hasAnyPreviousBudget) {
                item {
                    CopyFromPreviousCard(
                        monthLabel = state.previousPeriodLabel,
                        count = state.previousBudgetSetCount,
                        onClick = { viewModel.perform(BudgetViewModel.Action.TapCopyFromPrevious) },
                    )
                }
            }
            item {
                SectionLabel(
                    stringResource(
                        if (state.hasAnyBudget) {
                            R.string.budget_section_categories
                        } else {
                            R.string.budget_section_set_limits
                        },
                    ),
                )
            }
            items(state.items, key = { it.categoryId.value }) { item ->
                val onTap = { viewModel.perform(BudgetViewModel.Action.TapCategory(item.categoryId)) }
                when (item) {
                    is BudgetViewModel.Item.Set -> BudgetCard(
                        item = item,
                        onTap = onTap,
                        onReallocate = { viewModel.perform(BudgetViewModel.Action.TapReallocate(item.categoryId)) },
                        onIncrease = { viewModel.perform(BudgetViewModel.Action.TapIncrease(item.categoryId)) },
                        imageLoader = imageLoader,
                        amountFormatter = amountFormatter,
                    )
                    is BudgetViewModel.Item.Unset -> UnsetCategoryRow(
                        item = item,
                        imageLoader = imageLoader,
                        amountFormatter = amountFormatter,
                        onClick = onTap,
                    )
                }
            }
        }

        if (state.copyConfirmVisible) {
            CopyConfirmDialog(
                onConfirm = {
                    val count = state.previousBudgetSetCount
                    val label = state.previousPeriodLabel
                    viewModel.perform(BudgetViewModel.Action.ConfirmCopy)
                    toastMessage = "Copied $count categories from $label"
                },
                onCancel = { viewModel.perform(BudgetViewModel.Action.CancelCopy) },
            )
        }

        state.removeConfirmRow?.let { row ->
            val toast = stringResource(R.string.budget_remove_toast, row.categoryName)
            RemoveConfirmSheet(
                name = row.categoryName,
                icon = row.icon,
                colorScheme = row.colorScheme,
                imageLoader = imageLoader,
                onConfirm = {
                    viewModel.perform(BudgetViewModel.Action.ConfirmRemove)
                    toastMessage = toast
                },
                onCancel = { viewModel.perform(BudgetViewModel.Action.CancelRemove) },
            )
        }

        InlineNumpadOverlay(
            state = state,
            imageLoader = imageLoader,
            amountFormatter = amountFormatter,
            viewModel = viewModel,
        )

        BudgetToast(
            message = toastMessage,
            onDismiss = { toastMessage = null },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun InlineNumpadOverlay(
    state: BudgetViewModel.State,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
    viewModel: BudgetViewModel,
) {
    // Hidden while the remove confirmation is layered on top; cancelling brings it back.
    val visible = state.editingCategoryId != null && state.removeConfirm == null
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
    ) {
        val row = state.editingRow ?: return@AnimatedVisibility

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ZeroTheme.colors.scrim)
                    .clickable { viewModel.perform(BudgetViewModel.Action.DismissInlineEdit) }
                    .testTag("Budget.inlineNumpad.scrim"),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        ZeroTheme.colors.surface,
                        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    )
                    .padding(top = 8.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 40.dp, height = 4.dp)
                            .background(ZeroTheme.colors.outlineVariant, RoundedCornerShape(2.dp)),
                    )
                }
                InlineNumpadHeader(
                    name = row.categoryName,
                    icon = row.icon,
                    colorScheme = row.colorScheme,
                    previousAmount = state.editingPreviousAmount,
                    isPreviousSelected = state.isPreviousAmountSelected,
                    // Removal only applies to a budget that's already set.
                    canRemove = row.budgetId != null,
                    imageLoader = imageLoader,
                    amountFormatter = amountFormatter,
                    onPreviousChip = { viewModel.perform(BudgetViewModel.Action.TapPreviousChip) },
                    onRemove = { viewModel.perform(BudgetViewModel.Action.TapRemove) },
                )
                InlineAmountDisplay(state.editingAmountText)
                AmountKeypad(
                    value = state.editingAmountText,
                    onChange = { viewModel.perform(BudgetViewModel.Action.ChangeEditAmount(it)) },
                    modifier = Modifier.padding(top = 8.dp),
                )
                InlineCommitButton(
                    text = state.editingAmountText,
                    hasNextUnset = state.hasNextUnsetForEditing,
                    onCommit = { viewModel.perform(BudgetViewModel.Action.CommitInlineEdit) },
                )
            }
        }
    }
}

@Composable
private fun InlineNumpadHeader(
    name: String,
    icon: Image,
    colorScheme: ColorScheme,
    previousAmount: Amount?,
    isPreviousSelected: Boolean,
    canRemove: Boolean,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
    onPreviousChip: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryIconView(
            colorScheme = colorScheme.toUi(),
            size = 32.dp,
            contentPadding = 6.dp,
        ) { tint ->
            imageLoader.View(
                modifier = Modifier.size(18.dp),
                image = icon,
                tint = tint,
            )
        }
        Text(
            text = name,
            modifier = Modifier.weight(1f),
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ZeroTheme.colors.onSurface),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (previousAmount != null) {
            Box(
                modifier = Modifier
                    .background(
                        color = if (isPreviousSelected) ZeroTheme.colors.primaryContainer else ZeroTheme.colors.surfaceContainerLow,
                        shape = RoundedCornerShape(20.dp),
                    )
                    .clickable(onClick = onPreviousChip)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = stringResource(R.string.budget_edit_last_month_chip, amountFormatter.format(previousAmount, currencySymbol = "$")),
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isPreviousSelected) ZeroTheme.colors.surface else ZeroTheme.colors.primaryContainer,
                    ),
                )
            }
        }
        if (canRemove) {
            Icon(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = stringResource(R.string.budget_remove_confirm_remove),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onRemove)
                    .testTag("Budget.inlineNumpad.remove")
                    .padding(6.dp)
                    .size(22.dp),
                tint = ZeroTheme.colors.error,
            )
        }
    }
}

@Composable
private fun InlineAmountDisplay(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Center,
    ) {
        val hasAmount = text != "0"
        Text(
            text = "$",
            style = TextStyle(
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = if (hasAmount) ZeroTheme.colors.onSurfaceVariant else ZeroTheme.colors.outlineVariant,
            ),
        )
        Text(
            text = text,
            modifier = if (!hasAmount) Modifier.alpha(0.3f) else Modifier,
            style = TextStyle(
                fontSize = 44.sp,
                fontWeight = FontWeight.ExtraBold,
                color = ZeroTheme.colors.primaryContainer,
            ),
        )
    }
}

@Composable
private fun InlineCommitButton(text: String, hasNextUnset: Boolean, onCommit: () -> Unit) {
    val parsed = text.toBigDecimalOrNull()
    val hasAmount = parsed != null && parsed > BigDecimal.ZERO
    val label = when {
        hasNextUnset && hasAmount -> stringResource(R.string.budget_numpad_set_next, "$$text")
        hasNextUnset && !hasAmount -> stringResource(R.string.budget_numpad_skip_next)
        !hasNextUnset && hasAmount -> stringResource(R.string.budget_numpad_set, "$$text")
        else -> stringResource(R.string.budget_numpad_close)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 4.dp, bottom = 28.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(ZeroTheme.colors.primaryContainer, RoundedCornerShape(14.dp))
                .clickable(onClick = onCommit)
                .testTag("Budget.inlineNumpad.commit")
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ZeroTheme.colors.surface),
            )
        }
    }
}

@Composable
private fun BudgetTitle() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.budget_title),
            style = TextStyle(
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = ZeroTheme.colors.onSurface,
            ),
        )
    }
}

@Composable
private fun BudgetMonthSelector(
    label: String,
    hasOlder: Boolean,
    hasNewer: Boolean,
    onOlder: () -> Unit,
    onNewer: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.ChevronLeft,
            contentDescription = stringResource(R.string.budget_previous_month_cd),
            modifier = Modifier
                .size(24.dp)
                .clickable(enabled = hasOlder, onClick = onOlder),
            tint = if (hasOlder) ZeroTheme.colors.onSurface else ZeroTheme.colors.outlineVariant,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = ZeroTheme.colors.onSurface,
            ),
        )
        Spacer(Modifier.width(16.dp))
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = stringResource(R.string.budget_next_month_cd),
            modifier = Modifier
                .size(24.dp)
                .clickable(enabled = hasNewer, onClick = onNewer),
            tint = if (hasNewer) ZeroTheme.colors.onSurface else ZeroTheme.colors.outlineVariant,
        )
    }
}

@Composable
private fun EmptyBudgetCallout(
    periodLabel: String,
    totalCategories: Int,
    previousPeriodHadBudgets: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .background(ZeroTheme.colors.primaryContainer, RoundedCornerShape(20.dp))
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.budget_empty_title, periodLabel),
            style = TextStyle(
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = ZeroTheme.colors.onPrimary,
            ),
        )
        Text(
            text = stringResource(R.string.budget_empty_subtitle),
            style = TextStyle(
                fontSize = 13.sp,
                color = ZeroTheme.colors.onPrimaryContainer,
            ),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            CalloutStat(label = stringResource(R.string.budget_stat_categories), value = totalCategories.toString())
            CalloutStat(label = stringResource(R.string.budget_stat_budgets_set), value = "0")
            CalloutStat(
                label = stringResource(R.string.budget_stat_last_month),
                value = if (previousPeriodHadBudgets) {
                    stringResource(R.string.budget_stat_last_month_set)
                } else {
                    stringResource(R.string.budget_stat_last_month_none)
                },
            )
        }
    }
}

@Composable
private fun CalloutStat(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label.uppercase(),
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = ZeroTheme.colors.onPrimaryContainer,
                letterSpacing = 1.sp,
            ),
        )
        Text(
            text = value,
            style = TextStyle(
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = ZeroTheme.colors.onPrimary,
            ),
        )
    }
}

@Composable
private fun CopyFromPreviousCard(
    monthLabel: String,
    count: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .background(ZeroTheme.colors.surfaceContainerLow, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.budget_copy_from_previous, monthLabel),
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ZeroTheme.colors.onSurface,
                ),
            )
            Text(
                text = stringResource(R.string.budget_copy_count, count),
                style = TextStyle(fontSize = 12.sp, color = ZeroTheme.colors.onSurfaceVariant),
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = ZeroTheme.colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(
        text = label.uppercase(),
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
        style = TextStyle(
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = ZeroTheme.colors.onSurfaceVariant,
            letterSpacing = 0.8.sp,
        ),
    )
}

@Composable
private fun UnsetCategoryRow(
    item: BudgetViewModel.Item.Unset,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(ZeroTheme.colors.surfaceContainerLowest, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        UnsetIconWithRing(item = item, imageLoader = imageLoader)
        Column(modifier = Modifier.weight(1f)) {
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
                    text = amountFormatter.format(Amount.zero()),
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = ZeroTheme.colors.outline,
                    ),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = ZeroTheme.colors.primaryContainer,
                    )
                    Text(
                        text = stringResource(R.string.budget_set_limit),
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ZeroTheme.colors.primaryContainer,
                        ),
                    )
                }
                Text(
                    text = if (item.previousAmount != null) {
                        stringResource(R.string.budget_card_last, amountFormatter.format(item.previousAmount))
                    } else {
                        stringResource(R.string.budget_card_no_limit)
                    },
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = ZeroTheme.colors.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}

@Composable
private fun UnsetIconWithRing(
    item: BudgetViewModel.Item.Unset,
    imageLoader: ImageLoader,
) {
    // Match CategoryIconView: in dark mode the entity's primary/background swap
    // so the icon container reads as theme-coherent on the dark surface.
    val schemeBg = item.colorScheme.background.value.toCompose()
    val schemePrimary = item.colorScheme.primary.value.toCompose()
    val bg = if (ZeroTheme.colors.isLight) schemeBg else schemePrimary
    val primary = if (ZeroTheme.colors.isLight) schemePrimary else schemeBg
    val ringColor = ZeroTheme.colors.surfaceContainer
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
                color = ringColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(
                    width = strokePx,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())),
                ),
            )
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
internal fun BudgetToast(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(message) {
        if (message != null) {
            delay(TOAST_DURATION_MS)
            onDismiss()
        }
    }
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
        modifier = modifier.padding(horizontal = 16.dp, vertical = 88.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ZeroTheme.colors.primaryContainer, RoundedCornerShape(14.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = ZeroTheme.colors.transactionIncome,
            )
            Text(
                text = message.orEmpty(),
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ZeroTheme.colors.onPrimary,
                ),
            )
        }
    }
}

@Composable
private fun RemoveConfirmSheet(
    name: String,
    icon: Image,
    colorScheme: ColorScheme,
    imageLoader: ImageLoader,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ZeroTheme.colors.scrim)
                .clickable(onClick = onCancel)
                .testTag("Budget.removeConfirm.scrim"),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    ZeroTheme.colors.surface,
                    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                )
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 28.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 4.dp)
                        .background(ZeroTheme.colors.outlineVariant, RoundedCornerShape(2.dp)),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CategoryIconView(
                    colorScheme = colorScheme.toUi(),
                    size = 44.dp,
                    contentPadding = 9.dp,
                ) { tint ->
                    imageLoader.View(
                        modifier = Modifier.size(22.dp),
                        image = icon,
                        tint = tint,
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.budget_remove_confirm_title, name),
                        style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = ZeroTheme.colors.onSurface),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.budget_remove_confirm_subtitle),
                        style = TextStyle(fontSize = 13.sp, color = ZeroTheme.colors.onSurfaceVariant),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(ZeroTheme.colors.surfaceContainerLow, RoundedCornerShape(12.dp))
                        .clickable(onClick = onCancel)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.budget_remove_confirm_cancel),
                        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ZeroTheme.colors.onSurface),
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(ZeroTheme.colors.error, RoundedCornerShape(12.dp))
                        .clickable(onClick = onConfirm)
                        .testTag("Budget.removeConfirm.confirm")
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.budget_remove_confirm_remove),
                        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ZeroTheme.colors.onPrimary),
                    )
                }
            }
        }
    }
}

@Composable
private fun CopyConfirmDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    Dialog(onDismissRequest = onCancel) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ZeroTheme.colors.surface, RoundedCornerShape(20.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.budget_copy_confirm_title),
                style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ZeroTheme.colors.onSurface),
            )
            Text(
                text = stringResource(R.string.budget_copy_confirm_subtitle),
                style = TextStyle(fontSize = 13.sp, color = ZeroTheme.colors.onSurfaceVariant),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            ) {
                Box(
                    modifier = Modifier
                        .background(ZeroTheme.colors.surfaceContainerLow, RoundedCornerShape(12.dp))
                        .clickable(onClick = onCancel)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.budget_copy_confirm_cancel),
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ZeroTheme.colors.onSurface),
                    )
                }
                Box(
                    modifier = Modifier
                        .background(ZeroTheme.colors.primary, RoundedCornerShape(12.dp))
                        .clickable(onClick = onConfirm)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.budget_copy_confirm_replace),
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ZeroTheme.colors.onPrimary),
                    )
                }
            }
        }
    }
}
