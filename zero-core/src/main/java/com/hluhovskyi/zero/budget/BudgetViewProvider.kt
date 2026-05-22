package com.hluhovskyi.zero.budget

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
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
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.CategoryIconView
import com.hluhovskyi.zero.ui.budget.NumPad
import com.hluhovskyi.zero.ui.common.toUi
import com.hluhovskyi.zero.ui.theme.OnPrimary
import com.hluhovskyi.zero.ui.theme.OnPrimaryContainer
import com.hluhovskyi.zero.ui.theme.OnSurface
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant
import com.hluhovskyi.zero.ui.theme.Outline
import com.hluhovskyi.zero.ui.theme.OutlineVariant
import com.hluhovskyi.zero.ui.theme.Primary
import com.hluhovskyi.zero.ui.theme.PrimaryContainer
import com.hluhovskyi.zero.ui.theme.Surface
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLow
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
    val state by viewModel.state.collectAsState(initial = BudgetViewModel.State())
    var toastMessage by remember { mutableStateOf<String?>(null) }
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
                        previousPeriodHadBudgets = state.previousPeriodBudgets.any { it.budgetId != null },
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
            if (state.previousPeriodBudgets.any { it.budgetId != null }) {
                item {
                    CopyFromPreviousCard(
                        monthLabel = state.previousPeriodLabel,
                        count = state.previousPeriodBudgets.count { it.budgetId != null },
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
            items(state.budgeted, key = { it.categoryId.value }) { row ->
                if (row.budgetId != null) {
                    BudgetCard(
                        row = row,
                        onTap = { viewModel.perform(BudgetViewModel.Action.TapCategory(row.categoryId)) },
                        imageLoader = imageLoader,
                        amountFormatter = amountFormatter,
                    )
                } else {
                    UnsetCategoryRow(
                        name = row.categoryName,
                        colorScheme = row.colorScheme,
                        icon = row.icon,
                        imageLoader = imageLoader,
                        onClick = { viewModel.perform(BudgetViewModel.Action.TapCategory(row.categoryId)) },
                    )
                }
            }
        }

        if (state.copyConfirmVisible) {
            CopyConfirmDialog(
                onConfirm = {
                    val count = state.previousPeriodBudgets.count { it.budgetId != null }
                    val label = state.previousPeriodLabel
                    viewModel.perform(BudgetViewModel.Action.ConfirmCopy)
                    toastMessage = "Copied $count categories from $label"
                },
                onCancel = { viewModel.perform(BudgetViewModel.Action.CancelCopy) },
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
    val visible = state.editingCategoryId != null
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
    ) {
        val editingId = state.editingCategoryId ?: return@AnimatedVisibility
        val row = state.budgeted.firstOrNull { it.categoryId == editingId } ?: return@AnimatedVisibility

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x40000000))
                    .clickable { viewModel.perform(BudgetViewModel.Action.DismissInlineEdit) }
                    .testTag("Budget.inlineNumpad.scrim"),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Surface,
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
                            .background(OutlineVariant, RoundedCornerShape(2.dp)),
                    )
                }
                InlineNumpadHeader(
                    name = row.categoryName,
                    icon = row.icon,
                    colorScheme = row.colorScheme,
                    previousAmount = state.editingPreviousAmount,
                    isPreviousSelected = state.isPreviousAmountSelected,
                    imageLoader = imageLoader,
                    amountFormatter = amountFormatter,
                    onPreviousChip = { viewModel.perform(BudgetViewModel.Action.TapPreviousChip) },
                )
                InlineAmountDisplay(state.editingAmountText)
                NumPad(
                    value = state.editingAmountText,
                    onChange = { viewModel.perform(BudgetViewModel.Action.ChangeEditAmount(it)) },
                    modifier = Modifier.padding(top = 8.dp),
                )
                InlineCommitButton(
                    text = state.editingAmountText,
                    hasNextUnset = hasNextUnset(state, editingId),
                    onCommit = { viewModel.perform(BudgetViewModel.Action.CommitInlineEdit) },
                )
            }
        }
    }
}

private fun hasNextUnset(state: BudgetViewModel.State, editingId: Id.Known): Boolean = state.budgeted.any {
    it.categoryId != editingId &&
        it.budgetId == null &&
        it.categoryId !in state.skippedInSession
}

