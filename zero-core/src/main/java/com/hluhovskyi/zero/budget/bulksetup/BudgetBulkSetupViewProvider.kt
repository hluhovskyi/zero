package com.hluhovskyi.zero.budget.bulksetup

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
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.CategoryIconView
import com.hluhovskyi.zero.ui.budget.NumPad
import com.hluhovskyi.zero.ui.common.toUi
import com.hluhovskyi.zero.ui.theme.OnPrimary
import com.hluhovskyi.zero.ui.theme.OnSurface
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant
import com.hluhovskyi.zero.ui.theme.Outline
import com.hluhovskyi.zero.ui.theme.OutlineVariant
import com.hluhovskyi.zero.ui.theme.PrimaryContainer
import com.hluhovskyi.zero.ui.theme.Surface
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLow
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLowest
import java.math.BigDecimal

internal class BudgetBulkSetupViewProvider(
    private val viewModel: BudgetBulkSetupViewModel,
    private val imageLoader: ImageLoader,
    private val amountFormatter: AmountFormatter,
) : ViewProvider {

    @Composable
    override fun View() {
        BudgetBulkSetupView(
            viewModel = viewModel,
            imageLoader = imageLoader,
            amountFormatter = amountFormatter,
        )
    }
}

@Composable
private fun BudgetBulkSetupView(
    viewModel: BudgetBulkSetupViewModel,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
) {
    val state by viewModel.state.collectAsState(initial = BudgetBulkSetupViewModel.State())

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Header(
                title = stringResource(R.string.budget_bulk_title),
                subtitle = state.periodLabel,
                onClose = { viewModel.perform(BudgetBulkSetupViewModel.Action.TapClose) },
            )

            if (state.previousPeriodCount > 0) {
                CopyBanner(
                    count = state.previousPeriodCount,
                    onClick = { viewModel.perform(BudgetBulkSetupViewModel.Action.TapCopyAll) },
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 140.dp),
            ) {
                item {
                    Text(
                        text = stringResource(R.string.budget_bulk_section_label).uppercase(),
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 6.dp),
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurfaceVariant,
                            letterSpacing = 0.8.sp,
                        ),
                    )
                }
                items(state.categories, key = { it.categoryId.value }) { row ->
                    CategoryAmountRow(
                        row = row,
                        imageLoader = imageLoader,
                        amountFormatter = amountFormatter,
                        onClick = { viewModel.perform(BudgetBulkSetupViewModel.Action.StartEdit(row.categoryId)) },
                    )
                }
            }
        }

        BottomCta(
            state = state,
            amountFormatter = amountFormatter,
            onCreate = { viewModel.perform(BudgetBulkSetupViewModel.Action.TapCreate) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        NumpadOverlay(
            state = state,
            viewModel = viewModel,
            imageLoader = imageLoader,
            amountFormatter = amountFormatter,
        )
    }
}

@Composable
private fun Header(title: String, subtitle: String, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.budget_bulk_close_cd),
                modifier = Modifier.size(24.dp),
                tint = PrimaryContainer,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = TextStyle(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PrimaryContainer,
                ),
            )
            Text(
                text = subtitle,
                style = TextStyle(fontSize = 12.sp, color = OnSurfaceVariant),
            )
        }
        Spacer(Modifier.width(48.dp))
    }
}

@Composable
private fun CopyBanner(count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(PrimaryContainer, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Color(0x1FFFFFFF), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.SyncAlt,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = Color(0xFF5DDBA8),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.budget_bulk_copy_banner_title),
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OnPrimary),
            )
            Text(
                text = stringResource(R.string.budget_bulk_copy_banner_subtitle, count),
                style = TextStyle(fontSize = 12.sp, color = Color(0x8CFFFFFF)),
            )
        }
    }
}

@Composable
private fun CategoryAmountRow(
    row: BudgetBulkSetupViewModel.CategoryRow,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
    onClick: () -> Unit,
) {
    val isSet = row.amount > Amount.zero()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(SurfaceContainerLowest, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryIconView(
            colorScheme = row.colorScheme.toUi(),
            size = 36.dp,
            contentPadding = 8.dp,
        ) { tint ->
            imageLoader.View(
                modifier = Modifier.size(20.dp),
                image = row.icon,
                tint = tint,
            )
        }
        Text(
            text = row.name,
            modifier = Modifier.weight(1f),
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = OnSurface),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        AmountChip(
            row = row,
            isSet = isSet,
            amountFormatter = amountFormatter,
        )
    }
}

