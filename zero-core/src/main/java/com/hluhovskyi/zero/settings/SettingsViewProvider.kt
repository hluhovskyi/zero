package com.hluhovskyi.zero.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
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

@Composable
private fun SettingsView(
    viewModel: SettingsViewModel
) {
    Row(
        modifier = Modifier
            .height(48.dp)
            .fillMaxWidth()
            .clickable { viewModel.perform(SettingsViewModel.Action.Import) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            modifier = Modifier
                .aspectRatio(1f)
                .padding(10.dp),
            painter = painterResource(R.drawable.ic_import_24),
            contentDescription = "Import icon"
        )
        Text(
            text = "Import"
        )
    }
}