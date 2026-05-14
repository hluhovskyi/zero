package com.hluhovskyi.zero.budget.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.hluhovskyi.zero.ui.theme.OnSurface
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant
import com.hluhovskyi.zero.ui.theme.OutlineVariant
import com.hluhovskyi.zero.ui.theme.PrimaryContainer
import com.hluhovskyi.zero.ui.theme.Surface
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLow

internal class BudgetEditViewProvider(
    private val viewModel: BudgetEditViewModel,
    private val imageLoader: ImageLoader,
    private val amountFormatter: AmountFormatter,
) : ViewProvider {

    @Composable
    override fun View() {
        BudgetEditSheet(
            viewModel = viewModel,
            imageLoader = imageLoader,
            amountFormatter = amountFormatter,
        )
    }
}

@Composable
private fun BudgetEditSheet(
    viewModel: BudgetEditViewModel,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
) {
    val state by viewModel.state.collectAsState(initial = BudgetEditViewModel.State())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(Surface, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { viewModel.perform(BudgetEditViewModel.Action.TapClose) }) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.budget_edit_close_cd),
                    modifier = Modifier.size(24.dp),
                    tint = PrimaryContainer,
                )
            }
            Text(
                text = if (state.isEditing) {
                    stringResource(R.string.budget_edit_title_edit)
                } else {
                    stringResource(R.string.budget_edit_title_set)
                },
                modifier = Modifier.weight(1f),
                style = TextStyle(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryContainer,
                    textAlign = TextAlign.Center,
                ),
            )
            Spacer(Modifier.width(48.dp))
        }

        // Category identity row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CategoryIconView(
                colorScheme = state.colorScheme.toUi(),
                size = 36.dp,
                contentPadding = 8.dp,
            ) { tint ->
                imageLoader.View(
                    modifier = Modifier.size(20.dp),
                    image = state.icon,
                    tint = tint,
                )
            }
            Text(
                text = state.categoryName,
                modifier = Modifier.weight(1f),
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface,
                ),
            )
            val prevAmount = state.previousPeriodAmount
            if (prevAmount != null) {
                PreviousChip(
                    amount = prevAmount,
                    selected = state.isPreviousSelected,
                    amountFormatter = amountFormatter,
                    onClick = { viewModel.perform(BudgetEditViewModel.Action.TapPreviousChip) },
                )
            }
        }

        // Amount display
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.budget_edit_monthly_label).uppercase(),
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceVariant,
                    letterSpacing = 1.sp,
                ),
            )
            Row(verticalAlignment = Alignment.Bottom) {
                val hasAmount = state.amountText != "0"
                Text(
                    text = "$",
                    style = TextStyle(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (hasAmount) OnSurfaceVariant else OutlineVariant,
                    ),
                )
                Text(
                    text = state.amountText,
                    modifier = if (!hasAmount) Modifier.alpha(0.25f) else Modifier,
                    style = TextStyle(
                        fontSize = 52.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = PrimaryContainer,
                    ),
                )
            }
        }

        // NumPad — weight(1f) fills remaining height so CTA stays pinned at bottom
        NumPad(
            modifier = Modifier.weight(1f),
            value = state.amountText,
            onChange = { viewModel.perform(BudgetEditViewModel.Action.ChangeAmount(it)) },
        )

        // CTA
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            val parsedAmount = state.amountText.toBigDecimalOrNull()
            val enabled = parsedAmount != null && parsedAmount > java.math.BigDecimal.ZERO
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (enabled) PrimaryContainer else OutlineVariant,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .then(
                        if (enabled) Modifier.clickable { viewModel.perform(BudgetEditViewModel.Action.TapSave) }
                        else Modifier,
                    )
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (state.isEditing) {
                        stringResource(R.string.budget_edit_update)
                    } else {
                        stringResource(R.string.budget_edit_save)
                    },
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Surface,
                    ),
                )
            }
        }
    }
}

@Composable
private fun PreviousChip(
    amount: Amount,
    selected: Boolean,
    amountFormatter: AmountFormatter,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .background(
                color = if (selected) PrimaryContainer else SurfaceContainerLow,
                shape = RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = stringResource(R.string.budget_edit_last_month_chip, amountFormatter.format(amount)),
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (selected) Surface else PrimaryContainer,
            ),
        )
    }
}
