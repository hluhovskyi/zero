package com.hluhovskyi.zero.transactions.edit.common

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.transactions.edit.TransactionEditCategory
import com.hluhovskyi.zero.ui.CategoryIconView
import com.hluhovskyi.zero.ui.TextFieldDropdownMenu
import com.hluhovskyi.zero.ui.common.toUi
import com.hluhovskyi.zero.ui.theme.ZeroTheme

@Composable
fun TransactionEditCategorySelect(
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
    categories: List<TransactionEditCategory>,
    selectedCategory: TransactionEditCategory?,
    onCategorySelected: (TransactionEditCategory) -> Unit,
) {
    TextFieldDropdownMenu(
        modifier = modifier,
        items = categories,
        label = {
            Text(text = stringResource(R.string.transaction_edit_category_label))
        },
        nameMapping = { it.name },
        menuItem = { category ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryIconView(
                    colorScheme = category.colorScheme.toUi(),
                    size = 32.dp,
                    contentPadding = 6.dp,
                    modifier = Modifier.padding(end = 12.dp),
                ) { tint ->
                    imageLoader.View(
                        modifier = Modifier.sizeIn(maxHeight = 20.dp, maxWidth = 20.dp),
                        image = category.icon,
                        tint = tint,
                    )
                }
                Text(text = category.name)
            }
        },
        selectedItem = selectedCategory,
        selectedItemIcon = { category ->
            CategoryIconView(
                colorScheme = category.colorScheme.toUi(),
                size = 32.dp,
                contentPadding = 6.dp,
                modifier = Modifier.padding(start = 8.dp),
            ) { tint ->
                imageLoader.View(image = category.icon, tint = tint)
            }
        },
        onItemSelected = onCategorySelected,
    )
}

@Composable
fun TransactionEditCategorySelectWithEditButton(
    modifier: Modifier = Modifier,
    imageLoader: ImageLoader = ImageLoader.empty(),
    categories: List<TransactionEditCategory>,
    selectedCategory: TransactionEditCategory?,
    onCategorySelected: (TransactionEditCategory) -> Unit,
    onCategoryEdit: () -> Unit,
) {
    Row(
        modifier = modifier.height(IntrinsicSize.Min),
    ) {
        TransactionEditCategorySelect(
            modifier = Modifier.weight(1f),
            imageLoader = imageLoader,
            categories = categories,
            selectedCategory = selectedCategory,
            onCategorySelected = onCategorySelected,
        )
        Button(
            modifier = Modifier
                .padding(start = 16.dp, top = 8.dp)
                .sizeIn(maxHeight = 54.dp)
                .aspectRatio(1f, true),
            onClick = onCategoryEdit,
        ) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = stringResource(R.string.transaction_edit_edit_categories_description),
                tint = ZeroTheme.colors.onPrimary,
            )
        }
    }
}
