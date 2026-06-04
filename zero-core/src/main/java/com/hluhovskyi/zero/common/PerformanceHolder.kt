package com.hluhovskyi.zero.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.metrics.performance.PerformanceMetricsState

@Composable
fun rememberPerformanceHolder(): PerformanceMetricsState.Holder {
    val view = LocalView.current
    return remember(view) {
        PerformanceMetricsState.getHolderForHierarchy(view)
    }
}