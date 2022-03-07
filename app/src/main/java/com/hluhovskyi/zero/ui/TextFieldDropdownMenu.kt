package com.hluhovskyi.zero.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun <T> TextFieldDropdownMenu(
    items: List<T>,
    selectedItem: T?,
    label: @Composable (() -> Unit)? = null,
    nameMapping: (T) -> String = { it.toString() },
    onItemSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selectedItem?.let(nameMapping).orEmpty(),
            readOnly = true,
            label = label,
            onValueChange = { },
            modifier = Modifier.fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    onClick = {
                        onItemSelected(item)
                        expanded = false
                    }
                ) {
                    Text(text = nameMapping(item))
                }
            }
        }
    }
}