@Composable
private fun AmountChip(
    row: BudgetBulkSetupViewModel.CategoryRow,
    isSet: Boolean,
    amountFormatter: AmountFormatter,
) {
    val uiColor = row.colorScheme.toUi()
    val background = if (isSet) uiColor.background else SurfaceContainerLow
    val foreground = if (isSet) uiColor.primary else Outline
    Box(
        modifier = Modifier
            .background(background, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = if (isSet) amountFormatter.format(row.amount, currencySymbol = "$") else stringResource(R.string.budget_bulk_chip_set),
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = foreground),
        )
    }
}

@Composable
private fun BottomCta(
    state: BudgetBulkSetupViewModel.State,
    amountFormatter: AmountFormatter,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val setCount = state.setCount
    val enabled = setCount > 0
    val label = when {
        !enabled -> stringResource(R.string.budget_bulk_cta_empty)
        setCount == 1 -> stringResource(R.string.budget_bulk_cta_one)
        else -> stringResource(R.string.budget_bulk_cta_many, setCount)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Surface)
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp, bottom = 36.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = if (enabled) PrimaryContainer else OutlineVariant,
                    shape = RoundedCornerShape(14.dp),
                )
                .then(if (enabled) Modifier.clickable(onClick = onCreate) else Modifier)
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = label,
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Surface),
                )
                if (enabled) {
                    Text(
                        text = amountFormatter.format(state.totalBudgeted, currencySymbol = "$"),
                        style = TextStyle(fontSize = 12.sp, color = Color(0x8CFFFFFF)),
                    )
                }
            }
        }
    }
}

@Composable
private fun NumpadOverlay(
    state: BudgetBulkSetupViewModel.State,
    viewModel: BudgetBulkSetupViewModel,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
) {
    val visible = state.editingCategoryId != null
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
    ) {
        if (state.editingCategoryId == null) return@AnimatedVisibility
        val row = state.categories.firstOrNull { it.categoryId == state.editingCategoryId } ?: return@AnimatedVisibility
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x40000000))
                    .clickable { viewModel.perform(BudgetBulkSetupViewModel.Action.DismissEdit) },
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Surface, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .padding(top = 8.dp),
            ) {
                DragHandle()
                NumpadHeader(
                    row = row,
                    isPreviousSelected = row.previousAmount != null &&
                        state.editingAmountText == row.previousAmount.value.stripTrailingZeros().toPlainString(),
                    imageLoader = imageLoader,
                    amountFormatter = amountFormatter,
                    onPreviousChip = { viewModel.perform(BudgetBulkSetupViewModel.Action.TapPreviousChip) },
                    onDone = { viewModel.perform(BudgetBulkSetupViewModel.Action.CommitEdit) },
                )
                AmountDisplay(state.editingAmountText)
                NumPad(
                    value = state.editingAmountText,
                    onChange = { viewModel.perform(BudgetBulkSetupViewModel.Action.ChangeEditAmount(it)) },
                    modifier = Modifier.padding(top = 8.dp),
                )
                CommitButton(
                    text = state.editingAmountText,
                    onCommit = { viewModel.perform(BudgetBulkSetupViewModel.Action.CommitEdit) },
                )
            }
        }
    }
}

@Composable
private fun DragHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 4.dp)
                .background(OutlineVariant, RoundedCornerShape(2.dp)),
        )
    }
}

@Composable
private fun NumpadHeader(
    row: BudgetBulkSetupViewModel.CategoryRow,
    isPreviousSelected: Boolean,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
    onPreviousChip: () -> Unit,
    onDone: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryIconView(
            colorScheme = row.colorScheme.toUi(),
            size = 32.dp,
            contentPadding = 6.dp,
        ) { tint ->
            imageLoader.View(
                modifier = Modifier.size(18.dp),
                image = row.icon,
                tint = tint,
            )
        }
        Text(
            text = row.name,
            modifier = Modifier.weight(1f),
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = OnSurface),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (row.previousAmount != null) {
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
                    text = stringResource(R.string.budget_edit_last_month_chip, amountFormatter.format(row.previousAmount, currencySymbol = "$")),
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isPreviousSelected) Surface else PrimaryContainer,
                    ),
                )
            }
        }
        Text(
            text = stringResource(R.string.budget_bulk_numpad_done),
            modifier = Modifier
                .clickable(onClick = onDone)
                .padding(horizontal = 4.dp),
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PrimaryContainer),
        )
    }
}

@Composable
private fun AmountDisplay(text: String) {
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
private fun CommitButton(text: String, onCommit: () -> Unit) {
    val parsed = text.toBigDecimalOrNull()
    val hasAmount = parsed != null && parsed > BigDecimal.ZERO
    val label = if (hasAmount) stringResource(R.string.budget_bulk_numpad_set, "$$text") else stringResource(R.string.budget_bulk_numpad_skip)
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
