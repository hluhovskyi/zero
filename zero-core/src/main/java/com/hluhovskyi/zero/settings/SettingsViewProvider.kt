package com.hluhovskyi.zero.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.ContentAlpha
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ExposedDropdownMenuBox
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.common.ViewProvider

internal class SettingsViewProvider(
    private val viewModel: SettingsViewModel,
) : ViewProvider {

    @Composable
    override fun View() {
        SettingsView(viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun SettingsView(
    viewModel: SettingsViewModel
) {
    val state by viewModel.state.collectAsState(initial = SettingsViewModel.State())
    Column {
        SettingsItem(
            primaryText = "Import",
            secondaryText = "Transfer data from previously used apps",
            icon = painterResource(R.drawable.ic_import_24),
            iconDescription = "Import icon",
            onClick = { viewModel.perform(SettingsViewModel.Action.Import) }
        )
        var currencyDropdownExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = currencyDropdownExpanded,
            onExpandedChange = { currencyDropdownExpanded = it }
        ) {
            SettingsItem(
                primaryText = "Primary currency",
                secondaryText = state.selectedCurrency,
                icon = painterResource(R.drawable.ic_currency_24),
                iconDescription = "Currency icon",
            )
            ExposedDropdownMenu(
                expanded = currencyDropdownExpanded,
                onDismissRequest = { currencyDropdownExpanded = false }
            ) {
                listOf("UAH", "USD", "EUR").forEach {
                    DropdownMenuItem(
                        onClick = {
                            currencyDropdownExpanded = false
                        }
                    ) {
                        Text(text = it)
                    }
                }
            }
        }

    }
}

@Composable
private fun SettingsItem(
    primaryText: String,
    secondaryText: String,
    icon: Painter,
    iconDescription: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick?.invoke() }
            .padding(
                vertical = 16.dp,
                horizontal = 16.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            modifier = Modifier
                .size(24.dp),
            painter = icon,
            contentDescription = iconDescription
        )
        Column(
            modifier = Modifier.padding(start = 24.dp)
        ) {
            Text(
                text = primaryText,
                fontSize = 16.sp,
            )
            CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                Text(
                    modifier = Modifier.padding(top = 2.dp),
                    text = secondaryText
                )
            }
        }
    }
}