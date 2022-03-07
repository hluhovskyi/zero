package com.hluhovskyi.zero.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.LifecycleOwner

interface AttachableViewComponent : Attachable {
    
    val viewProvider: ViewProvider
}

@Composable
fun AttachableViewComponent.AttachWithView(
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
) {
    DisposableEffect(lifecycleOwner) {
        val closeable = attach()
        onDispose { closeable.close() }
    }
    viewProvider()
}