package com.hluhovskyi.zero.transactions.edit.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
 * Date/Account; shows the current category and opens the full picker) over a row of
 * frequent-category quick chips for fast switching. The selected chip is highlighted;
 * tapping a chip selects that category.
 */
@Composable
internal fun CategoryField(
    modifier: Modifier = Modifier,
    imageLoader: ImageLoader,
    categories: List<TransactionEditCategory>,
    selectedCategory: TransactionEditCategory?,
    onCategorySelected: (TransactionEditCategory) -> Unit,
    onOpenPicker: () -> Unit,
) {
    Column(modifier = modifier) {
        CategoryRow(
            imageLoader = imageLoader,
            selectedCategory = selectedCategory,
            onClick = onOpenPicker,
        )
        QuickChipsRow(
            modifier = Modifier.padding(top = 10.dp),
            imageLoader = imageLoader,
            categories = categories.take(QUICK_CHIP_COUNT),
            selectedCategory = selectedCategory,
            onCategorySelected = onCategorySelected,
        )
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
    selectedCategory: TransactionEditCategory?,
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
                selected = category.id == selectedCategory?.id,
                onClick = { onCategorySelected(category) },
            )
        }
    }
}

@Composable
private fun QuickChip(
    imageLoader: ImageLoader,
    category: TransactionEditCategory,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = category.colorScheme.toUi()
    // Mirror CategoryIconView's tint logic so the icon reads as theme-coherent, and
    // reuse the same accent for the selected chip's border + label.
    val accent = if (ZeroTheme.colors.isLight) scheme.primary else scheme.background
    val background = if (selected) {
        ZeroTheme.colors.surfaceContainerLowest
    } else {
        ZeroTheme.colors.surfaceContainerLow
    }
    val labelColor = if (selected) accent else ZeroTheme.colors.onSurface
    Row(
        modifier = Modifier
            .clip(ChipShape)
            .background(background)
            .then(if (selected) Modifier.border(1.5.dp, accent, ChipShape) else Modifier)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        imageLoader.View(
            modifier = Modifier.sizeIn(maxHeight = 19.dp, maxWidth = 19.dp),
            image = category.icon,
            tint = accent,
        )
        Text(
            text = category.name,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = labelColor,
            maxLines = 1,
        )
    }
}