@Composable
private fun InlineNumpadHeader(
    name: String,
    icon: Image,
    colorScheme: ColorScheme,
    previousAmount: Amount?,
    isPreviousSelected: Boolean,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
    onPreviousChip: () -> Unit,
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
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = OnSurface),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (previousAmount != null) {
            Box(
                modifier = Modifier
                    .background(
                        color = if (isPreviousSelected) PrimaryContainer else SurfaceContainerLow,
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
                        color = if (isPreviousSelected) Surface else PrimaryContainer,
                    ),
                )
            }
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
                color = if (hasAmount) OnSurfaceVariant else OutlineVariant,
            ),
        )
        Text(
            text = text,
            modifier = if (!hasAmount) Modifier.alpha(0.3f) else Modifier,
            style = TextStyle(
                fontSize = 44.sp,
                fontWeight = FontWeight.ExtraBold,
                color = PrimaryContainer,
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
                .background(PrimaryContainer, RoundedCornerShape(14.dp))
                .clickable(onClick = onCommit)
                .testTag("Budget.inlineNumpad.commit")
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Surface),
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
                color = OnSurface,
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
            tint = if (hasOlder) OnSurface else OutlineVariant,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnSurface,
            ),
        )
        Spacer(Modifier.width(16.dp))
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = stringResource(R.string.budget_next_month_cd),
            modifier = Modifier
                .size(24.dp)
                .clickable(enabled = hasNewer, onClick = onNewer),
            tint = if (hasNewer) OnSurface else OutlineVariant,
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
            .background(PrimaryContainer, RoundedCornerShape(20.dp))
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.budget_empty_title, periodLabel),
            style = TextStyle(
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = OnPrimary,
            ),
        )
        Text(
            text = stringResource(R.string.budget_empty_subtitle),
            style = TextStyle(
                fontSize = 13.sp,
                color = OnPrimaryContainer,
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
                color = OnPrimaryContainer,
                letterSpacing = 1.sp,
            ),
        )
        Text(
            text = value,
            style = TextStyle(
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = OnPrimary,
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
            .background(SurfaceContainerLow, RoundedCornerShape(12.dp))
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
                    color = OnSurface,
                ),
            )
            Text(
                text = stringResource(R.string.budget_copy_count, count),
                style = TextStyle(fontSize = 12.sp, color = OnSurfaceVariant),
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = OnSurfaceVariant,
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
            color = OnSurfaceVariant,
            letterSpacing = 0.8.sp,
        ),
    )
}

@Composable
private fun UnsetCategoryRow(
    name: String,
    colorScheme: ColorScheme,
    icon: Image,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .drawBehind {
                drawRoundRect(
                    color = OutlineVariant,
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                    ),
                )
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryIconView(
            colorScheme = colorScheme.toUi(),
            size = 36.dp,
            contentPadding = 8.dp,
        ) { tint ->
            imageLoader.View(
                modifier = Modifier.size(20.dp),
                image = icon,
                tint = tint,
            )
        }
        Text(
            text = name,
            modifier = Modifier.weight(1f),
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnSurface,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .background(SurfaceContainerLow, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.budget_set_limit),
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Outline,
                ),
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
                .background(PrimaryContainer, RoundedCornerShape(14.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Color(0xFF5DDBA8),
            )
            Text(
                text = message.orEmpty(),
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OnPrimary,
                ),
            )
        }
    }
}

@Composable
private fun CopyConfirmDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    Dialog(onDismissRequest = onCancel) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface, RoundedCornerShape(20.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.budget_copy_confirm_title),
                style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = OnSurface),
            )
            Text(
                text = stringResource(R.string.budget_copy_confirm_subtitle),
                style = TextStyle(fontSize = 13.sp, color = OnSurfaceVariant),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            ) {
                Box(
                    modifier = Modifier
                        .background(SurfaceContainerLow, RoundedCornerShape(12.dp))
                        .clickable(onClick = onCancel)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.budget_copy_confirm_cancel),
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = OnSurface),
                    )
                }
                Box(
                    modifier = Modifier
                        .background(Primary, RoundedCornerShape(12.dp))
                        .clickable(onClick = onConfirm)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.budget_copy_confirm_replace),
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = OnPrimary),
                    )
                }
            }
        }
    }
}
