package com.hluhovskyi.zero.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ExposedDropdownMenuBox
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterialApi::class)
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
            modifier = Modifier.fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    onClick = {
                        onItemSelected(item)
                        expanded = false
                    },
                ) {
                    menuItem(item)
                }
            }
        }
    }
}
