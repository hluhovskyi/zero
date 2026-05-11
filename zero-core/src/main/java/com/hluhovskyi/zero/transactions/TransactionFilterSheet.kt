package com.hluhovskyi.zero.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.transactions.filter.TransactionFilterSheetViewModel
import com.hluhovskyi.zero.ui.CategoryIconView
import com.hluhovskyi.zero.ui.ModalHeader
import com.hluhovskyi.zero.ui.common.toUi
import com.hluhovskyi.zero.ui.theme.Error
import com.hluhovskyi.zero.ui.theme.OnSurface
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant
import com.hluhovskyi.zero.ui.theme.Outline
import com.hluhovskyi.zero.ui.theme.OutlineVariant
import com.hluhovskyi.zero.ui.theme.PrimaryContainer
import com.hluhovskyi.zero.ui.theme.SurfaceContainer
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLow

@Composable
internal fun TransactionFilterSheet(
    activeFilter: TransactionFilter,
    availableCategories: List<TransactionFilterSheetViewModel.FilterCategory>,
    availableAccounts: List<TransactionFilterSheetViewModel.FilterAccount>,
    imageLoader: ImageLoader,
    onApply: (TransactionFilter) -> Unit,
    onClose: () -> Unit,
) {
    var draft by remember(activeFilter) { mutableStateOf(activeFilter) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f),
    ) {
        ModalHeader(
            title = "Filter",
            onClose = onClose,
            trailingContent = if (draft.isActive) {
                {
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clickable { draft = TransactionFilter() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Reset",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Error,
                        )
                    }
                }
            } else {
                null
            },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            FilterSection(label = "Period") {
                PeriodPillRow(
                    selected = draft.period,
                    onSelect = { id ->
                        draft = draft.copy(period = if (draft.period == id) null else id)
                    },
                )
            }

            FilterSection(label = "Type") {
                TypePillRow(
                    selected = draft.type,
                    onSelect = { type -> draft = draft.copy(type = type) },
                )
            }

            if (availableCategories.isNotEmpty()) {
                FilterSection(label = "Categories") {
                    SelectionGridSection(
                        allLabel = "All categories",
                        totalCount = availableCategories.size,
                        selectedIds = draft.categoryIds,
                        onToggleAll = { draft = draft.copy(categoryIds = null) },
                    ) {
                        ItemGrid(
                            items = availableCategories.map { CategoryGridWrapper(it) },
                            selectedIds = draft.categoryIds,
                            onToggle = { id ->
                                draft = draft.copy(
                                    categoryIds = toggleCategoryId(
                                        current = draft.categoryIds,
                                        id = id,
                                        total = availableCategories.size,
                                    ),
                                )
                            },
                            itemContent = { wrapper, isActive, isDimmed ->
                                CategoryGridItem(
                                    category = wrapper.category,
                                    isActive = isActive,
                                    isDimmed = isDimmed,
                                    imageLoader = imageLoader,
                                )
                            },
                        )
                    }
                }
            }

            if (availableAccounts.isNotEmpty()) {
                FilterSection(label = "Accounts") {
                    SelectionGridSection(
                        allLabel = "All accounts",
                        totalCount = availableAccounts.size,
                        selectedIds = draft.accountIds,
                        onToggleAll = { draft = draft.copy(accountIds = null) },
                    ) {
                        ItemGrid(
                            items = availableAccounts.map { AccountGridWrapper(it) },
                            selectedIds = draft.accountIds,
                            onToggle = { id ->
                                draft = draft.copy(
                                    accountIds = toggleAccountId(
                                        current = draft.accountIds,
                                        id = id,
                                        total = availableAccounts.size,
                                    ),
                                )
                            },
                            itemContent = { wrapper, isActive, isDimmed ->
                                AccountGridItem(
                                    account = wrapper.account,
                                    isActive = isActive,
                                    isDimmed = isDimmed,
                                )
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        ApplyButton(
            activeCount = draft.activeCount,
            onClick = { onApply(draft) },
        )
    }
}

@Composable
private fun FilterSection(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 24.dp)) {
        Text(
            text = label.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = OnSurfaceVariant,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 10.dp, start = 2.dp),
        )
        content()
    }
}

@Composable
private fun PeriodPillRow(
    selected: TransactionFilter.DatePeriod?,
    onSelect: (TransactionFilter.DatePeriod) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        TransactionFilter.DatePeriod.entries.forEach { period ->
            PillChip(
                label = period.label,
                isActive = selected == period,
                onClick = { onSelect(period) },
            )
        }
    }
}

