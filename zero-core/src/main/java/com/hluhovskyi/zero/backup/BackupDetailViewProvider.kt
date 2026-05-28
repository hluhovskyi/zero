package com.hluhovskyi.zero.backup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.hluhovskyi.zero.common.ViewProvider

internal class BackupDetailViewProvider(
    private val viewModel: BackupDetailViewModel,
) : ViewProvider {

    @Composable
    override fun View() {
        @Suppress("UNUSED_VARIABLE")
        val state by viewModel.state.collectAsState(initial = BackupDetailViewModel.State())
        // TODO Task 3: implement layout per Phase 3 design.
    }
}
