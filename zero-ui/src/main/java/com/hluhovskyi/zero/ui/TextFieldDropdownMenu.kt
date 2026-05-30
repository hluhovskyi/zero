package com.hluhovskyi.zero.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun <T> TextFieldDropdownMenu(
    modifier: Modifier = Modifier,
    items: List<T>,
    selectedItem: T?,
    selectedItemIcon: @Composable ((T) -> Unit)? = null,
    nameMapping: (T) -> String = { it.toString() },
    menuItem: @Composable (T) -> Unit = { item -> Text(text = nameMapping(item)) },
    label: @Composable (() -> Unit)? = null,
    onItemSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        modifier = modifier,
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selectedItem?.let(nameMapping).orEmpty(),
            readOnly = true,
            label = label,
            leadingIcon = selectedItem?.let { item ->
                selectedItemIcon?.let { { it.invoke(item) } }
            },
            onValueChange = { },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { menuItem(item) },
                    onClick = {
                        onItemSelected(item)
                        expanded = false
                    },
                )
            }
        }
    }
}
