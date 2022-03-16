package com.hluhovskyi.zero.transactions.edit.common

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hluhovskyi.zero.transactions.edit.TransactionEditCategory
import com.hluhovskyi.zero.ui.TextFieldDropdownMenu

@Composable
fun TransactionEditCategorySelect(
    modifier: Modifier = Modifier,
    categories: List<TransactionEditCategory>,
    selectedCategory: TransactionEditCategory?,
    onCategorySelected: (TransactionEditCategory) -> Unit
) {
    TextFieldDropdownMenu(
        modifier = modifier,
        items = categories,
        label = {
            Text(text = "Category")
        },
        nameMapping = { it.name },
        selectedItem = selectedCategory,
        onItemSelected = onCategorySelected
    )
}
