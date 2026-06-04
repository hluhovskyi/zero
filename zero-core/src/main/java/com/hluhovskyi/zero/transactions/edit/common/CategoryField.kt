package com.hluhovskyi.zero.transactions.edit.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.transactions.edit.TransactionEditCategory
import com.hluhovskyi.zero.ui.CategoryIconView
import com.hluhovskyi.zero.ui.common.toUi
import com.hluhovskyi.zero.ui.theme.ZeroTheme

private const val QUICK_CHIP_COUNT = 6
private val ChipShape = RoundedCornerShape(percent = 50)

/**
 * Category selector for the transaction edit screen: a boxed field row (matches
 * Date/Account; shows the current category and opens the full picker) and — only as a
 * first-time shortcut ([showShortcuts]) — a row of stateless quick chips for frequent
 * categories *other than* the current one. The row is the single source of truth for
 * what's selected; the chips are one-tap switches that retire once the picker is used.
 */
@Composable
internal fun CategoryField(
    modifier: Modifier = Modifier,
    imageLoader: ImageLoader,
    categories: List<TransactionEditCategory>,
    selectedCategory: TransactionEditCategory?,
    showShortcuts: Boolean,
    onCategorySelected: (TransactionEditCategory) -> Unit,
    onOpenPicker: () -> Unit,
) {
    val shortcuts = if (showShortcuts) {
        categories.filter { it.id != selectedCategory?.id }.take(QUICK_CHIP_COUNT)
    } else {
        emptyList()
    }
    Column(modifier = modifier) {
        CategoryRow(
            imageLoader = imageLoader,
            selectedCategory = selectedCategory,
            onClick = onOpenPicker,
        )
        if (shortcuts.isNotEmpty()) {
            QuickChipsRow(
                modifier = Modifier.padding(top = 10.dp),
                imageLoader = imageLoader,
                categories = shortcuts,
                onCategorySelected = onCategorySelected,
            )
        }
    }
}

@Composable
private fun CategoryRow(
    imageLoader: ImageLoader,
    selectedCategory: TransactionEditCategory?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ZeroTheme.colors.surfaceContainerLow, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (selectedCategory != null) {
            CategoryIconView(
                colorScheme = selectedCategory.colorScheme.toUi(),
                size = 38.dp,
                contentPadding = 9.dp,
            ) { iconTint ->
                imageLoader.View(
                    modifier = Modifier.sizeIn(maxHeight = 20.dp, maxWidth = 20.dp),
                    image = selectedCategory.icon,
                    tint = iconTint,
                )
            }
        } else {
            CategoryIconView(
                color = ZeroTheme.colors.surfaceContainer,
                size = 38.dp,
                contentPadding = 9.dp,
            ) {
                Icon(
                    imageVector = Icons.Filled.Apps,
                    contentDescription = null,
                    modifier = Modifier.sizeIn(maxHeight = 20.dp, maxWidth = 20.dp),
                    tint = ZeroTheme.colors.onSurfaceVariant,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.transaction_edit_category_label).uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = ZeroTheme.colors.onSurfaceVariant,
                letterSpacing = 1.sp,
            )
            Text(
                modifier = Modifier.padding(top = 2.dp),
                text = selectedCategory?.name
                    ?: stringResource(R.string.transaction_edit_choose_category),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (selectedCategory != null) {
                    ZeroTheme.colors.primary
                } else {
                    ZeroTheme.colors.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.Filled.ArrowDropDown,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = ZeroTheme.colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun QuickChipsRow(
    modifier: Modifier = Modifier,
    imageLoader: ImageLoader,
    categories: List<TransactionEditCategory>,
    onCategorySelected: (TransactionEditCategory) -> Unit,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(categories, key = { it.id.value }) { category ->
            QuickChip(
                imageLoader = imageLoader,
                category = category,
                onClick = { onCategorySelected(category) },
            )
        }
    }
}

@Composable
private fun QuickChip(
    imageLoader: ImageLoader,
    category: TransactionEditCategory,
    onClick: () -> Unit,
) {
    val scheme = category.colorScheme.toUi()
    // Mirror CategoryIconView's tint logic so the icon reads as theme-coherent.
    val iconTint = if (ZeroTheme.colors.isLight) scheme.primary else scheme.background
    Row(
        modifier = Modifier
            .clip(ChipShape)
            .background(ZeroTheme.colors.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        imageLoader.View(
            modifier = Modifier.sizeIn(maxHeight = 19.dp, maxWidth = 19.dp),
            image = category.icon,
            tint = iconTint,
        )
        Text(
            text = category.name,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = ZeroTheme.colors.onSurface,
            maxLines = 1,
        )
    }
}