@Composable
private fun TypePillRow(
    selected: TransactionFilter.TransactionType,
    onSelect: (TransactionFilter.TransactionType) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TransactionFilter.TransactionType.entries.forEach { type ->
            PillChip(
                label = type.label,
                isActive = selected == type,
                onClick = { onSelect(type) },
            )
        }
    }
}

@Composable
private fun PillChip(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (isActive) PrimaryContainer else Color.Transparent)
            .border(
                width = 1.5.dp,
                color = if (isActive) PrimaryContainer else OutlineVariant,
                shape = CircleShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isActive) Color.White else OnSurfaceVariant,
        )
    }
}

@Composable
private fun SelectionGridSection(
    allLabel: String,
    totalCount: Int,
    selectedIds: Set<Id.Known>?,
    onToggleAll: () -> Unit,
    content: @Composable () -> Unit,
) {
    val allActive = selectedIds == null
    val selectedCount = selectedIds?.size ?: totalCount

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(if (allActive) PrimaryContainer else SurfaceContainerLow)
                .border(
                    width = 1.5.dp,
                    color = if (allActive) PrimaryContainer else OutlineVariant,
                    shape = CircleShape,
                )
                .clickable(onClick = onToggleAll)
                .padding(start = if (allActive) 10.dp else 14.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (allActive) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp),
                    )
                }
                Text(
                    text = allLabel,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (allActive) Color.White else OnSurfaceVariant,
                )
            }
        }

        Text(
            text = if (allActive) "All $totalCount" else "$selectedCount of $totalCount",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = OnSurfaceVariant,
        )
    }

    content()
}

private interface GridItem {
    val gridId: Id.Known
}

private data class CategoryGridWrapper(val category: TransactionFilterSheetViewModel.FilterCategory) : GridItem {
    override val gridId = category.id
}

private data class AccountGridWrapper(val account: TransactionFilterSheetViewModel.FilterAccount) : GridItem {
    override val gridId = account.id
}

@Composable
private fun <T : GridItem> ItemGrid(
    items: List<T>,
    selectedIds: Set<Id.Known>?,
    onToggle: (Id.Known) -> Unit,
    itemContent: @Composable (item: T, isActive: Boolean, isDimmed: Boolean) -> Unit,
) {
    val allActive = selectedIds == null
    val rows = items.chunked(3)
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                row.forEach { item ->
                    val isActive = allActive || selectedIds!!.contains(item.gridId)
                    val isDimmed = !allActive && !selectedIds!!.contains(item.gridId)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isActive) SurfaceContainer else SurfaceContainerLow)
                            .clickable { onToggle(item.gridId) }
                            .alpha(if (isDimmed) 0.3f else 1f),
                    ) {
                        itemContent(item, isActive, isDimmed)
                    }
                }
                repeat(3 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CategoryGridItem(
    category: TransactionFilterSheetViewModel.FilterCategory,
    isActive: Boolean,
    isDimmed: Boolean,
    imageLoader: ImageLoader,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CategoryIconView(
            colorScheme = if (isActive) {
                category.colorScheme.toUi()
            } else {
                category.colorScheme.toUi().copy(
                    primary = Outline,
                    background = SurfaceContainer,
                )
            },
            size = 32.dp,
            contentPadding = 7.dp,
        ) { iconTint ->
            imageLoader.View(
                image = category.icon,
                modifier = Modifier.size(18.dp),
                tint = iconTint,
            )
        }
        Text(
            text = category.name,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = OnSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp,
        )
    }
}

@Composable
private fun AccountGridItem(
    account: TransactionFilterSheetViewModel.FilterAccount,
    isActive: Boolean,
    isDimmed: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isActive) Color(0xFFD9E2FF) else SurfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = account.name.take(1).uppercase(),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (isActive) PrimaryContainer else Outline,
            )
        }
        Text(
            text = account.name,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = OnSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp,
        )
    }
}

@Composable
private fun ApplyButton(
    activeCount: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .padding(bottom = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(PrimaryContainer)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Apply filters",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        if (activeCount > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = activeCount.toString(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
    }
}

private fun toggleCategoryId(
    current: Set<Id.Known>?,
    id: Id.Known,
    total: Int,
): Set<Id.Known>? {
    val next = if (current == null) {
        mutableSetOf(id)
    } else {
        current.toMutableSet().also {
            if (it.contains(id)) it.remove(id) else it.add(id)
        }
    }
    return if (next.isEmpty() || next.size == total) null else next
}

private fun toggleAccountId(
    current: Set<Id.Known>?,
    id: Id.Known,
    total: Int,
): Set<Id.Known>? {
    val next = if (current == null) {
        mutableSetOf(id)
    } else {
        current.toMutableSet().also {
            if (it.contains(id)) it.remove(id) else it.add(id)
        }
    }
    return if (next.isEmpty() || next.size == total) null else next
}
