package com.hluhovskyi.zero.transactions.edit

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ExtendedFloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.common.AttachWithView
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant
import com.hluhovskyi.zero.ui.theme.PrimaryContainer
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLow
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLowest

internal class TransactionEditViewProvider(
    private val viewModel: TransactionEditViewModel,
    private val expenseComponent: Buildable<out AttachableViewComponent>,
    private val incomeComponent: Buildable<out AttachableViewComponent>,
    private val transferComponent: Buildable<out AttachableViewComponent>,
) : ViewProvider {

    @Composable
    override fun View() {
        TransactionEditView(
            viewModel = viewModel,
            expenseComponent = expenseComponent,
            incomeComponent = incomeComponent,
            transferComponent = transferComponent
        )
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun TransactionEditView(
    viewModel: TransactionEditViewModel,
    expenseComponent: Buildable<out AttachableViewComponent>,
    incomeComponent: Buildable<out AttachableViewComponent>,
    transferComponent: Buildable<out AttachableViewComponent>
) {
    val state by viewModel.state.collectAsState(initial = TransactionEditViewModel.State())

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 96.dp)
        ) {
            item {
                TransactionEditHeader(
                    onDiscard = { viewModel.perform(TransactionEditViewModel.Action.Discard) }
                )
            }
            item {
                TransactionTypeToggle(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 24.dp),
                    types = state.transactionTypes,
                    selectedType = state.selectedTransactionType,
                    onTypeSelected = { type ->
                        viewModel.perform(TransactionEditViewModel.Action.ChangeTransactionType(type))
                    }
                )
            }
            item {
                when (state.selectedTransactionType) {
                    TransactionEditType.EXPENSE -> expenseComponent.AttachWithView()
                    TransactionEditType.INCOME -> incomeComponent.AttachWithView()
                    TransactionEditType.TRANSFER -> transferComponent.AttachWithView()
                }
            }
        }

        ExtendedFloatingActionButton(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .height(56.dp),
            onClick = { viewModel.perform(TransactionEditViewModel.Action.Save) },
            text = {
                Text(
                    text = "Save Transaction",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
            },
            backgroundColor = PrimaryContainer,
            contentColor = Color.White,
            shape = RoundedCornerShape(16.dp),
        )
    }
}

@Composable
private fun TransactionEditHeader(
    onDiscard: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onDiscard) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Discard",
                tint = PrimaryContainer,
            )
        }
        Text(
            modifier = Modifier.weight(1f),
            text = "New Transaction",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryContainer,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        // Spacer to balance the close button width
        Box(modifier = Modifier.widthIn(min = 48.dp))
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun TransactionTypeToggle(
    modifier: Modifier = Modifier,
    types: List<TransactionEditType>,
    selectedType: TransactionEditType?,
    onTypeSelected: (TransactionEditType) -> Unit
) {
    Row(
        modifier = modifier
            .background(SurfaceContainerLow, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        types.forEach { type ->
            val isSelected = type == selectedType
            val label = when (type) {
                TransactionEditType.EXPENSE -> "Expense"
                TransactionEditType.INCOME -> "Income"
                TransactionEditType.TRANSFER -> "Transfer"
            }

            Surface(
                modifier = Modifier.weight(1f),
                onClick = { onTypeSelected(type) },
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) SurfaceContainerLowest else Color.Transparent,
                elevation = if (isSelected) 2.dp else 0.dp,
            ) {
                Text(
                    modifier = Modifier.padding(vertical = 10.dp),
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) {
                        MaterialTheme.colors.primary
                    } else {
                        OnSurfaceVariant
                    },
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}
