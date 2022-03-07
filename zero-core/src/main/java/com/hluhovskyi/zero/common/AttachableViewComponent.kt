package com.hluhovskyi.zero.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

@Composable
fun Buildable<out AttachableViewComponent>.AttachWithView(
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
) {
    val component by remember(lifecycleOwner) { mutableStateOf(build()) }
    component.AttachWithView()
}