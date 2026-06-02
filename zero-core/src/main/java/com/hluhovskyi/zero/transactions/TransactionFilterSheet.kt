package com.hluhovskyi.zero.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.trace
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.transactions.filter.TransactionFilterSheetViewModel
import com.hluhovskyi.zero.ui.ModalHeader
import com.hluhovskyi.zero.ui.UiColorScheme
import com.hluhovskyi.zero.ui.common.toUi
import com.hluhovskyi.zero.ui.theme.ZeroTheme

@Composable
internal fun TransactionFilterSheet(
    activeFilter: TransactionFilter,
    availableCategories: List<TransactionFilterSheetViewModel.FilterCategoryItem>,
    availableAccounts: List<TransactionFilterSheetViewModel.FilterAccountItem>,
    imageLoader: ImageLoader,
    onApply: (TransactionFilter) -> Unit,
    onClose: () -> Unit,
) {
    var draft by remember(activeFilter) { mutableStateOf(activeFilter) }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        ModalHeader(
            title = stringResource(R.string.filter_title),
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
                            text = stringResource(R.string.filter_reset),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = ZeroTheme.colors.error,
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
            FilterSection(label = stringResource(R.string.filter_section_period)) {
                PeriodPillRow(
                    selected = draft.period,
                    onSelect = { id ->
                        draft = draft.copy(period = if (draft.period == id) null else id)
                    },
                )
            }

            FilterSection(label = stringResource(R.string.filter_section_type)) {
                TypePillRow(
                    selected = draft.type,
                    onSelect = { type -> draft = draft.copy(type = type) },
                )
            }

            if (availableAccounts.isNotEmpty()) {
                FilterSection(
                    label = stringResource(R.string.filter_section_accounts),
                    selectedLabel = draft.accountIds?.size?.let { "$it / ${availableAccounts.size - 1}" },
                ) {
                    AccountItemGrid(
                        allLabel = stringResource(R.string.filter_all_accounts),
                        items = availableAccounts,
                        selectedIds = draft.accountIds,
                        onToggleAll = { draft = draft.copy(accountIds = null) },
                        onToggle = { id ->
                            draft = draft.copy(
                                accountIds = toggleAccountId(
                                    current = draft.accountIds,
                                    id = id,
                                    total = availableAccounts.size - 1,
                                ),
                            )
                        },
                        imageLoader = imageLoader,
                    )
                }
            }

            if (availableCategories.isNotEmpty()) {
                FilterSection(
                    label = stringResource(R.string.filter_section_categories),
                    selectedLabel = draft.categoryIds?.size?.let { "$it / ${availableCategories.size - 1}" },
                ) {
                    CategoryItemGrid(
                        allLabel = stringResource(R.string.filter_all_categories),
                        items = availableCategories,
                        selectedIds = draft.categoryIds,
                        onToggleAll = { draft = draft.copy(categoryIds = null) },
                        onToggle = { id ->
                            draft = draft.copy(
                                categoryIds = toggleCategoryId(
                                    current = draft.categoryIds,
                                    id = id,
                                    total = availableCategories.size - 1,
                                ),
                            )
                        },
                        imageLoader = imageLoader,
                    )
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
    selectedLabel: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 24.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp, start = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = ZeroTheme.colors.onSurfaceVariant,
                letterSpacing = 1.sp,
            )
            if (selectedLabel != null) {
                Text(
                    text = selectedLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ZeroTheme.colors.primaryContainer,
                )
            }
        }
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
            .background(if (isActive) ZeroTheme.colors.primaryContainer else Color.Transparent)
            .border(
                width = 1.5.dp,
                color = if (isActive) ZeroTheme.colors.primaryContainer else ZeroTheme.colors.outlineVariant,
                shape = CircleShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isActive) ZeroTheme.colors.onPrimary else ZeroTheme.colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun AllFilterTile(
    label: String,
    allOn: Boolean,
    count: Int,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (allOn) ZeroTheme.colors.primaryContainer else ZeroTheme.colors.surfaceContainerLow),
            contentAlignment = Alignment.Center,
        ) {
            if (allOn) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = ZeroTheme.colors.onPrimary,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                Text(
                    text = count.toString(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = ZeroTheme.colors.outline,
                )
            }
        }
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (allOn) ZeroTheme.colors.primaryContainer else ZeroTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CategoryItemGrid(
    allLabel: String,
    items: List<TransactionFilterSheetViewModel.FilterCategoryItem>,
    selectedIds: Set<Id.Known>?,
    onToggleAll: () -> Unit,
    onToggle: (Id.Known) -> Unit,
    imageLoader: ImageLoader,
) {
    FilterTileGrid(rowCount = (items.size + 4) / 5) { idx ->
        val item = items.getOrNull(idx)
        Box(
            modifier = Modifier
                .weight(1f)
                .then(
                    when (item) {
                        null -> Modifier
                        is TransactionFilterSheetViewModel.FilterCategoryItem.All -> Modifier.clickable(onClick = onToggleAll)
                        is TransactionFilterSheetViewModel.FilterCategoryItem.Category -> Modifier.clickable { onToggle(item.id) }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            when (item) {
                null -> Unit
                is TransactionFilterSheetViewModel.FilterCategoryItem.All -> AllFilterTile(
                    label = allLabel,
                    allOn = selectedIds == null,
                    count = item.count,
                )
                is TransactionFilterSheetViewModel.FilterCategoryItem.Category -> CategoryTile(
                    category = item,
                    isSelected = selectedIds != null && item.id in selectedIds,
                    imageLoader = imageLoader,
                )
            }
        }
    }
}

@Composable
private fun AccountItemGrid(
    allLabel: String,
    items: List<TransactionFilterSheetViewModel.FilterAccountItem>,
    selectedIds: Set<Id.Known>?,
    onToggleAll: () -> Unit,
    onToggle: (Id.Known) -> Unit,
    imageLoader: ImageLoader,
) = trace("AccountItemGrid") {
    FilterTileGrid(rowCount = (items.size + 4) / 5) { idx ->
        val item = items.getOrNull(idx)
        Box(
            modifier = Modifier
                .weight(1f)
                .then(
                    when (item) {
                        null -> Modifier
                        is TransactionFilterSheetViewModel.FilterAccountItem.All -> Modifier.clickable(onClick = onToggleAll)
                        is TransactionFilterSheetViewModel.FilterAccountItem.Account -> Modifier.clickable { onToggle(item.id) }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            when (item) {
                null -> Unit
                is TransactionFilterSheetViewModel.FilterAccountItem.All -> AllFilterTile(
                    label = allLabel,
                    allOn = selectedIds == null,
                    count = item.count,
                )
                is TransactionFilterSheetViewModel.FilterAccountItem.Account -> AccountTile(
                    account = item,
                    isSelected = selectedIds != null && item.id in selectedIds,
                    imageLoader = imageLoader,
                )
            }
        }
    }
}

@Composable
private fun FilterTileGrid(
    rowCount: Int,
    cellContent: @Composable RowScope.(idx: Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        for (rowIdx in 0 until rowCount) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (colIdx in 0 until 5) {
                    cellContent(rowIdx * 5 + colIdx)
                }
            }
        }
    }
}

@Composable
private fun CategoryTile(
    category: TransactionFilterSheetViewModel.FilterCategoryItem.Category,
    isSelected: Boolean,
    imageLoader: ImageLoader,
) = trace("CategoryTile") {
    val (containerColor, iconColor) = filterTileColors(category.colorScheme.toUi())
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(containerColor)
                .then(
                    if (isSelected) {
                        Modifier.border(2.dp, iconColor, RoundedCornerShape(14.dp))
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            imageLoader.View(
                image = category.icon,
                modifier = Modifier.size(24.dp),
                tint = iconColor,
            )
        }
        Text(
            text = category.name,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = ZeroTheme.colors.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp,
        )
    }
}

@Composable
private fun AccountTile(
    account: TransactionFilterSheetViewModel.FilterAccountItem.Account,
    isSelected: Boolean,
    imageLoader: ImageLoader,
) {
    val (containerColor, iconColor) = filterTileColors(account.colorScheme.toUi())
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(containerColor)
                .then(
                    if (isSelected) {
                        Modifier.border(2.dp, iconColor, RoundedCornerShape(14.dp))
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            imageLoader.View(
                image = account.icon,
                modifier = Modifier.size(24.dp),
                tint = iconColor,
            )
        }
        Text(
            text = account.name,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = ZeroTheme.colors.onSurface,
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
            .background(ZeroTheme.colors.primaryContainer)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.filter_apply),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = ZeroTheme.colors.onPrimary,
        )
        if (activeCount > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(ZeroTheme.colors.onPrimary.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = activeCount.toString(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = ZeroTheme.colors.onPrimary,
                )
            }
        }
    }
}

/**
 * In dark mode the entity's `primary` and `background` swap roles so the tile reads as a
 * dark brand chip with a light pastel icon, matching `CategoryIconView`.
 * Returns `(container, icon)`.
 */
@Composable
private fun filterTileColors(colorScheme: UiColorScheme): Pair<Color, Color> =
    if (ZeroTheme.colors.isLight) {
        colorScheme.background to colorScheme.primary
    } else {
        colorScheme.primary to colorScheme.background
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
            if (id in it) it.remove(id) else it.add(id)
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
            if (id in it) it.remove(id) else it.add(id)
        }
    }
    return if (next.isEmpty() || next.size == total) null else next
}
